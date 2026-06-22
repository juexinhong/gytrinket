package com.gytrinket.gytrinket.client.shield;

import com.gytrinket.gytrinket.ClientConfig;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ShieldHudEvent {

    private static boolean renderedThisFrame = false;

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiLayerEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        if (ClientConfig.VANILLA_STYLE_HUD.get()) {
            // 只在 PLAYER_HEALTH 图层渲染后触发一次，避免重复渲染
            boolean isHealthLayer = event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH);
            boolean isHotbarLayer = event.getName().equals(VanillaGuiLayers.HOTBAR);

            if (isHealthLayer) {
                renderedThisFrame = true;
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            } else if (isHotbarLayer && !renderedThisFrame) {
                // 创造模式下PLAYER_HEALTH不会触发，在HOTBAR后渲染
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            }

            // 在帧结束时重置标记
            if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
                renderedThisFrame = false;
            }
        } else {
            ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
