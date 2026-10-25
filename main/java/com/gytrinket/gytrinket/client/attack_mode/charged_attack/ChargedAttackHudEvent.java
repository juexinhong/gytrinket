package com.gytrinket.gytrinket.client.attack_mode.charged_attack;

import com.mojang.blaze3d.vertex.PoseStack;

import com.gytrinket.gytrinket.client.screen.GuiCanvas;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ChargedAttackHudEvent {

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;

        // 在准星渲染后绘制充能值
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.type().id())) {
            ChargedAttackHudRenderer.getInstance().render(event.getPoseStack());
        }
    }
}
