package com.gytrinket.gytrinket.client.shield;

import com.gytrinket.gytrinket.Config;
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

        if (Config.VANILLA_STYLE_HUD.get()) {
            // 生存模式：PLAYER_HEALTH 图层会触发
            // 创造模式：PLAYER_HEALTH 不会触发，使用 HOTBAR 作为备选（创造和生存都会渲染）
            boolean isHealthLayer = event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH);
            boolean isHotbarLayer = event.getName().equals(VanillaGuiLayers.HOTBAR);

            if (isHealthLayer) {
                renderedThisFrame = true;
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            } else if (isHotbarLayer && !renderedThisFrame) {
                // 创造模式下PLAYER_HEALTH不会触发，在HOTBAR后渲染
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            }

            // 在帧结束时重置标记（EXPERIENCE_LEVEL是最后渲染的图层之一）
            if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
                renderedThisFrame = false;
            }
        } else {
            ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
