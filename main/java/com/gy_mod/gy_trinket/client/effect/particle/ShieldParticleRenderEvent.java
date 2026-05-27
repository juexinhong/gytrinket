package com.gy_mod.gy_trinket.client.effect.particle;

import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import com.mojang.blaze3d.vertex.PoseStack;

public class ShieldParticleRenderEvent {
    
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevel);
    }
    
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            PoseStack poseStack = event.getPoseStack();
            float partialTicks = event.getPartialTick();
            
            ShieldParticleRenderer.render(poseStack, null, null, partialTicks);
        }
    }
}