package com.gytrinket.gytrinket.client.world;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.gytrinket.gytrinket.client.shader.ModShaders;
import com.gytrinket.gytrinket.core.world.IslandTeleporter;
import com.gytrinket.gytrinket.gytrinket;
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
import net.minecraftforge.client.event.RenderLevelLastEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.gytrinket.gytrinket.mixin.Matrix4fAccessor;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

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
 * 光影包兼容（对齐 1.21.1 的做法）：Iris/Oculus 会在自己的 composite 里画
 * 掉/覆盖掉天空，所以光影下改为在 RenderLevelLastEvent（composite 之后）
 * 再画一遍全屏天空，并把 Iris 的场景深度纹理传给着色器逐像素 discard，
 * 只在真正的天空像素上落笔。这样孤岛天空能盖过光影包天空，又不会挡住地形。
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
    private static boolean depthLogged;

    /** ViewRot（相机视图旋转 R）上传缓冲，复用避免每帧分配 */
    private static final FloatBuffer VIEW_ROT_SRC = ByteBuffer.allocateDirect(9 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final FloatBuffer VIEW_ROT_DST = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final Matrix4f VIEW_ROT = new Matrix4f();

    static {
        VIEW_ROT.setIdentity();
    }

    private IslandSky() {
    }

    /**
     * 由 {@code IslandDimensionEffects.renderSky} 调用。
     * <p>
     * 无光影时只记录状态，真正的绘制推迟到地形写满深度缓冲之后；
     * 有光影时也不在这里画：Iris 的 composite 会覆盖掉，统一留到
     * RenderLevelLastEvent 的补画通道。
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
            LOGGER.info("[island-sky] shader pack active, deferring sky to RenderLevelLastEvent");
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        // 光影下由 RenderLevelLastEvent 的补画通道负责（composite 之后 + 深度纹理 discard）
        if (ShaderModCompat.isShadersEnabled()) {
            return;
        }
        if (!skyVisible) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        drawDeferred(new Matrix4f(event.getPoseStack().last().pose()), event.getProjectionMatrix(), true, -1);
    }

    /**
     * 光影下的补画通道：Iris 已完成 final composite，用场景深度纹理做逐像素遮挡，
     * 在天空像素上重画孤岛天空。由 ModClient 以 HIGHEST priority 手动注册，
     * 保证在 RenderLevelLastEvent 的护盾粒子/能量波/闪电之前执行。
     */
    public static void onRenderLevelLast(RenderLevelLastEvent event) {
        if (!ShaderModCompat.isShadersEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        // 有些光影包会直接跳过 DimensionSpecialEffects.renderSky，prepare 可能没跑
        if (!skyVisible) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 position = camera.getPosition();
            boolean foggy = level.effects().isFoggyAt(Mth.floor(position.x), Mth.floor(position.z));
            skyVisible = !foggy && camera.getFluidInCamera() == FogType.NONE;
        }
        if (!skyVisible) {
            return;
        }
        int depthTexture = ShaderModCompat.getIrisDepthTexture();
        if (depthTexture <= 0) {
            if (!depthLogged) {
                depthLogged = true;
                LOGGER.warn("[island-sky] Iris depth texture unavailable; skip shader sky");
            }
            return;
        }
        skyTime = (level.getGameTime() % 240000L) + event.getPartialTick();
        drawDeferred(computeViewRotation(), event.getProjectionMatrix(), false, depthTexture);
    }

    /**
     * 视图旋转矩阵（世界 → 视图），等价于 1.21.1 的
     * {@code new Matrix4f().rotation(camera.rotation().conjugate())}。
     * 取 {@code RenderSystem.getInverseViewRotationMatrix()} 的转置，和顶点实际经历的旋转同源。
     */
    private static Matrix4f computeViewRotation() {
        Matrix3f inverseViewRotation = RenderSystem.getInverseViewRotationMatrix();
        inverseViewRotation.store(VIEW_ROT_SRC);
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float value;
                if (col == 3 || row == 3) {
                    value = (col == row) ? 1.0F : 0.0F;
                } else {
                    value = VIEW_ROT_SRC.get(row * 3 + col);
                }
                VIEW_ROT_DST.put(col * 4 + row, value);
            }
        }
        VIEW_ROT.load(VIEW_ROT_DST);
        return VIEW_ROT;
    }

    private static void drawDeferred(Matrix4f modelView, Matrix4f projection, boolean depthTest, int depthTexture) {
        ShaderInstance shader = ModShaders.getIslandSkyShader();
        if (shader == null) {
            return;
        }

        float m11 = ((Matrix4fAccessor) (Object) projection).gytrinket$m11();
        int height = Minecraft.getInstance().getWindow().getHeight();
        float pixelAngle = 2.0F / (Math.max(1, height) * Math.max(0.0001F, m11));

        setUniform(shader, "SkyTime", skyTime);
        setUniform(shader, "PixelAngle", pixelAngle);
        setUniform(shader, "SkyDepthEnabled", depthTexture > 0 ? 1.0F : 0.0F);
        setUniform(shader, "SkyInvProj", inverted(projection));
        setUniform(shader, "SkyFogColor", (float) FOG_COLOR.x,
                (float) FOG_COLOR.y, (float) FOG_COLOR.z);
        if (depthTexture > 0) {
            RenderSystem.setShaderTexture(0, depthTexture);
        }

        // 非光影：深度缓冲此时已写满实体/镂空地形，LEQUAL 只在天空像素通过。
        // 光影补画：主深度已不是场景深度，关闭硬件深度测试，由 fsh 采样 SkyDepth 做 discard。
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

    /** 1.19.2 的 Matrix4f#invert() 是"就地求逆 + 返回成功标志"，这里补一个返回新矩阵的辅助。 */
    private static Matrix4f inverted(Matrix4f source) {
        Matrix4f copy = source.copy();
        copy.invert();
        return copy;
    }

    private static void setUniform(ShaderInstance shader, String name, Matrix4f value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static VertexBuffer quadBuffer() {
        if (quad == null) {
            quad = new VertexBuffer();
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
