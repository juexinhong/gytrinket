package com.gy_mod.gy_trinket.client.explosion;

import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 模拟爆炸贴图特效渲染器
 * <p>
 * 参考{@link com.gy_mod.gy_trinket.client.shield.type.AuraRenderer 光环护盾}的渲染贴图方法：
 * AFTER_PARTICLES 阶段 + POSITION_COLOR_TEX_LIGHTMAP 着色器 + 顶点色透明度 + 光照固定亮度15，水平铺在爆心位置。
 * <p>
 * 贴图为竖排 3 帧图集（simulated_explosion.png，128x384，每帧 128x128）：
 * - 播放 3 刻，每刻对应一帧，按顺序播放
 * - 透明度不绑定游戏刻，按渲染时间线性插值：帧起始依次为 100% → 70% → 40%，随后淡出至 0
 * - 最终帧圆的视觉直径 = 爆炸直径（2×半径），尺寸按贴图实际内容占比校准（128/98）
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class SimulatedExplosionFXRenderer {

    private static final ResourceLocation EXPLOSION_TEXTURE = new ResourceLocation(
        gytrinket.MODID, "textures/particle/simulated_explosion.png"
    );

    /** 每帧时长（毫秒）= 1 游戏刻 */
    private static final long FRAME_DURATION_MS = 50;
    /** 总帧数 */
    private static final int FRAME_COUNT = 3;
    /** 每帧起始透明度：帧1 100%、帧2 70%、帧3 40% */
    private static final float[] FRAME_START_ALPHAS = {1.0f, 0.7f, 0.4f};
    /**
     * 内容大小修正：按爆炸贴图实际内容占比校准。
     * 图集每帧 128x128，最终帧（第3帧）圆的实际直径为 98px（占比 98/128），
     * 放大 128/98 后最终帧圆的视觉直径 = 爆炸直径（2×半径），与实际爆炸半径精确一致；
     * 前 2 帧圆较小（30px/68px），呈现由小到大的扩散动画。
     */
    private static final double SIZE_CORRECTION = 128.0 / 98.0;

    private static final List<ExplosionEffect> EFFECTS = new ArrayList<>();

    private SimulatedExplosionFXRenderer() {}

    /** 添加一个模拟爆炸特效（由网络包在客户端主线程调用） */
    public static void addEffect(double x, double y, double z, double radius) {
        EFFECTS.add(new ExplosionEffect(x, y, z, radius, System.nanoTime()));
    }

    @SubscribeEvent
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (EFFECTS.isEmpty()) {
            return;
        }

        long nowNanos = System.nanoTime();

        // 移除播放完毕的特效
        Iterator<ExplosionEffect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            ExplosionEffect effect = iterator.next();
            if (elapsedMs(effect, nowNanos) >= FRAME_DURATION_MS * FRAME_COUNT) {
                iterator.remove();
            }
        }
        if (EFFECTS.isEmpty()) {
            return;
        }

        net.minecraft.world.phys.Vec3 camPos = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);
        RenderSystem.setShaderTexture(0, EXPLOSION_TEXTURE);

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);

        for (ExplosionEffect effect : EFFECTS) {
            long elapsedMs = elapsedMs(effect, nowNanos);

            float alpha = computeAlpha(elapsedMs);
            if (alpha <= 0.001f) {
                continue;
            }

            // 每刻对应一帧，按顺序播放
            int frameIndex = (int) (elapsedMs / FRAME_DURATION_MS);
            if (frameIndex < 0) frameIndex = 0;
            if (frameIndex > FRAME_COUNT - 1) frameIndex = FRAME_COUNT - 1;

            // quad 边长 = 爆炸直径 × 内容修正，最终帧圆的视觉直径 = 爆炸直径
            float halfSize = (float) (effect.radius * SIZE_CORRECTION);

            // 竖排 3 帧图集：每帧占 V 方向的 1/3
            float v0 = frameIndex / (float) FRAME_COUNT;
            float v1 = (frameIndex + 1) / (float) FRAME_COUNT;

            float px = (float) (effect.x - camPos.x);
            float py = (float) (effect.y - camPos.y);
            float pz = (float) (effect.z - camPos.z);

            // 光照固定亮度15（FULL_BRIGHT = 240 | 240<<16），特效不受环境光照影响
            bufferBuilder.vertex(matrix, px - halfSize, py, pz - halfSize).color(1.0f, 1.0f, 1.0f, alpha).uv(0.0f, v0).uv2(240, 240).endVertex();
            bufferBuilder.vertex(matrix, px - halfSize, py, pz + halfSize).color(1.0f, 1.0f, 1.0f, alpha).uv(0.0f, v1).uv2(240, 240).endVertex();
            bufferBuilder.vertex(matrix, px + halfSize, py, pz + halfSize).color(1.0f, 1.0f, 1.0f, alpha).uv(1.0f, v1).uv2(240, 240).endVertex();
            bufferBuilder.vertex(matrix, px + halfSize, py, pz - halfSize).color(1.0f, 1.0f, 1.0f, alpha).uv(1.0f, v0).uv2(240, 240).endVertex();
        }

        BufferUploader.drawWithShader(bufferBuilder.end());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * 透明度按渲染时间线性插值（不绑定游戏刻）：
     * 各帧起始时刻依次为 100% → 70% → 40%，第三帧期间渐隐至 0（随后不再渲染）
     */
    private static float computeAlpha(long elapsedMs) {
        int frameIndex = (int) (elapsedMs / FRAME_DURATION_MS);
        if (frameIndex < 0) frameIndex = 0;
        if (frameIndex > FRAME_COUNT - 1) frameIndex = FRAME_COUNT - 1;

        float frameProgress = (elapsedMs - frameIndex * FRAME_DURATION_MS) / (float) FRAME_DURATION_MS;
        if (frameProgress < 0.0f) frameProgress = 0.0f;
        if (frameProgress > 1.0f) frameProgress = 1.0f;

        // 最后一帧期间：从 40% 渐隐至 0
        if (frameIndex == FRAME_COUNT - 1) {
            return FRAME_START_ALPHAS[frameIndex] * (1.0f - frameProgress);
        }

        float startAlpha = FRAME_START_ALPHAS[frameIndex];
        float nextAlpha = FRAME_START_ALPHAS[frameIndex + 1];
        return startAlpha + (nextAlpha - startAlpha) * frameProgress;
    }

    private static long elapsedMs(ExplosionEffect effect, long nowNanos) {
        return (nowNanos - effect.startNanos) / 1_000_000L;
    }

    private static final class ExplosionEffect {
        private final double x;
        private final double y;
        private final double z;
        private final double radius;
        private final long startNanos;

        private ExplosionEffect(double x, double y, double z, double radius, long startNanos) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.startNanos = startNanos;
        }
    }
}
