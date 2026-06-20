package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.core.modifier.ModifierHelper;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 处理模组移动速度修改对FOV的影响
 * 当模组施加减速效果时，取消原版的镜头放大（FOV缩小）效果
 * 保留加速时的镜头缩小（FOV放大）效果
 *
 * 原版FOV计算公式（AbstractClientPlayer.getFieldOfViewModifier）：
 *   f = 1.0
 *   if flying: f *= 1.1
 *   f *= (getAttributeValue(MOVEMENT_SPEED) / walkingSpeed + 1.0) / 2.0
 *   if using bow: f *= ...
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class FovHandler {

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        AttributeInstance speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }

        // 计算所有模组减速修改器的总乘积
        double totalDecayMultiplier = 1.0;
        boolean hasDecay = false;

        for (AttributeModifier modifier : speedAttribute.getModifiers()) {
            if (modifier.id().toString().startsWith(ModifierHelper.MOD_PREFIX)) {
                // MULTIPLY_TOTAL 的 amount = multiplier - 1
                double multiplier = modifier.amount() + 1.0;
                if (multiplier < 1.0 && multiplier > 0.0) {
                    totalDecayMultiplier *= multiplier;
                    hasDecay = true;
                }
            }
        }

        if (!hasDecay) {
            return;
        }

        float fovModifier = event.getFovModifier();

        double currentSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        // 去除所有模组减速后的速度
        double speedWithoutDecay = currentSpeed / totalDecayMultiplier;
        float walkingSpeed = player.getAbilities().getWalkingSpeed();

        // 按原版公式重新计算速度部分的FOV贡献
        float correctedSpeedFov = (float)((speedWithoutDecay / walkingSpeed + 1.0) / 2.0);
        float currentSpeedFov = (float)((currentSpeed / walkingSpeed + 1.0) / 2.0);

        // 将整个 fovModifier 乘以修正比来抵消减速影响
        if (currentSpeedFov > 0.0F && !Float.isNaN(currentSpeedFov) && !Float.isInfinite(currentSpeedFov)) {
            float correctedFovModifier = fovModifier * (correctedSpeedFov / currentSpeedFov);
            float fovEffectScale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
            float correctedNewFovModifier = Mth.lerp(fovEffectScale, 1.0F, correctedFovModifier);
            event.setNewFovModifier(correctedNewFovModifier);
        }
    }
}
