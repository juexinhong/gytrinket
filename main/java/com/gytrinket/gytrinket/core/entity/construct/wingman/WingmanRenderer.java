package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 僚机构造体渲染器（标准 Minecraft 实体渲染，已去除 GeckoLib 依赖）
 * <p>
 * 使用 Blockbench 导出的 Java 模型（WingmanEntityModel），
 * 保留发光层与拦截机武器渲染（含近战攻击动画）。
 */
public class WingmanRenderer extends LivingEntityRenderer<WingmanConstructEntity, WingmanEntityModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/wingman.png");
    private static final ResourceLocation GLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/entity/wingman2.png");

    private final ItemInHandRenderer itemInHandRenderer;

    // 客户端动画状态跟踪：每个实体的近战动画开始时间（毫秒）
    private static final Map<Integer, Long> meleeAnimStartTime = new ConcurrentHashMap<>();

    public WingmanRenderer(EntityRendererProvider.Context context) {
        super(context, new WingmanEntityModel(context.bakeLayer(WingmanEntityModel.LAYER_LOCATION)), 0.3F);
        this.itemInHandRenderer = context.getItemInHandRenderer();

        // 发光层
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, WingmanConstructEntity entity,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                VertexConsumer glowBuffer = bufferSource.getBuffer(RenderType.eyes(GLOW_TEXTURE));
                getParentModel().renderToBuffer(poseStack, glowBuffer, packedLight, OverlayTexture.NO_OVERLAY, -1);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(WingmanConstructEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(WingmanConstructEntity entity, PoseStack poseStack, float partialTickTime) {
        // 与原 GeckoLib 渲染器 withScale(0.8F, 0.8F) 保持一致
        poseStack.scale(1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void setupRotations(WingmanConstructEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float scale) {
        // 偏航：标准方向
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        // 俯仰：绕世界 X 轴全局旋转（在偏航之后应用，等价于绕机体横轴）。
        // 必须在实体级旋转整个模型，而不是在模型部件局部旋转，
        // 否则会与 bone 部件预置的 45° 偏航耦合，导致机身歪斜。
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTick;
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
    }

    @Override
    protected void renderNameTag(WingmanConstructEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        // 不渲染僚机上方"僚机"的名牌
    }

    @Override
    public void render(WingmanConstructEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        // 拦截机武器渲染：在实体上方渲染武器物品，跟随朝向旋转
        renderWeapon(entity, partialTicks, poseStack, bufferSource, packedLight);
    }

    private void renderWeapon(WingmanConstructEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 从SynchedEntityData读取（客户端同步）
        if (!entity.getEntityData().get(WingmanConstructEntity.DATA_INTERCEPTOR_MODE)) return;
        ItemStack weapon = entity.getEntityData().get(WingmanConstructEntity.DATA_INTERCEPTOR_WEAPON);
        if (weapon.isEmpty()) return;

        // 读取攻击模式
        String modeName = entity.getEntityData().get(WingmanConstructEntity.DATA_INTERCEPTOR_ATTACK_MODE);
        boolean isMeleeMode = InterceptorAttackMode.MELEE.getSerializedName().equals(modeName);

        // 读取近战攻击动画状态
        int meleeAnimTicks = entity.getEntityData().get(WingmanConstructEntity.DATA_MELEE_ANIM_TICKS);
        boolean isMeleeAnimating = meleeAnimTicks > 0;
        float meleeAnimProgress = 0.0f;

        // 客户端帧级动画计时：用毫秒代替服务端tick，消除卡顿
        int entityId = entity.getId();
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
        float yaw = entity.yRotO + (entity.getYRot() - entity.yRotO) * partialTick;
        float pitch = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTick;

        poseStack.pushPose();
        // 定位到实体上方0.5格
        poseStack.translate(0, 0.5, 0);
        // 缩放至原来的一半
        poseStack.scale(0.8F, 0.8F, 0.8F);
        // 应用朝向旋转（偏航+俯仰），使武器跟随拦截机朝向
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (!isMeleeAnimating) {
            // 动画期间X轴保持0度，非动画时才应用pitch
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
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
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
            // Z轴旋转（剑身横置，先应用=局部空间）
            poseStack.mulPose(Axis.ZP.rotationDegrees(zRot));
        } else if (isMeleeMode) {
            // 正常近战模式：X轴旋转90度
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
        } else {
            // 弓箭模式
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
        }

        // 渲染物品
        itemInHandRenderer.renderItem(entity, weapon, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
