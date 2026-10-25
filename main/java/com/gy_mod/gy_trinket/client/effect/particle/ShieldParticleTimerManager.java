package com.gy_mod.gy_trinket.client.effect.particle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 延迟出场的粒子队列（护盾粒子一圈里除中心那几颗之外的，会延迟 2/4 刻出现）。
 * <p>
 * 注意：这里的粒子同样带着 {@code burstId}。即使它还没出场，只要同一只实体
 * 已经挨了更新的一下，它一出场就会被判定为"旧圈"并进入让位流程 ——
 * 先播完出现动画，再淡出。
 */
public class ShieldParticleTimerManager {

    private static ShieldParticleTimerManager instance;
    private final List<PendingParticle> pendingParticles = new CopyOnWriteArrayList<>();

    private ShieldParticleTimerManager() {}

    public static ShieldParticleTimerManager getInstance() {
        if (instance == null) {
            instance = new ShieldParticleTimerManager();
        }
        return instance;
    }

    public void addPendingParticle(int entityId,
                                   double originOffsetX, double originOffsetY, double originOffsetZ,
                                   double offsetX, double offsetY, double offsetZ,
                                   double dirX, double dirY, double dirZ,
                                   int delayTicks, int burstId) {
        pendingParticles.add(new PendingParticle(entityId, originOffsetX, originOffsetY, originOffsetZ,
                                                 offsetX, offsetY, offsetZ,
                                                 dirX, dirY, dirZ,
                                                 delayTicks, burstId));
    }

    public void tick() {
        pendingParticles.removeIf(p -> {
            if (p.delayTicks <= 0) {
                ShieldParticleRenderManager.getInstance().addParticle(
                    p.entityId, p.originOffsetX, p.originOffsetY, p.originOffsetZ,
                    p.offsetX, p.offsetY, p.offsetZ,
                    p.dirX, p.dirY, p.dirZ, p.burstId
                );
                return true;
            }
            p.delayTicks--;
            return false;
        });
    }

    private static class PendingParticle {
        final int entityId;
        final double originOffsetX, originOffsetY, originOffsetZ;
        final double offsetX, offsetY, offsetZ;
        final double dirX, dirY, dirZ;
        final int burstId;
        int delayTicks;

        PendingParticle(int entityId,
                       double originOffsetX, double originOffsetY, double originOffsetZ,
                       double offsetX, double offsetY, double offsetZ,
                       double dirX, double dirY, double dirZ,
                       int delayTicks, int burstId) {
            this.entityId = entityId;
            this.originOffsetX = originOffsetX;
            this.originOffsetY = originOffsetY;
            this.originOffsetZ = originOffsetZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.delayTicks = delayTicks;
            this.burstId = burstId;
        }
    }
}
