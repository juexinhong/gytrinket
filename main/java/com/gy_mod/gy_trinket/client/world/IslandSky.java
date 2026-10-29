package com.gy_mod.gy_trinket.client.world;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.client.shader.ModShaders;
import com.gy_mod.gy_trinket.core.world.IslandTeleporter;
import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.blaze3d.platform.GlStateManager;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
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
 * 光影包兼容（对齐 1.19.2 的 6c1950a / 1.21.1 的做法）：Iris/Oculus 会在自己的
 * composite 里画掉/覆盖掉天空，所以光影下改为在 {@code AFTER_LEVEL}（composite 之后，
 * 1.20.1 没有 RenderLevelLastEvent）再画一遍全屏天空，并把 Iris 的场景深度纹理传给
 * 着色器逐像素 discard，只在真正的天空像素上落笔。这样孤岛天空能盖过光影包天空，
 * 又不会挡住地形。该通道由 {@code ModClient} 以 HIGHEST 优先级手动注册，保证先于
 * 同一阶段补画的护盾粒子/能量波/闪电执行。
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
    private static int loggedRtW = -1;
    private static int loggedRtH = -1;
    private static boolean pipelineReloaded;

    /** ViewRot（相机视图旋转 R）上传缓冲，复用避免每帧分配 */
    private static final Matrix4f VIEW_ROT = new Matrix4f();

    private IslandSky() {
    }

    /**
     * 由 {@code IslandDimensionEffects.renderSky} 调用。
     * <p>
     * 无光影时只记录状态，真正的绘制推迟到地形写满深度缓冲之后；
     * 有光影时也不在这里画：Iris 的 composite 会覆盖掉，统一留到
     * AFTER_LEVEL 的补画通道（见 {@link #paintWithShaders}）。
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
            LOGGER.info("[island-sky] shader pack active, deferring sky to AFTER_LEVEL");
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        // 光影下由 AFTER_LEVEL 的补画通道负责（composite 之后 + 深度纹理 discard）
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
     * 光影下的补画通道：AFTER_LEVEL 时 Iris 已完成 final composite，用场景深度纹理
     * 做逐像素遮挡，在天空像素上重画孤岛天空。
     * <p>
     * 由 {@code ModClient} 以 HIGHEST priority 手动注册，保证在 AFTER_LEVEL 的
     * 护盾粒子/能量波/闪电之前执行（否则天空会盖住天空像素里的粒子）。
     */
    public static void paintWithShaders(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
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
        // Oculus/Iris 在启动阶段窗口尺寸变化后，pipeline 的 render target 可能仍是旧尺寸：
        // 彩色画面由 composite 拉伸填满，但深度纹理右/上侧没有写入（=1.0），
        // 全屏天空会在那里盖住方块（表现为右上角漏出星空/星云）。
        // 每次启动重建一次 pipeline，让 render target 跟随当前窗口尺寸。
        // 兜底开关：-Dgytrinket.disablePipelineReset=true。
        if (!pipelineReloaded && !Boolean.getBoolean("gytrinket.disablePipelineReset")) {
            pipelineReloaded = true;
            ShaderModCompat.reloadIrisPipeline();
            return; // 下一帧用重建后的 pipeline 再画
        }
        int depthTexture = ShaderModCompat.getIrisDepthTexture();
        if (depthTexture <= 0) {
            if (!depthLogged) {
                depthLogged = true;
                LOGGER.warn("[island-sky] Iris depth texture unavailable; skip shader sky");
            }
            return;
        }
        int mainW = Minecraft.getInstance().getMainRenderTarget().width;
        int mainH = Minecraft.getInstance().getMainRenderTarget().height;
        if (mainW != loggedRtW || mainH != loggedRtH) {
            loggedRtW = mainW;
            loggedRtH = mainH;
            logDepthTextureSize(depthTexture);
        }
        skyTime = (level.getGameTime() % 240000L) + event.getPartialTick();
        // 双保险：Iris 的 SkyDepth 纹理在部分窗口尺寸下右上角会残留 1.0（未写入），
        // 此时主深度缓冲仍是正确的场景深度；硬件深度测试可以挡住这些像素，避免天空盖住方块。
        drawDeferred(computeViewRotation(), event.getProjectionMatrix(), true, depthTexture);
    }

    /** framebuffer / 深度纹理 / viewport 尺寸变化时打印一次，排查“右上角漏天空”类 UV 不一致。 */
    private static void logDepthTextureSize(int depthTexture) {
        Minecraft mc = Minecraft.getInstance();
        int rtW = mc.getMainRenderTarget().width;
        int rtH = mc.getMainRenderTarget().height;
        int texW = -1;
        int texH = -1;
        try {
            int previous = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._bindTexture(depthTexture);
            texW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            texH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            GlStateManager._bindTexture(previous);
        } catch (Throwable ignored) {
            // 查询失败不影响绘制
        }
        int[] viewport = new int[4];
        try {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        } catch (Throwable ignored) {
            viewport = new int[]{0, 0, rtW, rtH};
        }
        int[] gbuffer = ShaderModCompat.getIrisRenderTargetSize();
        int[] cached = ShaderModCompat.getIrisCachedSize();
        LOGGER.info("[island-sky] mainRT {}x{}, depthTex {}x{}, gbuffer {}x{}, irisCached {}x{}, window {}x{}, viewport {}x{}@{},{}",
                rtW, rtH, texW, texH,
                gbuffer != null ? gbuffer[0] : -1, gbuffer != null ? gbuffer[1] : -1,
                cached != null ? cached[0] : -1, cached != null ? cached[1] : -1,
                mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                viewport[2], viewport[3], viewport[0], viewport[1]);
    }

    /**
     * 视图旋转矩阵（世界 → 视图），等价于 1.21.1 的
     * {@code new Matrix4f().rotation(camera.rotation().conjugate())}。
     * <p>
     * 取 {@code RenderSystem.getInverseViewRotationMatrix()} 的转置：GameRenderer 每帧把
     * 「世界渲染视图 pose 的 3x3 逆」写进去，转置回来就是顶点实际用的视图旋转。
     * 事件 PoseStack 在 1.20.1 的 AFTER_LEVEL 是投影栈，不能当 modelview 用。
     */
    private static Matrix4f computeViewRotation() {
        Matrix3f inv = RenderSystem.getInverseViewRotationMatrix();
        // JOML 的 set(...) 参数是行主序：VIEW_ROT(r,c) = inv(c,r)
        VIEW_ROT.set(
                inv.m00(), inv.m10(), inv.m20(), 0.0F,
                inv.m01(), inv.m11(), inv.m21(), 0.0F,
                inv.m02(), inv.m12(), inv.m22(), 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        return VIEW_ROT;
    }

    private static void drawDeferred(Matrix4f modelView, Matrix4f projection, boolean depthTest, int depthTexture) {
        ShaderInstance shader = ModShaders.getIslandSkyShader();
        if (shader == null) {
            return;
        }

        float m11 = projection.m11();
        int screenW = Minecraft.getInstance().getMainRenderTarget().width;
        int screenH = Minecraft.getInstance().getMainRenderTarget().height;
        float pixelAngle = 2.0F / (Math.max(1, screenH) * Math.max(0.0001F, m11));

        setUniform(shader, "SkyTime", skyTime);
        setUniform(shader, "PixelAngle", pixelAngle);
        setUniform(shader, "SkyDepthEnabled", depthTexture > 0 ? 1.0F : 0.0F);
        // 用当前 framebuffer 尺寸归一化屏幕坐标（深度纹理尺寸可能因 Iris 分配/缩放而不同）
        setUniform(shader, "SkyScreenW", screenW);
        setUniform(shader, "SkyScreenH", screenH);
        // Iris 的 gbuffer 可能比主 framebuffer 小（启动后被启动器改窗口尺寸时）；
        // 此时深度纹理只有左/下部分被写入，压缩 UV 到写入区域，避免右上角漏出天空。
        float depthScaleX = 1.0F;
        float depthScaleY = 1.0F;
        int[] targetSize = ShaderModCompat.getIrisRenderTargetSize();
        if (targetSize != null && targetSize[0] > 0 && targetSize[1] > 0 && screenW > 0 && screenH > 0) {
            depthScaleX = Math.min(1.0F, targetSize[0] / (float) screenW);
            depthScaleY = Math.min(1.0F, targetSize[1] / (float) screenH);
        }
        setUniform(shader, "SkyDepthScaleX", depthScaleX);
        setUniform(shader, "SkyDepthScaleY", depthScaleY);
        setUniform(shader, "SkyInvProj", projection.invert(new Matrix4f()));
        setUniform(shader, "SkyFogColor", (float) FOG_COLOR.x,
                (float) FOG_COLOR.y, (float) FOG_COLOR.z);
        // 显式绑定 sampler：1.20.1 的 VertexBuffer 只自动绑定 Sampler0..11，
        // 自定义名字（SkyDepth）不 setSampler 的话会采样到未定义纹理单元。
        shader.setSampler("SkyDepth", depthTexture > 0 ? depthTexture : 0);
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

        // 光影 composite 之后 viewport 不一定是全屏；天空必须覆盖整个 framebuffer，
        // 否则残留的 viewport 会在右上角留下一块没被天空覆盖的旧画面。
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        boolean viewportFixed = previousViewport[0] != 0 || previousViewport[1] != 0
                || previousViewport[2] != screenW || previousViewport[3] != screenH;
        if (viewportFixed) {
            RenderSystem.viewport(0, 0, screenW, screenH);
        }

        VertexBuffer buffer = quadBuffer();
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();

        if (viewportFixed) {
            RenderSystem.viewport(previousViewport[0], previousViewport[1],
                    previousViewport[2], previousViewport[3]);
        }

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
