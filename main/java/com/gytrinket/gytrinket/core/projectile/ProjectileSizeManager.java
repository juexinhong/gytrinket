package com.gytrinket.gytrinket.core.projectile;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.ProjectileScalePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 弹射物大小管理器
 * <p>
 * 当归属玩家的弹射物加入世界时，读取玩家的 {@code weapon_projectile_size} 属性组
 * （百分比之和），并通过 {@link ProjectileScalePayload} 同步缩放值到客户端，
 * 渲染时由 {@code EntityRenderDispatcherMixin} 应用 pose 缩放。
 * <p>
 * 注意：缩放<b>仅影响客户端渲染</b>，不改变弹射物碰撞箱——原版弹射物的实体命中
 * 判定是「中心位置线段 vs 目标实体 AABB」（{@code ProjectileUtil.getEntityHitResult}），
 * 弹射物自身碰撞箱不参与精确命中，修改碰撞箱无法改变命中范围。
 * <p>
 * 覆盖范围：所有 {@link Projectile} 及其子类（与弹射物爆炸机制一致）。
 * 网络同步时序：EntityJoinLevelEvent 触发时追踪者尚未建立（spawn 包也未发出），
 * 故加入 pending 队列，下一服务端 tick 统一发送 TRACKING_ENTITY 包。
 * 客户端缓存在弹射物从世界移除时清理，防止 entityId 复用导致错误缩放。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ProjectileSizeManager {

    /** weapon_projectile_size 属性组名（客户端本地推导经此拼接属性名） */
    public static final String SIZE_GROUP = "weapon_projectile_size";

    /** 缩放下限，防止属性负值把模型缩没了 */
    public static final float MIN_SCALE = 0.05F;

    /** 等待同步的弹射物（加入世界当刻收集，下一刻发送） */
    private static final List<PendingSync> PENDING_SYNC = new ArrayList<>();

    private record PendingSync(Entity entity, float scale) {}

    /**
     * 计算玩家弹射物缩放值：weapon_projectile_size 组值。
     * <p>
     * 注意：getGroupAttribute 对 PERCENT 组已返回「1 + 组内百分比之和」（PERCENT 终值自带 +1 基数），
     * 即该组值本身就是缩放倍率，不能再叠加 1.0（否则无属性玩家也会 ×2）。
     */
    public static float computeScale(UUID playerUUID) {
        double groupValue = AttributeManager.getGroupAttribute(playerUUID, SIZE_GROUP);
        return (float) Math.max(MIN_SCALE, groupValue);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }

        if (!(projectile.getOwner() instanceof ServerPlayer player)) {
            return;
        }

        float scale = computeScale(player.getUUID());
        if (scale == 1.0F) {
            return;
        }

        // 客户端模型缩放：下一刻随追踪者建立后同步
        PENDING_SYNC.add(new PendingSync(projectile, scale));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_SYNC.isEmpty()) {
            return;
        }

        for (PendingSync pending : PENDING_SYNC) {
            Entity entity = pending.entity();
            if (!entity.isAlive() || !entity.isAddedToLevel()) {
                continue;
            }
            PacketDistributor.sendToPlayersTrackingEntity(
                entity,
                new ProjectileScalePayload(entity.getId(), pending.scale()));
        }
        PENDING_SYNC.clear();
    }
}
