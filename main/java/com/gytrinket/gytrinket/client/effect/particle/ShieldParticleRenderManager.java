package com.gytrinket.gytrinket.client.effect.particle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 护盾粒子的客户端容器。
 * <p>
 * 让位规则（连续受击时的"波浪涌动"）：
 * <ul>
 *   <li>每次受击由服务端分配一个 {@code burstId}，同一批 19 颗共用；</li>
 *   <li>某只实体出现更新的 burstId 时，它<b>之前那批</b>的粒子（包括还在延迟队列里
 *       没出场的）全部标记为让位 —— 但只针对这只实体，别的实体不受影响；</li>
 *   <li>被标记的粒子不会立刻消失：先把出现动画走完，再在 2 刻内淡出。</li>
 * </ul>
 */
public class ShieldParticleRenderManager {

    /** 安全上限：防止异常情况下粒子无限堆积拖垮渲染 */
    private static final int MAX_PARTICLES = 256;

    /** 出现动画的时长（刻）：0.14 × 寿命 20 ≈ 2.8，取 3 */
    public static final int APPEAR_TICKS = 3;
    /** 让位淡出的时长（刻） */
    public static final int FADE_TICKS = 2;

    private static ShieldParticleRenderManager instance;
    private final List<ShieldParticleData> particles = new CopyOnWriteArrayList<>();

    /** 每只实体当前最新的受击编号 */
    private final Map<Integer, Integer> currentBurstId = new HashMap<>();

    private ShieldParticleRenderManager() {}

    public static ShieldParticleRenderManager getInstance() {
        if (instance == null) {
            instance = new ShieldParticleRenderManager();
        }
        return instance;
    }

    public void addParticle(int entityId,
                           double originOffsetX, double originOffsetY, double originOffsetZ,
                           double offsetX, double offsetY, double offsetZ,
                           double dirX, double dirY, double dirZ) {
        addParticle(entityId, originOffsetX, originOffsetY, originOffsetZ,
                    offsetX, offsetY, offsetZ, dirX, dirY, dirZ, 0);
    }

    /**
     * @param burstId 这一次受击的编号；&lt;= 0 表示不参与让位体系（待机粒子等）
     */
    public void addParticle(int entityId,
                           double originOffsetX, double originOffsetY, double originOffsetZ,
                           double offsetX, double offsetY, double offsetZ,
                           double dirX, double dirY, double dirZ,
                           int burstId) {
        if (burstId > 0) {
            int current = currentBurstId.getOrDefault(entityId, 0);
            if (burstId > current) {
                // 这只实体挨了新的一下：它之前那批全部让位（别的实体不动）
                currentBurstId.put(entityId, burstId);
                for (ShieldParticleData p : particles) {
                    if (p.entityId == entityId && p.burstId < burstId) {
                        markYield(p);
                    }
                }
            }
        }

        if (particles.size() >= MAX_PARTICLES) {
            return;
        }

        ShieldParticleData particle = new ShieldParticleData(entityId,
                originOffsetX, originOffsetY, originOffsetZ,
                offsetX, offsetY, offsetZ, dirX, dirY, dirZ,
                ThreadLocalRandom.current().nextFloat(),
                ThreadLocalRandom.current().nextFloat(),
                burstId);

        // 迟到的旧圈（延迟队列里排它后面的）：一出现就标记让位，
        // 但它照样会先完整播完出现动画，再淡出。
        if (burstId > 0 && burstId < currentBurstId.getOrDefault(entityId, 0)) {
            markYield(particle);
        }
        particles.add(particle);
    }

    private static void markYield(ShieldParticleData particle) {
        if (!particle.yielding) {
            particle.yielding = true;
            particle.yieldMarkAge = particle.age;
        }
    }

    public List<ShieldParticleData> getParticles() {
        return particles;
    }

    public void tick() {
        particles.removeIf(p -> {
            p.tick();
            if (p.yielding) {
                int startAge = Math.max(p.yieldMarkAge, APPEAR_TICKS);
                if (p.age >= startAge + FADE_TICKS) {
                    return true;
                }
            }
            return p.age >= p.lifetime;
        });
    }

    public void clear() {
        particles.clear();
        currentBurstId.clear();
    }
}
