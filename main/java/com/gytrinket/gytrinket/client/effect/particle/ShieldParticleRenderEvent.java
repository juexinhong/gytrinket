package com.gytrinket.gytrinket.client.effect.particle;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.vertex.PoseStack;

public class ShieldParticleRenderEvent {

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ShieldParticleRenderEvent::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            PoseStack poseStack = event.getPoseStack();
            float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);

            ShieldParticleRenderer.render(poseStack, null, null, partialTicks);
        }
    }
}
