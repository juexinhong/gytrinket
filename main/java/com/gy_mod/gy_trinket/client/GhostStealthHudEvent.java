package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 幽灵隐身进度HUD事件
 * <p>
 * 在准星渲染后绘制隐身进度文本（位置规则见 {@link GhostStealthHudRenderer}）。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class GhostStealthHudEvent {

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        // 在准星渲染后绘制隐身进度
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.type().id())) {
            GhostStealthHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
