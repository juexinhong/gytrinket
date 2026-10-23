package com.gy_mod.gy_trinket.client.world;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.gy_mod.gy_trinket.core.world.IslandTeleporter;
import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 孤岛维度的天空：上半球是程序化星云，下半球是暗淡的镜面星空。
 * <p>
 * 全屏四边形绘制，片元着色器用逆投影逐像素重建视线，因此没有天球网格
 * 在广角边缘产生的额外拉扯；FOV 完全沿用原版投影，不会单独增强。
 * <p>
 * 性能：不在 {@code renderSky} 阶段先画，而是推迟到不透明地形之后
 * （AFTER_CUTOUT_BLOCKS），用深度测试只在“没有地形覆盖”的像素上
 * 执行片元着色器。看地面时绝大多数天空片元会被 depth test 直接拒绝。
 * <p>
 * 光影包兼容：Iris/Oculus/OptiFine 启用光影时，原版天空阶段会被光影包接管，
 * 因此该路径改为在 {@code renderSky} 里立即绘制，保证孤岛保留自己的天空。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public final class IslandSky {

    /** 与地形雾共享的颜色（线性空间，与 FogRenderer 使用同一组数值） */
    public static final Vec3 FOG_COLOR = new Vec3(0.048, 0.075, 0.115);

    private static final int GL_LEQUAL = 515;
    private static final Logger LOGGER = LoggerFactory.getLogger("IslandSky");

    private static VertexBuffer quad;

    /** 由 renderSky 设置，供 AFTER_CUTOUT_BLOCKS 的深度测试通道使用 */
    private static boolean skyVisible;
    private static float skyTime;
    private static boolean compatLogged;

    private IslandSky() {
    }

    /**
     * 由 {@code IslandDimensionEffects.renderSky} 调用。
     * <p>
     * 无光影时只记录状态，真正的绘制推迟到地形写满深度缓冲之后；
     * 有光影时立即绘制（此时深度缓冲还没有地形，不能做深度测试）。
     */
    public static void prepare(ClientLevel level, float partialTick, Camera camera,
                               boolean isFoggy, Matrix4f projection) {
        skyVisible = !isFoggy && camera.getFluidInCamera() == FogType.NONE;
        skyTime = (level.getGameTime() % 240000L) + partialTick;
        if (!ShaderModCompat.isShadersEnabled()) {
            return;
        }
        if (!compatLogged) {
            compatLogged = true;
            LOGGER.info("[island-sky] shader pack active, drawing sky inline from renderSky");
        }
        if (skyVisible) {
            drawDeferred(new Matrix4f(), new Matrix4f(projection), false);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        if (!skyVisible || ShaderModCompat.isShadersEnabled()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        drawDeferred(new Matrix4f(event.getPoseStack().last().pose()), event.getProjectionMatrix(), true);
    }

    private static void drawDeferred(Matrix4f modelView, Matrix4f projection, boolean depthTest) {
        ShaderInstance shader = ModShaders.getIslandSkyShader();
        if (shader == null) {
            return;
        }

        float m11 = projection.m11();
        int height = Minecraft.getInstance().getWindow().getHeight();
        float pixelAngle = 2.0F / (Math.max(1, height) * Math.max(0.0001F, m11));

        setUniform(shader, "SkyTime", skyTime);
        setUniform(shader, "PixelAngle", pixelAngle);
        setUniform(shader, "SkyDepthEnabled", 0.0F);
        setUniform(shader, "SkyInvProj", projection.invert(new Matrix4f()));
        setUniform(shader, "SkyFogColor", (float) FOG_COLOR.x,
                (float) FOG_COLOR.y, (float) FOG_COLOR.z);

        // 深度缓冲此时已写满实体/镂空地形。天空四边形落在 NDC 远平面，
        // LEQUAL 只会在“仍为清屏深度值”的像素上通过。
        RenderSystem.depthMask(false);
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        // 地形阶段可能留下区块裁剪的 scissor，会把全屏天空裁出矩形缺口
        RenderSystem.disableScissor();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        VertexBuffer buffer = quadBuffer();
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
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
        if (quad == null) {
            quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
            quad.bind();
            quad.upload(buildQuad().end());
            VertexBuffer.unbind();
        }
        return quad;
    }

    private static BufferBuilder buildQuad() {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.vertex(-1.0F, -1.0F, 0.0F).endVertex();
        buffer.vertex(1.0F, -1.0F, 0.0F).endVertex();
        buffer.vertex(1.0F, 1.0F, 0.0F).endVertex();
        buffer.vertex(-1.0F, 1.0F, 0.0F).endVertex();
        return buffer;
    }
}