package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.mojang.math.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

/**
 * 无人机构造体渲染器（标准 Minecraft 实体渲染，不依赖 GeckoLib）
 * <p>
 * 三种变体各有一套原生模型与贴图，渲染时按实体效果切换 {@link #model}：
 * 普通无人机（drone）/ 突击无人机（assaultdrone）/ 防御无人机（defense_drone）。
 * 发光层复用同一模型，换发光贴图再画一遍。
 */
public class DroneRenderer extends LivingEntityRenderer<DroneConstructEntity, EntityModel<DroneConstructEntity>> {
    private static final ResourceLocation DRONE_TEXTURE =
            new ResourceLocation("gytrinket", "textures/entity/drone1.png");
    private static final ResourceLocation DRONE_GLOW =
            new ResourceLocation("gytrinket", "textures/entity/drone2.png");
    private static final ResourceLocation ASSAULT_TEXTURE =
            new ResourceLocation("gytrinket", "textures/entity/assaultdrone1.png");
    private static final ResourceLocation ASSAULT_GLOW =
            new ResourceLocation("gytrinket", "textures/entity/assaultdrone2.png");
    private static final ResourceLocation DEFENSE_TEXTURE =
            new ResourceLocation("gytrinket", "textures/entity/defense_drone1.png");
    private static final ResourceLocation DEFENSE_GLOW =
            new ResourceLocation("gytrinket", "textures/entity/defense_drone2.png");

    private final EntityModel<DroneConstructEntity> normalModel;
    private final EntityModel<DroneConstructEntity> assaultModel;
    private final EntityModel<DroneConstructEntity> defenseModel;

    public DroneRenderer(EntityRendererProvider.Context context) {
        // 原 GeckoLib 渲染器没有阴影（EntityRenderer.shadowRadius 默认 0）
        super(context, new DroneEntityModel(context.bakeLayer(DroneEntityModel.LAYER_LOCATION)), 0.0F);
        this.normalModel = this.model;
        this.assaultModel = new AssaultDroneEntityModel(
                context.bakeLayer(AssaultDroneEntityModel.LAYER_LOCATION));
        this.defenseModel = new DefenseDroneEntityModel(
                context.bakeLayer(DefenseDroneEntityModel.LAYER_LOCATION));

        // 发光层：同一模型 + 发光贴图
        this.addLayer(new RenderLayer<DroneConstructEntity, EntityModel<DroneConstructEntity>>(this) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                               DroneConstructEntity entity, float limbSwing, float limbSwingAmount,
                               float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                RenderType glowType;
                if (entity.isAssaultDrone()) {
                    glowType = RenderType.eyes(ASSAULT_GLOW);
                } else if (entity.isDefenseDrone()) {
                    glowType = RenderType.entityTranslucent(DEFENSE_GLOW);
                } else {
                    glowType = RenderType.eyes(DRONE_GLOW);
                }
                VertexConsumer glowBuffer = bufferSource.getBuffer(glowType);
                getParentModel().renderToBuffer(poseStack, glowBuffer, packedLight,
                        OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
    }

    @Override
    public void render(DroneConstructEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.model = pickModel(entity);
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private EntityModel<DroneConstructEntity> pickModel(DroneConstructEntity entity) {
        if (entity.isAssaultDrone()) {
            return this.assaultModel;
        }
        if (entity.isDefenseDrone()) {
            return this.defenseModel;
        }
        return this.normalModel;
    }

    @Override
    public ResourceLocation getTextureLocation(DroneConstructEntity entity) {
        if (entity.isAssaultDrone()) {
            return ASSAULT_TEXTURE;
        }
        if (entity.isDefenseDrone()) {
            return DEFENSE_TEXTURE;
        }
        return DRONE_TEXTURE;
    }

    @Override
    protected void setupRotations(DroneConstructEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick) {
        // 位置偏移与原渲染器一致：防御无人机略低，其余抬高 0.3
        if (entity.isDefenseDrone()) {
            poseStack.translate(0.0D, -0.2D, 0.0D);
        } else {
            poseStack.translate(0.0D, 0.3D, 0.0D);
        }
        // 偏航：标准方向
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - rotationYaw));
        // 俯仰：绕机体横轴（在偏航之后应用，等价于绕世界 X 轴）
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTick;
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-pitch));
    }

    @Override
    protected void scale(DroneConstructEntity entity, PoseStack poseStack, float partialTickTime) {
        // 原 GeckoLib 渲染器 withScale(0.8)；防御无人机再额外缩 20%。
        // 1.20.1 的 LivingEntityRenderer 不会自动应用实体大小倍率，这里手动乘上。
        float scale = 0.8F * (float) entity.getSizeMultiplier();
        if (entity.isDefenseDrone()) {
            scale *= 0.8F;
        }
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected void renderNameTag(DroneConstructEntity entity, Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight) {
        // 不渲染无人机上方的名牌
    }

    @Override
    protected int getBlockLightLevel(DroneConstructEntity entity, BlockPos pos) {
        // 机体自发光：只看方块光照，避免黑夜里整机消失
        return entity.getLevel().getBrightness(LightLayer.BLOCK, pos);
    }
}
