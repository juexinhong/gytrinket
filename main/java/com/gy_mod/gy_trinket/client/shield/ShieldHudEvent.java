package com.gy_mod.gy_trinket.client.shield;

import com.gy_mod.gy_trinket.config.ClientConfig;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ShieldHudEvent {

    private static boolean renderedThisFrame = false;

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        if (ClientConfig.VANILLA_STYLE_HUD.get()) {
            // 只在 PLAYER_HEALTH 图层渲染后触发一次，避免重复渲染
            boolean isHealthLayer = event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.type().id());
            boolean isHotbarLayer = event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.type().id());

            if (isHealthLayer) {
                renderedThisFrame = true;
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            } else if (isHotbarLayer && !renderedThisFrame) {
                // 创造模式下PLAYER_HEALTH不会触发，在HOTBAR后渲染
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            }

            // 在帧结束时重置标记
            if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.type().id())) {
                renderedThisFrame = false;
            }
        } else {
            ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
