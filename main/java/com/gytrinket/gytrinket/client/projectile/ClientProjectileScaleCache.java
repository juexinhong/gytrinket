package com.gytrinket.gytrinket.client.projectile;

import com.gytrinket.gytrinket.client.storage.ClientPlayerStoreManager;
import com.gytrinket.gytrinket.compat.CuriosCompat;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.projectile.ProjectileSizeManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端弹射物渲染缩放（缓存 + 本地推导）
 * <p>
 * 取值优先级（{@link #getRenderScale}）：
 * <ol>
 *   <li>已收到服务端包 → 缓存值（权威：含禁用系统等仅服务端可知的差异）；</li>
 *   <li>未收到且弹射物归属本地玩家 → 本地即时推导（零滞后，消除 1 tick 模型延迟）；</li>
 *   <li>其余（其他玩家的弹射物、包未到）→ 1.0。</li>
 * </ol>
 * <p>
 * 缓存由 ProjectileScalePayload（S -> C）写入，弹射物从世界移除时清理
 * （{@code ClientProjectileScaleEvents}），防止 entityId 被复用导致错误缩放。
 */
public class ClientProjectileScaleCache {

    /**
     * weapon_projectile_size 组当前的百分比属性名。
     * 客户端没有属性组定义表（组注册仅服务端），故由服务端组名拼接属性名求和；
     * 若未来组内新增属性，本地推导会暂时少算，服务端包到达后由缓存值纠正。
     */
    private static final String SIZE_ATTRIBUTE = ProjectileSizeManager.SIZE_GROUP + "_percent";

    private static final Map<Integer, Float> SCALES = new ConcurrentHashMap<>();

    /** 本地推导按 tick 缓存（仅渲染线程访问）：同游戏刻内多弹射物渲染复用同一结果 */
    private static long localScaleTick = Long.MIN_VALUE;
    private static float localScaleCache = 1.0F;

    public static void put(int entityId, float scale) {
        if (scale == 1.0F) {
            SCALES.remove(entityId);
        } else {
            SCALES.put(entityId, scale);
        }
    }

    public static void remove(int entityId) {
        SCALES.remove(entityId);
    }

    /**
     * 渲染路径取缩放：缓存优先（服务端权威值），未同步时本地玩家的弹射物即时推导。
     */
    public static float getRenderScale(Projectile projectile) {
        Float cached = SCALES.get(projectile.getId());
        if (cached != null) {
            return cached;
        }
        if (projectile.getOwner() instanceof LocalPlayer localPlayer) {
            return computeLocalScale(localPlayer);
        }
        return 1.0F;
    }

    /**
     * 本地推导（按 tick 缓存）：无属性玩家的弹射物服务端不发包（缓存永不命中），
     * 若每次渲染都全量扫描装备栏，弹幕场景下每帧会产生大量临时对象；
     * 同游戏刻内只推导一次，之后复用。
     */
    private static float computeLocalScale(LocalPlayer player) {
        long tick = player.level().getGameTime();
        if (tick != localScaleTick) {
            localScaleTick = tick;
            localScaleCache = doComputeLocalScale(player);
        }
        return localScaleCache;
    }

    /**
     * 本地推导：与 ProjectileSizeManager.computeScale 同公式（组值 = 1 + 组内百分比之和）。
     * 装备范围 = 光点核心存储 + Curios 饰品栏，同 id 物品只计一次，均与服务端一致。
     * 客户端无禁用系统数据：被禁用物品会多算，待服务端包到达后由缓存值覆盖纠正。
     */
    private static float doComputeLocalScale(LocalPlayer player) {
        Set<String> processed = new HashSet<>();
        double percentSum = 0.0;

        ClientPlayerStoreManager.ClientPlayerStore store =
                ClientPlayerStoreManager.getClientStore(player.getUUID());
        if (store != null) {
            for (int i = 0; i < store.getSlotCount(); i++) {
                percentSum += accumulate(store.getStackInSlot(i), processed);
            }
        }
        if (CuriosCompat.isCuriosLoaded()) {
            for (ItemStack stack : CuriosCompat.getEquippedCurios(player)) {
                percentSum += accumulate(stack, processed);
            }
        }

        return (float) Math.max(ProjectileSizeManager.MIN_SCALE, 1.0 + percentSum);
    }

    /** 单件物品的弹射物大小百分比贡献（同 id 只计一次；未注册属性的物品为 0） */
    private static double accumulate(ItemStack stack, Set<String> processed) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!processed.add(itemId)) {
            return 0.0;
        }
        ItemAttributeConfig config = AttributeManager.getItemAttributes(itemId);
        if (config == null) {
            return 0.0;
        }
        return config.getAttributes().getOrDefault(SIZE_ATTRIBUTE, 0.0);
    }
}
