package com.gytrinket.gytrinket.compat;

import com.gytrinket.gytrinket.core.attack_mode.burst_fire.ProjectileBurstManager;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * TACZ（永恒枪械工坊：零）1.19.2 可选联动。
 * <p>
 * 与 1.20.1 分支的 {@code TaczCompat} 语义一致：
 * <ul>
 *   <li>弹射物点射冷却期间取消 TACZ 的 {@code GunShootEvent} / {@code GunFireEvent}，
 *       避免冷却期内继续开枪绕过点射节奏；</li>
 *   <li>读取枪械数据实际射击间隔（RPM/配件/热量修正）作为点射攻击冷却的攻速基准。</li>
 * </ul>
 * <p>
 * 1.19.2 工程当前没有可用的 TACZ jar，因此这里**不直接引用 TACZ 类型**，全部通过反射调用：
 * TACZ 未安装时 {@link #init()} 立即返回，类加载不会触碰任何 TACZ 类；安装后自动启用。
 * 反射只在初始化与每次枪击/点射时发生，不在 tick 热路径。
 */
public final class TaczCompat {

    public static final String TACZ_MODID = "tacz";

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean warned;

    private static Method getIGunOrNull;
    private static Method getGunId;
    private static Method getFireMode;
    private static Method getCommonGunIndex;
    private static Method getGunData;
    private static Method getShootInterval;

    private TaczCompat() {
    }

    /** 在 compat 模块注册阶段调用；TACZ 未安装时静默失效。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded(TACZ_MODID)) {
            return;
        }
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> timelessApi = Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> commonGunIndex = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            Class<?> gunData = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            Class<?> fireMode = Class.forName("com.tacz.guns.api.item.gun.FireMode");

            getIGunOrNull = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            getGunId = iGunClass.getMethod("getGunId", ItemStack.class);
            getFireMode = iGunClass.getMethod("getFireMode", ItemStack.class);
            getCommonGunIndex = timelessApi.getMethod("getCommonGunIndex", ResourceLocation.class);
            getGunData = commonGunIndex.getMethod("getGunData");
            getShootInterval = gunData.getMethod("getShootInterval", LivingEntity.class, fireMode);

            registerCancelable(Class.forName("com.tacz.guns.api.event.common.GunShootEvent"));
            registerCancelable(Class.forName("com.tacz.guns.api.event.common.GunFireEvent"));
            available = true;
            gytrinket.LOGGER.info("[tacz] 1.19.2 联动已启用（反射模式：点射冷却拦截 + 枪械射速）");
        } catch (Throwable throwable) {
            available = false;
            gytrinket.LOGGER.warn("[tacz] 1.19.2 联动初始化失败，已降级为无联动", throwable);
        }
    }

    public static boolean isTaczLoaded() {
        return ModList.get().isLoaded(TACZ_MODID);
    }

    /**
     * 读取 TACZ 枪械数据实际射击间隔换算的射速（发/秒）；非 TACZ 枪或联动不可用时返回 null。
     * 反射缓存命中后每次调用只有几次 Method.invoke，不进入 tick 循环。
     */
    public static Double getGunFireRate(LivingEntity shooter, ItemStack gunItemStack) {
        if (!available) {
            return null;
        }
        try {
            Object iGun = getIGunOrNull.invoke(null, gunItemStack);
            if (iGun == null) {
                return null;
            }
            ResourceLocation gunId = (ResourceLocation) getGunId.invoke(iGun, gunItemStack);
            if (gunId == null) {
                return null;
            }
            Optional<?> index = (Optional<?>) getCommonGunIndex.invoke(null, gunId);
            if (index == null || index.isEmpty()) {
                return null;
            }
            Object data = getGunData.invoke(index.get());
            if (data == null) {
                return null;
            }
            Object mode = getFireMode.invoke(iGun, gunItemStack);
            long intervalMs = (long) getShootInterval.invoke(data, shooter, mode);
            return intervalMs > 0L ? 1000.0D / intervalMs : null;
        } catch (Throwable throwable) {
            warnOnce("读取 TACZ 枪械射速失败", throwable);
            return null;
        }
    }

    private static void registerCancelable(Class<?> eventClass) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                (Class) eventClass, (Consumer) event -> handleGunEvent(eventClass, event));
    }

    private static void handleGunEvent(Class<?> eventClass, Object event) {
        if (!(event instanceof Event forgeEvent)) {
            return;
        }
        try {
            LivingEntity shooter = (LivingEntity) eventClass.getMethod("getShooter").invoke(event);
            ItemStack stack = (ItemStack) eventClass.getMethod("getGunItemStack").invoke(event);
            if (shouldBlockGunAction(shooter, stack)) {
                forgeEvent.setCanceled(true);
            }
        } catch (Throwable throwable) {
            warnOnce("处理 TACZ 射击事件失败", throwable);
        }
    }

    private static boolean shouldBlockGunAction(LivingEntity shooter, ItemStack gunItemStack) {
        if (!(shooter instanceof Player player)) {
            return false;
        }
        if (player.getLevel().isClientSide) {
            return player.getCooldowns().isOnCooldown(gunItemStack.getItem());
        }
        return shooter instanceof ServerPlayer
                && ProjectileBurstManager.isInProjectileBurstCooldown(player);
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (!warned) {
            warned = true;
            gytrinket.LOGGER.warn("[tacz] {}（后续同类错误不再刷屏）", message, throwable);
        }
    }
}
