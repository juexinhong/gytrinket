package com.gy_mod.gy_trinket.client.shield;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ShieldHudEvent {

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        if (Config.VANILLA_STYLE_HUD.get()) {
            if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.type().id())) {
                ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
            }
        } else {
            ShieldHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
