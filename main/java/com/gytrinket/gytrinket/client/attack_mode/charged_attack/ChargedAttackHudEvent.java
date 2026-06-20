package com.gytrinket.gytrinket.client.attack_mode.charged_attack;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ChargedAttackHudEvent {

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiLayerEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        // 在准星渲染后绘制充能值
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            ChargedAttackHudRenderer.getInstance().render(event.getGuiGraphics());
        }
    }
}
