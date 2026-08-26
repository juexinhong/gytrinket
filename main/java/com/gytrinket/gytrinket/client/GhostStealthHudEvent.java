package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 幽灵隐身进度HUD事件
 * <p>
 * 在准星渲染后绘制隐身进度文本（位置规则见 {@link GhostStealthHudRenderer}）。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class GhostStealthHudEvent {

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiLayerEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        // 在准星渲染后绘制隐身进度
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            GhostStealthHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
