package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 僚机构造体渲染器
 */
public class WingmanRenderer extends GeoEntityRenderer<WingmanConstructEntity> {
    private static final ResourceLocation GLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/drone2.png");
    private final ItemInHandRenderer itemInHandRenderer;

    // 客户端动画状态跟踪：每个实体的近战动画开始时间（毫秒）
    private static final Map<Integer, Long> meleeAnimStartTime = new ConcurrentHashMap<>();

    public WingmanRenderer(EntityRendererProvider.Context context) {
        super(context, new WingmanModel());
        withScale(0.8F, 0.8F);
        this.itemInHandRenderer = context.getItemInHandRenderer();

        // 发光层
        addRenderLayer(new GeoRenderLayer<WingmanConstructEntity>(this) {
            @Override
            public void render(PoseStack poseStack, WingmanConstructEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                RenderType glowRenderType = RenderType.eyes(GLOW_TEXTURE);
                VertexConsumer glowBuffer = bufferSource.getBuffer(glowRenderType);
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowRenderType,
                    glowBuffer, partialTick, packedLight, packedOverlay,
                    1.0F, 1.0F, 1.0F, 1.0F);
            }
        });

        // 拦截机武器渲染层：在实体下方1格渲染武器物品，跟随朝向旋转
        addRenderLayer(new GeoRenderLayer<WingmanConstructEntity>(this) {
            @Override
            public void render(PoseStack poseStack, WingmanConstructEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                // 从SynchedEntityData读取（客户端同步）
                if (!animatable.getEntityData().get(WingmanConstructEntity.DATA_INTERCEPTOR_MODE)) return;
                ItemStack weapon = animatable.getEntityData().get(WingmanConstructEntity.DATA_INTERCEPTOR_WEAPON);
                if (weapon.isEmpty()) return;

                // 读取攻击模式
                String modeName = animatable.getEntityData().get(WingmanConstructEntity.DATA_INTERCEPTOR_ATTACK_MODE);
                boolean isMeleeMode = InterceptorAttackMode.MELEE.getSerializedName().equals(modeName);

                // 读取近战攻击动画状态
                int meleeAnimTicks = animatable.getEntityData().get(WingmanConstructEntity.DATA_MELEE_ANIM_TICKS);
                boolean isMeleeAnimating = meleeAnimTicks > 0;
                float meleeAnimProgress = 0.0f;

                // 客户端帧级动画计时：用毫秒代替服务端tick，消除卡顿
                int entityId = animatable.getId();
                if (isMeleeAnimating) {
                    long now = System.currentTimeMillis();
                    if (!meleeAnimStartTime.containsKey(entityId)) {
                        // 动画刚开始，记录起始时间
                        meleeAnimStartTime.put(entityId, now);
                    }
                    long elapsed = now - meleeAnimStartTime.get(entityId);
                    float durationMs = WingmanConstructEntity.MELEE_ANIM_DURATION * 50.0f; // 1tick=50ms
                    meleeAnimProgress = Mth.clamp(elapsed / durationMs, 0.0f, 1.0f);
                } else {
                    // 动画结束，清理计时
                    meleeAnimStartTime.remove(entityId);
                }

                // 计算插值朝向
                float yaw = animatable.yRotO + (animatable.getYRot() - animatable.yRotO) * partialTick;
                float pitch = animatable.xRotO + (animatable.getXRot() - animatable.xRotO) * partialTick;

                poseStack.pushPose();
                // 定位到实体上方0.5格
                poseStack.translate(0, 0.5, 0);
                // 缩放至原来的一半
                poseStack.scale(0.8F, 0.8F, 0.8F);
                // 应用朝向旋转（偏航+俯仰），使武器跟随拦截机朝向
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
                if (!isMeleeAnimating) {
                    // 动画期间X轴保持0度，非动画时才应用pitch
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
                }

                // 近战攻击动画效果（阶段1/3瞬间跳变，阶段2横扫平滑插值）
                if (isMeleeAnimating) {
                    float zRot, yRot;

                    if (meleeAnimProgress < 0.25f) {
                        // 阶段1：剑瞬间横置（无插值），Y=0
                        zRot = 90.0f;
                        yRot = 0;
                    } else if (meleeAnimProgress < 0.75f) {
                        // 阶段2：Y轴横扫（0°→180°），Z保持90°（仅此阶段插值）
                        float p = (meleeAnimProgress - 0.25f) / 0.5f;
                        zRot = 90.0f;
                        yRot = p * 180.0f;
                    } else {
                        // 阶段3：瞬间复位（无插值）
                        zRot = 0;
                        yRot = 0;
                    }

                    // Y轴旋转（横扫弧线，后应用=世界空间）
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yRot));
                    // Z轴旋转（剑身横置，先应用=局部空间）
                    poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(zRot));
                } else if (isMeleeMode) {
                    // 正常近战模式：X轴旋转45度 + Y轴旋转90度
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(0.0F));
                } else {
                    // 弓箭模式
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
                    poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(0.0F));
                }

                // 渲染物品
                itemInHandRenderer.renderItem(animatable, weapon, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource, packedLight);
                poseStack.popPose();
            }
        });
    }

    @Override
    public void render(WingmanConstructEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float renderYaw = entity.yRotO + (entity.getYRot() - entity.yRotO) * partialTicks;

        poseStack.pushPose();
        poseStack.translate(0, 0.0D, 0);

        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTicks;

        poseStack.pushPose();
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(-renderYaw)));
        poseStack.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(pitch)));
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(renderYaw)));

        BlockPos pos = entity.blockPosition();
        int blockLight = entity.level().getBrightness(LightLayer.BLOCK, pos);
        int skyLight = entity.level().getBrightness(LightLayer.SKY, pos);
        int correctPackedLight = LightTexture.pack(blockLight, skyLight);

        super.render(entity, renderYaw, partialTicks, poseStack, bufferSource, correctPackedLight);

        poseStack.popPose();
        poseStack.popPose();
    }

    @Override
    protected int getBlockLightLevel(WingmanConstructEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.BLOCK, pos);
    }
}
