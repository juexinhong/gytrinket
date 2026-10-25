package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.client.model.LightPointCoreBlockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 光点核心物品渲染器（仅客户端，标准模型渲染，不依赖 GeckoLib）
 * <p>
 * 通过 IClientItemExtensions 注册给光点核心物品，物品形态复用方块实体的
 * 3D 模型与贴图，并保留原来的 GUI 倾斜 / 手持缩放以及自转动画。
 * <p>
 * 坐标空间：物品渲染空间是「y 向下、z 反向」的（原版 BEWLR 都先做
 * {@code translate(.5,.5,.5); scale(1,-1,-1)} 再画方块模型），
 * 再经 {@code scale(1,1,-1); scale(-1,-1,1)} 换到原生实体模型空间。
 */
@OnlyIn(Dist.CLIENT)
public class LightPointCoreItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation BASE_TEXTURE =
            new ResourceLocation("gytrinket", "textures/block/light_point_core_block_gy1.png");

    /** 物品栏(GUI)里的倾斜角与缩放（沿用原渲染器观感）。 */
    private static final float GUI_TILT_DEG = 30.0F;
    private static final float GUI_SCALE = 0.63F;
    /** 手持时的缩放（模型居中于手心，需要比原渲染器更小）。 */
    private static final float HAND_SCALE = 0.3F;

    private static LightPointCoreItemRenderer instance;

    private LightPointCoreBlockModel model;
    private float bone51BaseY;

    private LightPointCoreItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** 获取共享渲染器实例（首次调用发生在物品实际渲染时，Minecraft 已就绪）。 */
    public static LightPointCoreItemRenderer getRenderer() {
        if (instance == null) {
            instance = new LightPointCoreItemRenderer();
        }
        return instance;
    }

    private LightPointCoreBlockModel model() {
        if (this.model == null) {
            this.model = new LightPointCoreBlockModel(
                    LightPointCoreBlockModel.createBodyLayer().bakeRoot(),
                    RenderType::entityTranslucent);
            this.bone51BaseY = this.model.bone51.y;
        }
        return this.model;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        LightPointCoreBlockModel blockModel = model();

        // 与原 GeckoLib 一致的自转动画（bone9 每 2 秒一圈）
        float loopTicks = (System.currentTimeMillis() % 2000L) / 50.0F;
        blockModel.bone9.yRot = -(loopTicks / 40.0F) * (float) (Math.PI * 2.0D);
        blockModel.bone51.y = this.bone51BaseY + 1.0F;

        poseStack.pushPose();
        // 把方块的角挪到物品原点（方块中心对齐物品原点）
        poseStack.translate(0.5F, 0.5F, 0.5F);

        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.mulPose(Axis.XP.rotationDegrees(GUI_TILT_DEG));
            poseStack.mulPose(Axis.YP.rotationDegrees(GUI_TILT_DEG));
            poseStack.scale(GUI_SCALE, GUI_SCALE, GUI_SCALE);
        } else if (displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            // 以方块中心为轴缩小，并上移一点让整块模型露在手部视野里
            poseStack.scale(HAND_SCALE, HAND_SCALE, HAND_SCALE);
            poseStack.translate(0.0F, 0.55F, 0.0F);
        }

        // 物品空间（y 向上、z 反向）→ 方块空间（y 向上）
        poseStack.scale(-1.0F, 1.0F, -1.0F);
        // 方块空间 → 原生实体模型空间（x/y 取反、脚底 y=24）
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        // 模型竖直中心对齐物品原点（0..17 单位 → -8.5..+8.5）
        poseStack.translate(0.0F, -1.0F, 0.0F);

        // 手部 / 掉落物等上下文的批次不一定会被及时 flush，这里显式刷一次
        RenderType renderType = RenderType.entityTranslucent(BASE_TEXTURE);
        VertexConsumer buffer = bufferSource.getBuffer(renderType);
        blockModel.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        if (bufferSource instanceof MultiBufferSource.BufferSource batchSource) {
            batchSource.endBatch(renderType);
        }

        poseStack.popPose();
    }
}
