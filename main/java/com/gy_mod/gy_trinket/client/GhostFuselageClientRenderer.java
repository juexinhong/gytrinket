package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageClientData;
import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 * 这会导致客户端模型完全不可见。因此在Pre中临时恢复可见，
 * 用setShaderColor实现渐进透明度，Post中恢复invisible状态。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GhostFuselageClientRenderer {

    /** 完全隐身进度阈值 */
    private static final float STEALTH_CAP = 0.8f;

    /** 最低透明度（完全隐身时） */
    private static final float MIN_ALPHA = 0.3f;

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        float progress = GhostFuselageClientData.getStealthProgress(event.getEntity().getId());
        if (progress > 0.001f) {
            // 抵消服务端invisible同步导致的模型完全隐藏
            if (event.getEntity().isInvisible()) {
                event.getEntity().setInvisible(false);
            }

            float alpha = 1.0f - progress * (1.0f - MIN_ALPHA) / STEALTH_CAP;
            alpha = Math.max(MIN_ALPHA, alpha);
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        float progress = GhostFuselageClientData.getStealthProgress(event.getEntity().getId());
        if (progress > 0.001f) {
            // 强制刷出缓冲区，确保玩家模型顶点在shader color仍含alpha时绘制
            if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // 恢复invisible状态（服务端同步值）
            if (progress >= STEALTH_CAP) {
                event.getEntity().setInvisible(true);
            }
        }
    }
}
