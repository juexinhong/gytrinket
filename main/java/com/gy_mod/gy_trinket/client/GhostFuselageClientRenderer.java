package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.client.compat.ShaderModCompat;
import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageClientData;
import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 幽灵机身客户端渲染器
 * <p>
 * 根据隐身进度调整玩家模型身体层的透明度：
 * <ul>
 *   <li>进度0%时完全可见（alpha=1.0）</li>
 *   <li>进度100%（完全隐身）时alpha=0.3（最低值）</li>
 * </ul>
 * <p>
 * 服务端在完全隐身时设置原版invisible标签（阻止怪物目标选取），
 * 这会导致客户端模型完全不可见。因此在Pre中临时恢复可见。
 * <p>
 * 渲染策略（光影/非光影统一）：
 * 取消事件并手动渲染，通过 GhostAlphaBufferSource 包装 MultiBufferSource，
 * 仅将玩家身体层替换为 entityTranslucent 并通过 GhostAlphaVertexConsumer 修改顶点 alpha；
 * 铠甲层 / 手持物品层保持原渲染类型原样透传，材质正确且不受透明度影响。
 * <p>
 * 使用顶点颜色 alpha 而非 setShaderColor 的原因：
 * Iris 的 FullyBufferedMultiBufferSource 延迟 endBatch，setShaderColor 的 alpha
 * 会在绘制时被重置；顶点颜色 alpha 在顶点写入时即固定，不受 endBatch 时序影响。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class GhostFuselageClientRenderer {

    /** 完全隐身进度上限（alpha曲线基准：进度满时透明度最低） */
    private static final float STEALTH_CAP = 1.0f;

    /** 完全隐身进入阈值（与服务端一致：进度>=95%时服务端设置invisible，需同步恢复） */
    private static final float STEALTH_ENTER_THRESHOLD = 0.95f;

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

        // 取消原渲染，手动渲染：仅玩家身体层通过 GhostAlphaBufferSource 降低顶点 alpha，
        // 铠甲/手持物品按原渲染类型正常绘制（不透明、材质正确）
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

        // 非光影模式：立即刷新缓冲，让半透明身体按顶点 alpha 正确绘制
        if (!ShaderModCompat.isShaderPackInUse()) {
            if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }
        }

        // 恢复 invisible 状态（服务端同步值；取消事件后 Post 不会触发）
        if (wasInvisible && progress >= STEALTH_ENTER_THRESHOLD) {
            player.setInvisible(true);
        }
    }
}
