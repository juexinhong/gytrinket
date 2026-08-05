package com.gytrinket.gytrinket.client.weapon.flamespear;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class FlameSpearHudEvent {

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiLayerEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        // 在准星渲染后绘制焰矛充能HUD
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            FlameSpearHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
