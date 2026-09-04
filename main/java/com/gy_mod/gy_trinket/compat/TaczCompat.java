package com.gy_mod.gy_trinket.compat;

import com.gy_mod.gy_trinket.core.attack_mode.burst_fire.ProjectileBurstManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * TACZ 永恒枪械（Zero，1.20.1 独占）可选联动：
 * <ul>
 * <li>弹射物点射冷却期间禁用枪械射击（事件拦截）</li>
 * <li>读取枪械数据设置的射速，作为弹射物点射攻击冷却的攻速修正值
 *     （{@link #getGunFireRate}，枪械的射击节奏由 TACZ 枪械数据决定，
 *     原版攻速属性对其无意义）</li>
 * </ul>
 * <p>
 * 背景：点射触发的攻击冷却挂在原版物品冷却上（枪械物品转圈），但 TACZ 枪械射击
 * 走自己的网络包与射击时间戳冷却，不检查原版物品冷却，导致点射冷却期间玩家
 * 仍可继续开枪（新弹射物加入世界会再次触发点射，绕过攻击冷却节奏）。
 * <p>
 * 拦截的 TACZ 射击事件（均可取消）：
 * <ul>
 * <li>{@link GunShootEvent}：扣动扳机的射击入口事件。服务端取消 → 不广播射击、
 *     不进入枪械射击逻辑；客户端取消 → 不发送射击包、不做本地射击预测
 *     （无幽灵音效/动画）。</li>
 * <li>{@link GunFireEvent}：每次击发事件（Burst 模式一次扳机多次触发），
 *     覆盖逻辑机脚本枪等绕过 GunShootEvent 直接调用射击 API 的路径。
 *     服务端取消 → 不击发、不消耗弹药；客户端取消 → 不播放击发动画与音效。</li>
 * </ul>
 * <p>
 * 判定条件：
 * <ul>
 * <li>服务端（权威拦截）：射手处于弹射物点射冷却中
 *     （{@link ProjectileBurstManager#isInProjectileBurstCooldown}，
 *     冷却 = 连击段数 × 攻击间隔，高攻速下可短于复制循环时长、提前解禁，
 *     不限定具体枪械——点射冷却期内换枪同样禁用）。</li>
 * <li>客户端（表现抑制）：射击枪械处于原版物品冷却中（服务端挂冷却时自动同步
 *     到客户端，用于抑制本地射击预测表现；服务端始终兜底拦截）。</li>
 * </ul>
 * <p>
 * 安全降级：本类由 {@link #init()} 在 TACZ 已加载时手动注册事件监听（不用
 * EventBusSubscriber 注解扫描），玩家未安装 TACZ 时本类不会被加载，联动完全静默失效。
 */
public class TaczCompat {

    public static final String TACZ_MODID = "tacz";

    private TaczCompat() {
    }

    /**
     * 初始化：仅在 TACZ 已加载时注册枪械射击拦截监听。
     * 需在 mod 主类构造阶段调用。
     */
    public static void init() {
        if (isTaczLoaded()) {
            MinecraftForge.EVENT_BUS.register(TaczCompat.class);
        }
    }

    /**
     * 判断 TACZ 永恒枪械模组是否已加载。
     */
    public static boolean isTaczLoaded() {
        return ModList.get().isLoaded(TACZ_MODID);
    }

    /**
     * 扣动扳机的射击入口事件：点射冷却期间取消射击
     */
    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (shouldBlockGunAction(event.getShooter(), event.getGunItemStack())) {
            event.setCanceled(true);
        }
    }

    /**
     * 每次击发事件：点射冷却期间取消击发（覆盖逻辑机脚本枪等直接调用射击 API 的路径）
     */
    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (shouldBlockGunAction(event.getShooter(), event.getGunItemStack())) {
            event.setCanceled(true);
        }
    }

    /**
     * 判断是否应拦截本次枪械动作：
     * 服务端（权威拦截）：射手处于弹射物点射冷却中（冷却 = 连击段数 × 攻击间隔，
     * 高攻速下可短于复制循环时长，不限定具体枪械）；
     * 客户端（表现抑制）：射击枪械处于原版物品冷却中（服务端挂冷却自动同步到客户端），
     * 用于抑制本地射击预测表现；服务端始终兜底拦截
     */
    private static boolean shouldBlockGunAction(LivingEntity shooter, ItemStack gunItemStack) {
        if (!(shooter instanceof Player player)) {
            return false;
        }

        if (player.level().isClientSide()) {
            return player.getCooldowns().isOnCooldown(gunItemStack.getItem());
        }

        return shooter instanceof ServerPlayer
            && ProjectileBurstManager.isInProjectileBurstCooldown(player);
    }

    /**
     * 读取 TACZ 枪械数据设置的射速（发/秒），作为弹射物点射攻击冷却的攻速修正值。
     * <p>
     * 射速取枪械实际开火间隔（{@code 60000 / RPM}，含配件 RPM 修正与枪管热量减射修正），
     * 与枪械实际射击节奏一致。非 TACZ 枪或 TACZ 未加载时返回 null，调用方回退默认攻速逻辑。
     * <p>
     * 类加载安全：TACZ 类型仅出现在内部方法中，TACZ 未加载时本方法提前返回不会被调用
     */
    public static Double getGunFireRate(LivingEntity shooter, ItemStack gunItemStack) {
        if (!isTaczLoaded()) {
            return null;
        }
        return getGunFireRateInternal(shooter, gunItemStack);
    }

    /**
     * 读取枪械实际射速（发/秒）：开火间隔 ms 换算
     */
    private static Double getGunFireRateInternal(LivingEntity shooter, ItemStack gunItemStack) {
        IGun iGun = IGun.getIGunOrNull(gunItemStack);
        if (iGun == null) {
            return null;
        }

        ResourceLocation gunId = iGun.getGunId(gunItemStack);
        if (gunId == null) {
            return null;
        }

        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    long intervalMs = index.getGunData()
                            .getShootInterval(shooter, iGun.getFireMode(gunItemStack), gunItemStack);
                    return intervalMs > 0 ? 1000.0 / intervalMs : null;
                })
                .orElse(null);
    }
}
