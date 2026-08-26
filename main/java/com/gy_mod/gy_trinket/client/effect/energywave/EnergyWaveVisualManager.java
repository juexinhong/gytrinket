package com.gy_mod.gy_trinket.client.effect.energywave;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.client.EnergyWaveVolumetricRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 能量波视觉特效公共管理器。
 * <p>
 * 统一管理蜂群和能量波爆炸的视觉特效，支持着色器和矢量两种渲染后端。
 * 光影模式下使用体积渲染器，通过保存/恢复矩阵兼容Iris composite pass。
 */
public class EnergyWaveVisualManager {

    // ===== 动画常量 =====
    public static final int GROWTH_TICKS = 3;
    public static final int EXPAND_TICKS = 8;
    public static final int TOTAL_DURATION_TICKS = GROWTH_TICKS + EXPAND_TICKS;
    public static final float END_SCALE = 3.5f;

    /** 默认颜色方案：黄→橙→红 */
    private static final float[] DEFAULT_COLORS = {
        0.9f, 0.9f, 0.0f, 1.0f,   // center: bright yellow
        0.9f, 0.6f, 0.0f, 0.8f,   // color: orange
        0.9f, 0.35f, 0.0f, 0.7f,  // outer: red-orange
        0.9f, 0.35f, 0.0f, 0.7f   // bloom: red-orange
    };

    /** 蓝色颜色方案：亮蓝→深蓝→蓝紫 */
    private static final float[] BLUE_COLORS = {
        0.3f, 0.7f, 1.0f, 1.0f,   // center: bright sky blue
        0.2f, 0.4f, 0.95f, 0.8f,  // color: medium blue
        0.4f, 0.15f, 0.85f, 0.7f, // outer: blue-purple
        0.4f, 0.15f, 0.85f, 0.7f  // bloom: blue-purple
    };

    /**
     * 获取颜色方案
     * @param colorType 0 = 默认（黄橙红），1 = 蓝色系
     */
    private static float[] getColors(int colorType) {
        return colorType == 1 ? BLUE_COLORS : DEFAULT_COLORS;
    }

    // ===== 蜂群默认参数 =====
    private static final float SWARM_SIZE_SCALE = 0.25f;
    private static final float SWARM_WIDTH_COEFF = 1.2f;
    private static final float SWARM_LENGTH_COEFF = 14.0f;
    private static final float SWARM_CENTER_WIDTH_MULT = 0.8f;
    private static final float SWARM_CENTER_LENGTH_MULT = 0.8f;
    private static final float SWARM_COLOR_WIDTH_MULT = 1.1f;
    private static final float SWARM_COLOR_LENGTH_MULT = 1.2f;
    private static final float SWARM_OUTER_WIDTH_MULT = 1.2f;
    private static final float SWARM_OUTER_LENGTH_MULT = 1.5f;

    // ===== 爆炸参数 =====
    private static final float EXPLOSION_CENTER_WIDTH_MULT = 0.8f;
    private static final float EXPLOSION_CENTER_LENGTH_MULT = 0.8f;
    private static final float EXPLOSION_COLOR_WIDTH_MULT = 1.1f;
    private static final float EXPLOSION_COLOR_LENGTH_MULT = 1.2f;
    private static final float EXPLOSION_OUTER_WIDTH_MULT = 1.2f;
    private static final float EXPLOSION_OUTER_LENGTH_MULT = 1.5f;
    private static final double EXPLOSION_LENGTH_CAP = 15.0;
    /** 爆炸能量波膨胀阶段最终倍率（蜂群用3.5因为基础尺寸很小，爆炸基础尺寸大所以用1.2） */
    private static final float EXPLOSION_END_SCALE = 1.2f;

    // ===== 能量波统一机制 =====
    /** 所有能量波的长度极限（格），同时也是攻击范围极限 */
    public static final double WAVE_LENGTH_CAP = 20.0;

    /**
     * 能量波前移量（格），随长度增加而增加：0长度对应0，长度极限20格对应1。
     * 抵消波后泛光后移感，并避免短波时相机处于波内外中间态产生视觉错误。
     * 伤害范围长度需加上该前移量（与能量波使用同一长度计算）以对齐能量波尖端。
     */
    public static double computeForwardShift(double length) {
        return Math.min(length, WAVE_LENGTH_CAP) / WAVE_LENGTH_CAP;
    }

    /**
     * 能量波宽度 = 长度/20（长宽比 1:20），宽度极限 = 长度/12。
     * 宽度跟随能量波机制动态变化。
     */
    public static double waveWidth(double length) {
        return Math.min(length / 20.0, length / 12.0);
    }

    private static final List<WaveVisualData> waves = new CopyOnWriteArrayList<>();

    // ===== 光影兼容：保存的矩阵状态 =====
    // 在AFTER_PARTICLES（Iris composite之前）保存，在AFTER_LEVEL（composite之后）使用
    private static Matrix4f savedProjectionMatrix = null;
    private static Matrix4f savedModelViewMatrix = null;
    private static Matrix4f savedPoseStackMatrix = null;

    /**
     * 添加蜂群能量波视觉（使用蜂群默认尺寸参数）
     */
    public static void addSwarmWave(int entityId, double x, double y, double z,
                                     double dirX, double dirY, double dirZ, boolean isRepair) {
        long currentTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;

        // 计算蜂群默认的目标尺寸（满生长时 currentScale = SWARM_SIZE_SCALE）
        float scale = SWARM_SIZE_SCALE;
        float targetCenterHW = SWARM_WIDTH_COEFF * scale * 0.1f * SWARM_CENTER_WIDTH_MULT;
        float targetCenterLen = SWARM_LENGTH_COEFF * scale * 0.1f * SWARM_CENTER_LENGTH_MULT;
        float targetColorHW = SWARM_WIDTH_COEFF * scale * 0.1f * SWARM_COLOR_WIDTH_MULT;
        float targetColorLen = SWARM_LENGTH_COEFF * scale * 0.1f * SWARM_COLOR_LENGTH_MULT;
        float targetOuterHW = SWARM_WIDTH_COEFF * scale * 0.1f * SWARM_OUTER_WIDTH_MULT;
        float targetOuterLen = SWARM_LENGTH_COEFF * scale * 0.1f * SWARM_OUTER_LENGTH_MULT;

        waves.add(new WaveVisualData(entityId, -1, x, y, z, dirX, dirY, dirZ, isRepair, currentTime,
            targetCenterHW, targetCenterLen, targetColorHW, targetColorLen, targetOuterHW, targetOuterLen,
            0.9f, 0.9f, 0.0f, 1.0f,   // center color
            0.9f, 0.6f, 0.0f, 0.8f,   // color layer
            0.9f, 0.35f, 0.0f, 0.7f,  // outer layer
            0.9f, 0.35f, 0.0f, 0.7f,  // bloom color
            END_SCALE, TOTAL_DURATION_TICKS, 0.0f  // 蜂群波尺寸小，使用基础持续时间，无偏移
        ));
    }

    /**
     * 添加能量波爆炸视觉（无位置同步，默认颜色）。
     */
    public static void addExplosionWave(double x, double y, double z,
                                         double dirX, double dirY, double dirZ,
                                         double splashLength) {
        addExplosionWave(x, y, z, dirX, dirY, dirZ, splashLength, -1, 0, 0.0);
    }

    /**
     * 添加能量波爆炸视觉（支持位置同步，默认颜色）。
     */
    public static void addExplosionWave(double x, double y, double z,
                                         double dirX, double dirY, double dirZ,
                                         double splashLength, int positionSyncEntityId) {
        addExplosionWave(x, y, z, dirX, dirY, dirZ, splashLength, positionSyncEntityId, 0, 0.0);
    }

    /**
     * 添加能量波爆炸视觉（支持位置同步和颜色）。
     */
    public static void addExplosionWave(double x, double y, double z,
                                         double dirX, double dirY, double dirZ,
                                         double splashLength, int positionSyncEntityId, int colorType) {
        addExplosionWave(x, y, z, dirX, dirY, dirZ, splashLength, positionSyncEntityId, colorType, 0.0);
    }

    /**
     * 添加能量波爆炸视觉（完整参数）。
     * 中心层长度 = 溅射长度 × 0.3（极限15格），宽度 = 转化后长度/12（宽度极限 = 转化后长度/6）
     *
     * @param positionSyncEntityId 位置同步实体ID（-1 = 固定位置，>= 0 = 跟随实体位置但保持初始方向）
     * @param colorType            颜色方案（0 = 默认黄橙红，1 = 蓝色系）
     * @param offsetDistance        位置同步时的沿方向偏移距离（格）
     */
    public static void addExplosionWave(double x, double y, double z,
                                         double dirX, double dirY, double dirZ,
                                         double splashLength, int positionSyncEntityId, int colorType, double offsetDistance) {
        long currentTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;

        // 溅射长度 × 0.4 转化为能量波长度
        double convertedLen = splashLength * 0.75;
        // 长度截断
        double cappedLen = Math.min(convertedLen, EXPLOSION_LENGTH_CAP);
        // 宽度规则：
        // 1. 未达极限：宽度 = 转化长度 / 14（随长度成长）
        // 2. 达到极限后：基础宽度 = 极限值 / 14（停止成长），超出长度以 1/28 比例提升宽度
        // 3. 宽度不超过 极限值 / 10
        double excessLen = Math.max(0, convertedLen - EXPLOSION_LENGTH_CAP);
        double hw = cappedLen / 14.0 + excessLen / 28.0;
        hw = Math.min(hw, EXPLOSION_LENGTH_CAP / 10.0);

        float targetCenterHW = (float) hw * EXPLOSION_CENTER_WIDTH_MULT;
        float targetCenterLen = (float) cappedLen * EXPLOSION_CENTER_LENGTH_MULT;
        float targetColorHW = (float) hw * EXPLOSION_COLOR_WIDTH_MULT;
        float targetColorLen = (float) cappedLen * EXPLOSION_COLOR_LENGTH_MULT;
        float targetOuterHW = (float) hw * EXPLOSION_OUTER_WIDTH_MULT;
        float targetOuterLen = (float) cappedLen * EXPLOSION_OUTER_LENGTH_MULT;

        float[] colors = getColors(colorType);

        // 根据波大小动态计算持续时间：越大存在越久
        // 基础11 ticks + 中心层长度 × 2.0，使大波有足够时间展示
        int durationTicks = TOTAL_DURATION_TICKS + Math.round(targetCenterLen * 0.5f);

        waves.add(new WaveVisualData(-1, positionSyncEntityId, x, y, z, dirX, dirY, dirZ, false, currentTime,
            targetCenterHW, targetCenterLen, targetColorHW, targetColorLen, targetOuterHW, targetOuterLen,
            colors[0], colors[1], colors[2], colors[3],     // center color
            colors[4], colors[5], colors[6], colors[7],     // color layer
            colors[8], colors[9], colors[10], colors[11],   // outer layer
            colors[12], colors[13], colors[14], colors[15], // bloom color
            EXPLOSION_END_SCALE, durationTicks, (float) offsetDistance
        ));
    }

    /**
     * 渲染事件处理器：自动分发到体积或矢量渲染器。
     * 光影模式下使用保存矩阵机制兼容Iris composite pass：
     * - AFTER_PARTICLES：保存正确的矩阵（Iris composite之前）
     * - AFTER_LEVEL：使用保存的矩阵渲染（Iris composite之后）
     */
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 阴影pass中跳过渲染
        if (ShaderModCompat.isRenderingShadows()) return;

        long currentTime = mc.level.getGameTime();
        waves.removeIf(w -> w.isExpired(currentTime));
        if (waves.isEmpty()) return;

        boolean shaderActive = ShaderModCompat.isShaderPackInUse();

        if (shaderActive) {
            // 光影模式：保存矩阵 + 延迟到AFTER_LEVEL渲染
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                // Iris composite之前：保存正确的矩阵状态
                savedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
                savedModelViewMatrix = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
                savedPoseStackMatrix = new Matrix4f(event.getPoseStack().last().pose());
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                // Iris composite之后：使用保存的矩阵渲染体积能量波
                EnergyWaveVolumetricRenderer.renderWavesWithSavedMatrices(
                    event, waves,
                    savedProjectionMatrix, savedModelViewMatrix, savedPoseStackMatrix);
            }
        } else {
            // 非光影模式：直接在AFTER_PARTICLES渲染
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                EnergyWaveVolumetricRenderer.renderWaves(event, waves);
            }
        }
    }

    /**
     * 获取蜂群实体的渲染位置和朝向（partialTick插值）
     */
    public static Vec3 resolveSwarmPosition(Entity swarm, float partialTick) {
        Vec3 dir = swarm.getLookAngle().normalize();
        double x = Mth.lerp(partialTick, swarm.xOld, swarm.getX()) + dir.x * 0.3;
        double y = Mth.lerp(partialTick, swarm.yOld, swarm.getY()) + swarm.getBbHeight() * 0.4 + dir.y * 0.3;
        double z = Mth.lerp(partialTick, swarm.zOld, swarm.getZ()) + dir.z * 0.3;
        return new Vec3(x, y, z);
    }

    /**
     * 解析波的原点和方向（支持实体跟随、位置同步和固定位置）
     * <p>
     * entityId >= 0：蜂群模式，同步位置和方向
     * positionSyncEntityId >= 0：位置同步模式，同步位置但保持初始方向
     * 其他：固定位置和方向
     */
    public static WaveTransform resolveTransform(WaveVisualData wave, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && wave.entityId >= 0) {
            Entity entity = mc.level.getEntity(wave.entityId);
            if (entity != null && entity.isAlive()) {
                Vec3 pos = resolveSwarmPosition(entity, partialTick);
                Vec3 dir = entity.getLookAngle().normalize();
                return new WaveTransform(pos, dir);
            }
        }
        // 位置同步模式：跟随实体位置，但保持初始方向，并沿方向应用偏移
        if (mc.level != null && wave.positionSyncEntityId >= 0) {
            Entity entity = mc.level.getEntity(wave.positionSyncEntityId);
            if (entity != null && entity.isAlive()) {
                Vec3 dir = new Vec3(wave.dirX, wave.dirY, wave.dirZ).normalize();
                Vec3 basePos = new Vec3(
                    Mth.lerp(partialTick, entity.xOld, entity.getX()),
                    Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() / 2.0,
                    Mth.lerp(partialTick, entity.zOld, entity.getZ())
                );
                Vec3 pos = basePos.add(dir.scale(wave.offsetDistance));
                return new WaveTransform(pos, dir);
            }
        }
        return new WaveTransform(
            new Vec3(wave.x, wave.y, wave.z),
            new Vec3(wave.dirX, wave.dirY, wave.dirZ).normalize()
        );
    }

    /**
     * 动态波客户端变换：锚定到归属玩家实体，用 xOld/x、yRotO/yRot 线性插值
     * （与光环渲染贴图同款防抖），位置为身高一半处朝向前 1 格。
     * 若归属实体不可用，回退到上一 tick 与当前 tick 的插值。
     */
    public static WaveTransform resolveDynamicTransform(WaveVisualData wave, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && wave.isDynamic() && wave.positionSyncEntityId >= 0) {
            Entity entity = mc.level.getEntity(wave.positionSyncEntityId);
            if (entity instanceof LivingEntity living) {
                double px = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double py = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5;
                double pz = Mth.lerp(partialTick, entity.zOld, entity.getZ());

                float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
                float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
                float yawRad = yaw * (float) Math.PI / 180.0f;
                float pitchRad = pitch * (float) Math.PI / 180.0f;
                Vec3 dir = new Vec3(
                        -Math.sin(yawRad) * Math.cos(pitchRad),
                        -Math.sin(pitchRad),
                        Math.cos(yawRad) * Math.cos(pitchRad)
                ).normalize();

                Vec3 pos = new Vec3(px, py, pz).add(dir.scale(1.0));
                return new WaveTransform(pos, dir);
            }
        }
        if (wave.isDynamic() && wave.hasPrev) {
            Vec3 pos = new Vec3(
                    Mth.lerp(partialTick, wave.prevX, wave.x),
                    Mth.lerp(partialTick, wave.prevY, wave.y),
                    Mth.lerp(partialTick, wave.prevZ, wave.z)
            );
            Vec3 dir = new Vec3(
                    Mth.lerp(partialTick, wave.prevDirX, wave.dirX),
                    Mth.lerp(partialTick, wave.prevDirY, wave.dirY),
                    Mth.lerp(partialTick, wave.prevDirZ, wave.dirZ)
            );
            if (dir.lengthSqr() < 1e-8) {
                dir = new Vec3(wave.dirX, wave.dirY, wave.dirZ);
            }
            dir = dir.normalize();
            return new WaveTransform(pos, dir);
        }
        Vec3 pos = new Vec3(wave.x, wave.y, wave.z);
        Vec3 dir = new Vec3(wave.dirX, wave.dirY, wave.dirZ).normalize();
        return new WaveTransform(pos, dir);
    }

    /**
     * 计算动画进度
     */
    public static float getProgress(WaveVisualData wave, long currentTime, float partialTick) {
        return Math.min((currentTime - wave.startTime + partialTick) / (float) wave.durationTicks, 1.0f);
    }

    /**
     * 计算生长和膨胀阶段进度
     *
     * @param totalProgress 总进度（0~1）
     * @param durationTicks 该波的总持续时间（ticks）
     * @param endScale      膨胀阶段最终缩放倍率
     */
    public static AnimationState computeAnimation(float totalProgress, int durationTicks, float endScale) {
        // 保持原始的生长/膨胀比例（3:8）
        float growthFraction = (float) GROWTH_TICKS / TOTAL_DURATION_TICKS;
        float expandFraction = (float) EXPAND_TICKS / TOTAL_DURATION_TICKS;

        float growthProgress = Math.min(totalProgress / growthFraction, 1.0f);
        float expandProgress = totalProgress > growthFraction
            ? (totalProgress - growthFraction) / expandFraction
            : 0.0f;
        expandProgress = Math.min(expandProgress, 1.0f);

        // 生长阶段：尺寸从0到1；膨胀阶段：尺寸从1到endScale
        float sizeMultiplier = growthProgress < 1.0f
            ? growthProgress
            : 1.0f + (endScale - 1.0f) * expandProgress;

        float fadeAlpha = expandProgress > 0 ? 1.0f - expandProgress : 1.0f;
        float darkenFactor = expandProgress > 0 ? 1.0f - expandProgress * 0.5f : 1.0f;

        return new AnimationState(sizeMultiplier, fadeAlpha, darkenFactor);
    }

    public static Vec3 findUp(Vec3 forward) {
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(forward.dot(up)) > 0.99) {
            up = new Vec3(1, 0, 0);
        }
        return up.subtract(forward.scale(forward.dot(up))).normalize();
    }

    // ===== 数据类 =====

    public record WaveTransform(Vec3 position, Vec3 direction) {}

    public record AnimationState(float sizeMultiplier, float fadeAlpha, float darkenFactor) {}

    public static class WaveVisualData {
        public final int entityId; // -1 = 固定位置（蜂群模式：>= 0 同步位置和方向）
        public final int positionSyncEntityId; // -1 = 固定位置，>= 0 = 同步位置但保持初始方向
        public final double x, y, z;
        public final double dirX, dirY, dirZ;
        public final boolean isRepair;
        public final long startTime;

        // 目标尺寸（满生长时的尺寸，膨胀阶段在此基础上 × endScale）
        public final float targetCenterHW, targetCenterLen;
        public final float targetColorHW, targetColorLen;
        public final float targetOuterHW, targetOuterLen;

        // 层颜色
        public final float centerR, centerG, centerB, centerAlpha;
        public final float colorR, colorG, colorB, colorAlpha;
        public final float outerR, outerG, outerB, outerAlpha;
        public final float bloomR, bloomG, bloomB, bloomAlpha;

        // 膨胀阶段最终缩放倍率
        public final float endScale;

        // 该波的总持续时间（ticks），根据波大小动态计算
        public final int durationTicks;

        // 位置同步时的沿方向偏移距离（格）
        public final float offsetDistance;

        /** 动态能量波：可随时更新长度/宽度/朝向，不随时间消退 */
        public boolean dynamic = false;

        // 动态波插值：上一 tick 的位置/朝向（客户端线性插值，消除旋转卡顿）
        public double prevX, prevY, prevZ;
        public double prevDirX, prevDirY, prevDirZ;
        public boolean hasPrev = false;

        // 动态波长度/宽度（当前与上一 tick），用于渲染时平滑尺寸变化
        public float len, width;
        public float prevLen, prevWidth;
        // 客户端显示长度/宽度（向目标缓动，消除充能/消退时的长度抖动）
        public float displayLen, displayWidth;
        public boolean displayInitialized = false;

        public WaveVisualData(int entityId, int positionSyncEntityId, double x, double y, double z,
                              double dirX, double dirY, double dirZ,
                              boolean isRepair, long startTime,
                              float targetCenterHW, float targetCenterLen,
                              float targetColorHW, float targetColorLen,
                              float targetOuterHW, float targetOuterLen,
                              float centerR, float centerG, float centerB, float centerAlpha,
                              float colorR, float colorG, float colorB, float colorAlpha,
                              float outerR, float outerG, float outerB, float outerAlpha,
                              float bloomR, float bloomG, float bloomB, float bloomAlpha,
                              float endScale, int durationTicks, float offsetDistance) {
            this.entityId = entityId;
            this.positionSyncEntityId = positionSyncEntityId;
            this.x = x; this.y = y; this.z = z;
            this.dirX = dirX; this.dirY = dirY; this.dirZ = dirZ;
            this.isRepair = isRepair;
            this.startTime = startTime;
            this.targetCenterHW = targetCenterHW; this.targetCenterLen = targetCenterLen;
            this.targetColorHW = targetColorHW; this.targetColorLen = targetColorLen;
            this.targetOuterHW = targetOuterHW; this.targetOuterLen = targetOuterLen;
            this.centerR = centerR; this.centerG = centerG; this.centerB = centerB; this.centerAlpha = centerAlpha;
            this.colorR = colorR; this.colorG = colorG; this.colorB = colorB; this.colorAlpha = colorAlpha;
            this.outerR = outerR; this.outerG = outerG; this.outerB = outerB; this.outerAlpha = outerAlpha;
            this.bloomR = bloomR; this.bloomG = bloomG; this.bloomB = bloomB; this.bloomAlpha = bloomAlpha;
            this.endScale = endScale;
            this.durationTicks = durationTicks;
            this.offsetDistance = offsetDistance;
        }

        public boolean isExpired(long currentTime) {
            return currentTime - startTime >= durationTicks;
        }

        public boolean isDynamic() {
            return dynamic;
        }
    }
}
