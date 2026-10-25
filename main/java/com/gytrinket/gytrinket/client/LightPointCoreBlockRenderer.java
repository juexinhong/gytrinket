package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.blocks.LightPointCoreBlockEntity;
import com.gytrinket.gytrinket.client.model.LightPointCoreBlockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 光点核心方块渲染器（标准 Minecraft 模型渲染，不依赖 GeckoLib）
 * <p>
 * 双层材质：先画基础层（gy1），再用半透明装饰层（gy3）叠一遍。
 * 原 GeckoLib 的 {@code recharge} 动画改为在这里直接驱动 ModelPart：
 * <ul>
 *   <li>{@code bone9} 绕 Y 轴每 2 秒（40 tick）转一圈；</li>
 *   <li>{@code bone51} 有常量位置偏移 [0,-1,0]（向下一格 = 原生 y+1）。</li>
 * </ul>
 */
public class LightPointCoreBlockRenderer implements BlockEntityRenderer<LightPointCoreBlockEntity> {
    private static final ResourceLocation BASE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/block/light_point_core_block_gy1.png");
    private static final ResourceLocation DECOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/block/light_point_core_block_gy3.png");

    private final LightPointCoreBlockModel model;
    /** bone51 的建模基准 y，动画在其上加 1 个单位（向下一格）。 */
    private final float bone51BaseY;

    public LightPointCoreBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new LightPointCoreBlockModel(
                context.bakeLayer(LightPointCoreBlockModel.LAYER_LOCATION),
                RenderType::entityTranslucent);
        this.bone51BaseY = this.model.bone51.y;
    }

    @Override
    public void render(LightPointCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        animate(blockEntity.getLevel(), partialTick);

        poseStack.pushPose();
        // GeckoLib 方块渲染口径：方块内居中 + x/y 取反 + 脚底 y=1.5 格
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);

        VertexConsumer baseBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(BASE_TEXTURE));
        this.model.renderToBuffer(poseStack, baseBuffer, packedLight, packedOverlay, 0xFFFFFFFF);

        VertexConsumer decorBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(DECOR_TEXTURE));
        this.model.renderToBuffer(poseStack, decorBuffer, packedLight, packedOverlay, 0xFFFFFFFF);

        poseStack.popPose();
    }

    /** 原 recharge 动画：bone9 自转 + bone51 常量下移。 */
    private void animate(Level level, float partialTick) {
        long gameTime = level != null ? level.getGameTime() : System.currentTimeMillis() / 50L;
        float loopTicks = (float) Math.floorMod(gameTime, 40L) + partialTick;
        // geo 旋转 0 → -360°/2s，原生 yRot 数值原样照搬（弧度）
        this.model.bone9.yRot = -(loopTicks / 40.0F) * (float) (Math.PI * 2.0D);
        // 位置 [0,-1,0]：作者坐标向下 1 单位 = 原生 y +1
        this.model.bone51.y = this.bone51BaseY + 1.0F;
    }
}
