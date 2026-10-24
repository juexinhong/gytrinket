package com.gytrinket.gytrinket.client.world;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.gytrinket.gytrinket.client.shader.ModShaders;
import com.gytrinket.gytrinket.core.world.IslandTeleporter;
import com.gytrinket.gytrinket.gytrinket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 孤岛维度的天空：上半球是程序化星云，下半球是暗淡的镜面星空。 * <p>
 * 全屏四边形绘制，片元着色器用逆投影逐像素重建视线，因此没有天球网格
 * 在广角边缘产生的额外拉扯；FOV 完全沿用原版投影，不会单独增强。 * <p>
 * 性能：天空不�?{@code renderSky} 阶段先画一遍，而是推迟到不透明地形
 * 之后（AFTER_CUTOUT_BLOCKS），用深度测试只在“没有地形覆盖”的像素�? * 执行片元着色器。看地面时，绝大多数天空片元会被 depth test 直接拒绝�? * 不再为被地形遮住的像素计算整片星云。看天空时仍然保持完整画质。 * <p>
 * Shader-pack compatibility: when Iris/Oculus/OptiFine is using a shader pack,
 * the normal sky pass can be skipped or overwritten by the pack.  In that case
 * the sky is drawn again at AFTER_LEVEL, depth-tested against the terrain, so
 * the island keeps its own sky instead of the pack's overworld sky.
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public final class IslandSky {

    /** 与地形雾共享的颜色（线性空间，�?FogRenderer 使用同一组数值） */
    public static final Vec3 FOG_COLOR = new Vec3(0.048, 0.075, 0.115);

    /** 天空镜面海的参考高度（维度 sea level）。 */
    private static final double SKY_SEA_LEVEL = 0.0;
    /** 有限地平线距离：相机越高，地平线越低于准心。 */
    private static final double SKY_HORIZON_DISTANCE = 1024.0;

    private static final int GL_LEQUAL = 515;
    private static final Logger LOGGER = LoggerFactory.getLogger("IslandSky");

    /**
     * When a shader pack is active, force the island sky over the pack's sky.
     * Can be disabled for troubleshooting with
     * {@code -Dgytrinket.forceSkyWithShaders=false}.
     */
    private static final boolean FORCE_SKY_WITH_SHADERS =
            !"false".equalsIgnoreCase(System.getProperty("gytrinket.forceSkyWithShaders"));

    private static VertexBuffer quad;

    /** Set by renderSky; consumed once by the after-cutout depth-tested pass. */
    private static boolean skyVisible;
    private static float skyTime;
    private static boolean shaderSkyDrawn;
    private static boolean shaderCompatLogged;
    private static Matrix4f shaderProjection;

    private IslandSky() {
    }

    /**
     * Called from {@code DimensionSpecialEffects.renderSky}.  We deliberately
     * do not draw here: the sky is deferred until opaque/cutout terrain has
     * filled the depth buffer.  Returning true still suppresses the vanilla sky.
     */
    public static void prepare(ClientLevel level, float partialTick, Camera camera, boolean isFoggy) {
        skyVisible = !isFoggy && camera.getFluidInCamera() == FogType.NONE;
        skyTime = (level.getGameTime() % 240000L) + partialTick;
    }

    /**
     * Shader-pack path, called from {@code DimensionSpecialEffects.renderSky}.
     * Iris/Oculus composites its own gbuffer into the main framebuffer later;
     * draw directly into that gbuffer so the island sky survives the final
     * composite.  Without Iris this returns without drawing and the normal
     * AFTER_LEVEL fallback handles it.
     */
    public static void drawInShaderPipeline(ClientLevel level, float partialTick, Camera camera,
                                            Matrix4f modelView, Matrix4f projection, boolean isFoggy) {
        skyVisible = !isFoggy && camera.getFluidInCamera() == FogType.NONE;
        skyTime = (level.getGameTime() % 240000L) + partialTick;
        shaderSkyDrawn = false;

        if (!ShaderModCompat.bindIrisDefaultFramebuffer()) {
            if (!shaderCompatLogged) {
                shaderCompatLogged = true;
                LOGGER.info("[island-sky] shader pack active, Iris gbuffer unavailable; using AFTER_LEVEL fallback");
            }
            return;
        }
        if (!shaderCompatLogged) {
            shaderCompatLogged = true;
            LOGGER.info("[island-sky] shader pack active, island sky written into Iris gbuffer");
        }

        // The gbuffer depth is already cleared; this is the sky layer.
        drawDeferred(projection, false);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        shaderSkyDrawn = true;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();

        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            // With a shader pack the final sky is drawn from GameRendererMixin
            // after Iris' composite; skip the normal deferred pass.
            if (forceSkyWithShaders()) {
                return;
            }
            if (!skyVisible) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
                return;
            }
            drawDeferred(event.getProjectionMatrix());
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL && forceSkyWithShaders()) {
            // The tail pass runs after Iris' final composite, where the world
            // projection is gone.  Cache the real projection here; the camera
            // rotation is read from the main camera at draw time.
            shaderProjection = new Matrix4f(event.getProjectionMatrix());
        }
    }

    /**
     * Some shader packs skip {@code DimensionSpecialEffects.renderSky}, so the
     * normal prepare call never runs.  Rebuild the per-frame state from the
     * current client camera when forcing the sky at AFTER_LEVEL.
     */
    private static boolean forceSkyWithShaders() {
        return FORCE_SKY_WITH_SHADERS
                && (ShaderModCompat.isShadersEnabled()
                    || Boolean.getBoolean("gytrinket.testForceSkyWithShaders"));
    }

    /**
     * 这一帧孤岛天空会不会在 {@code renderLevel} 末尾补画一次。
     * <p>
     * 会补画就说明它**盖在一切之上**（包括 {@code AFTER_LEVEL} 画的护盾粒子），
     * 所以粒子要等它画完再补一遍。只有开光影、且当前在孤岛维度时才成立。
     */
    public static boolean willPaintAfterLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return false;
        }
        return forceSkyWithShaders() && skyVisible;
    }

    private static void ensurePrepared() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            skyVisible = false;
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 position = camera.getPosition();
        boolean foggy = level.effects().isFoggyAt(Mth.floor(position.x), Mth.floor(position.z));
        skyVisible = !foggy && camera.getFluidInCamera() == FogType.NONE;
        skyTime = level.getGameTime() % 240000L;
    }

    /**
     * Last-chance pass from GameRenderer.renderLevel TAIL.  Iris runs its final
     * composite there, so this is the only point where the custom sky is
     * guaranteed to be on top of the pack's own overworld sky.
     */
    public static void onGameRendererTail() {        if (!forceSkyWithShaders()) {
            return;
        }
        ensurePrepared();
        if (!skyVisible) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        int depthTexture = ShaderModCompat.getIrisNoHandDepthTexture();
        if (depthTexture <= 0) {
            // OptiFine has no Iris gbuffer; main depth is still scene depth.
            if (!ShaderModCompat.isIrisLoaded()) {
                drawDeferred(new Matrix4f(RenderSystem.getProjectionMatrix()), true);
            }
            return;
        }
        // Main-framebuffer depth is no longer the scene depth after Iris' final
        // pass.  Disable hardware depth test and discard using Iris' depth tex.
        Matrix4f projection = shaderProjection != null
                ? new Matrix4f(shaderProjection)
                : new Matrix4f(RenderSystem.getProjectionMatrix());
        drawDeferred(projection, false, depthTexture);
    }

    private static void drawDeferred(Matrix4f projection) {
        drawDeferred(projection, true, -1);
    }

    private static void drawDeferred(Matrix4f projection, boolean depthTest) {
        drawDeferred(projection, depthTest, -1);
    }

    private static void drawDeferred(Matrix4f projection, boolean depthTest, int depthTexture) {
        ShaderInstance shader = ModShaders.getIslandSkyShader();
        if (shader == null) {
            return;
        }

        float m11 = projection.m11();
        int width = Minecraft.getInstance().getWindow().getWidth();
        int height = Minecraft.getInstance().getWindow().getHeight();
        float pixelAngle = 2.0F / (Math.max(1, height) * Math.max(0.0001F, m11));
        float aspect = (float) Math.max(1, width) / Math.max(1, height);
        float tanHalfFov = 1.0F / Math.max(0.0001F, m11);

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var forward = camera.getLookVector();
        var up = camera.getUpVector();
        var left = camera.getLeftVector();

        setUniform(shader, "SkyTime", skyTime);
        setUniform(shader, "PixelAngle", pixelAngle);
        setUniform(shader, "SkyDepthEnabled", depthTexture > 0 ? 1.0F : 0.0F);
        setUniform(shader, "SkyTanHalfFov", tanHalfFov);
        setUniform(shader, "SkyAspect", aspect);
        setUniform(shader, "SkyCameraRight", -left.x, -left.y, -left.z);
        setUniform(shader, "SkyCameraUp", up.x, up.y, up.z);
        setUniform(shader, "SkyCameraForward", forward.x, forward.y, forward.z);
        // 把地平线锚定在世界空间的海平面，而不是相机眼高。
        // 有限距离让高空视角的地平线明显低于准心，而低处接近准心。
        double horizonElev = -Math.atan2(
                camera.getPosition().y - SKY_SEA_LEVEL, SKY_HORIZON_DISTANCE);
        setUniform(shader, "SkyHorizonElev", (float) horizonElev);
        setUniform(shader, "SkyFogColor", (float) FOG_COLOR.x,
                (float) FOG_COLOR.y, (float) FOG_COLOR.z);
        if (depthTexture > 0) {
            RenderSystem.setShaderTexture(0, depthTexture);
        }

        // Depth buffer now contains all solid/cutout terrain.  The sky quad is
        // emitted at NDC z = 1, so LEQUAL passes only where the depth buffer
        // is still at the far-plane clear value.  The early gbuffer pass runs
        // before terrain and therefore disables the test.
        RenderSystem.depthMask(false);
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        // The terrain pass may leave a chunk/debug scissor enabled; it would
        // clip the fullscreen sky into rectangular holes.
        RenderSystem.disableScissor();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // The sky ray is built from the camera basis vectors in the fragment
        // shader, so the matrices passed here are irrelevant (the vertex
        // shader writes gl_Position directly).  Pass identity instead of a
        // level model-view matrix to keep any matrix convention out of the
        // sky entirely.
        VertexBuffer buffer = quadBuffer();
        buffer.bind();
        buffer.drawWithShader(new Matrix4f(), projection, shader);
        VertexBuffer.unbind();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float x, float y, float z) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(x, y, z);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, Matrix4f value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static VertexBuffer quadBuffer() {
        if (quad == null || quad.isInvalid()) {
            if (quad != null) {
                quad.close();
            }
            quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
            quad.bind();
            quad.upload(buildQuad());
            VertexBuffer.unbind();
        }
        return quad;
    }

    private static MeshData buildQuad() {
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F);
        return buffer.buildOrThrow();
    }
}
