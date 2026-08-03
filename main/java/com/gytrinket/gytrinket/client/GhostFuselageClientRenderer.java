package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.client.compat.ShaderModCompat;
import com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageClientData;
import com.gytrinket.gytrinket.gytrinket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * 幽灵机身客户端渲染器
 * <p>
 * 根据隐身进度调整玩家模型的透明度：
 * <ul>
 *   <li>进度0%时完全可见（alpha=1.0）</li>
 *   <li>进度80%（完全隐身）时alpha=0.3（最低值）</li>
 *   <li>进度80%~100%保持alpha=0.3</li>
 * </ul>
 * <p>
 * 服务端在完全隐身时设置原版invisible标签（阻止怪物目标选取），
 * 这会导致客户端模型完全不可见。因此在Pre中临时恢复可见。
 * <p>
 * 渲染策略：
 * <ul>
 *   <li>非光影模式：使用 setShaderColor 设置全局 alpha（vanilla endBatch 立即绘制）</li>
 *   <li>光影模式（Iris）：取消事件，手动渲染，通过 GhostAlphaBufferSource 包装
 *       MultiBufferSource，将 RenderType 替换为 entityTranslucent（启用半透明混合），
 *       并通过 GhostAlphaVertexConsumer 修改顶点颜色 alpha（绕过 Iris 的 endBatch 延迟）</li>
 * </ul>
 * <p>
 * Iris 兼容性根因：Iris 的 FullyBufferedMultiBufferSource 延迟 endBatch 到 renderLevel
 * 的 translucent 阶段，此时 setShaderColor 已被重置为 1.0，alpha 丢失。
 * 顶点颜色 alpha 不受 endBatch 时序影响，在顶点写入时即固定。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class GhostFuselageClientRenderer {

    /** 完全隐身进度阈值 */
    private static final float STEALTH_CAP = 0.8f;

    /** 最低透明度（完全隐身时） */
    private static final float MIN_ALPHA = 0.3f;

    /** 防止手动渲染时递归触发 Pre 事件 */
    private static boolean isManualRendering = false;

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        // 手动渲染时跳过，防止递归
        if (isManualRendering) return;

        float progress = GhostFuselageClientData.getStealthProgress(event.getEntity().getId());
        if (progress <= 0.001f) return;

        // 计算目标 alpha
        float alpha = 1.0f - progress * (1.0f - MIN_ALPHA) / STEALTH_CAP;
        alpha = Math.max(MIN_ALPHA, alpha);

        // 抵消服务端 invisible 同步导致的模型完全隐藏
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        boolean wasInvisible = player.isInvisible();
        if (wasInvisible) {
            player.setInvisible(false);
        }

        boolean shaderActive = ShaderModCompat.isShaderPackInUse();

        if (shaderActive) {
            // 光影模式：取消事件，手动渲染，使用包装的 BufferSource
            // 注意：取消事件后 Post 不会触发，invisible 恢复需在此完成
            event.setCanceled(true);

            PlayerRenderer renderer = event.getRenderer();
            ResourceLocation texture = renderer.getTextureLocation(player);
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource wrappedSource = new GhostAlphaBufferSource(
                    event.getMultiBufferSource(), alpha, texture);

            isManualRendering = true;
            try {
                renderer.render(player, player.getYRot(), event.getPartialTick(),
                        poseStack, wrappedSource, event.getPackedLight());
            } finally {
                isManualRendering = false;
            }

            // 光影模式：在此恢复 invisible（Post 不会触发）
            if (wasInvisible && progress >= STEALTH_CAP) {
                player.setInvisible(true);
            }
        } else {
            // 非光影模式：使用 setShaderColor（vanilla endBatch 立即绘制，alpha 生效）
            // invisible 恢复在 Post 中完成
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        float progress = GhostFuselageClientData.getStealthProgress(event.getEntity().getId());
        if (progress <= 0.001f) return;

        // 非光影模式：endBatch + 重置 shaderColor
        if (!ShaderModCompat.isShaderPackInUse()) {
            if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        // 光影模式：无需 endBatch（顶点 alpha 已固定），无需重置 shaderColor（未设置）

        // 恢复 invisible 状态（服务端同步值）
        if (progress >= STEALTH_CAP) {
            event.getEntity().setInvisible(true);
        }
    }
}
