package com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 无人机子弹拖尾管理器。
 * <p>
 * 维护每颗活跃子弹对应的 {@link DroneBulletTrail}。
 * 使用全局渲染事件驱动，即使子弹实体被移除，拖尾仍能继续渲染到最终位置。
 */
public class DroneBulletTrailManager {
    private static final Map<ThrowableItemProjectile, DroneBulletTrail> trailMap = new HashMap<>();

    /**
     * 注册子弹的拖尾（由DroneBulletRenderer调用）。
     * 若该子弹尚未有拖尾对象则创建一个。
     */
    public static void registerTrail(ThrowableItemProjectile bullet) {
        if (bullet.isRemoved()) return;
        trailMap.computeIfAbsent(bullet, b -> new DroneBulletTrail(b));
    }

    /**
     * 全局渲染事件：渲染所有活跃的拖尾，并处理分离逻辑。
     */
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (trailMap.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partialTicks = event.getPartialTick();

        // 遍历所有拖尾，检测子弹是否已移除并触发分离
        Iterator<Map.Entry<ThrowableItemProjectile, DroneBulletTrail>> it = trailMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ThrowableItemProjectile, DroneBulletTrail> entry = it.next();
            DroneBulletTrail trail = entry.getValue();

            if (entry.getKey().isRemoved()) {
                if (!trail.isDetached()) {
                    trail.detach();
                }
            }

            // 渲染拖尾
            trail.render(event.getPoseStack(), partialTicks);

            // 移除已完成的拖尾
            if (trail.shouldBeRemoved()) {
                it.remove();
            }
        }
    }
}
