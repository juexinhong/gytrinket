package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.core.modifier.ModifierHelper;
import com.gy_mod.gy_trinket.core.shield.type.AmplificationShieldType;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 处理模组移动速度修改对FOV的影响
 * 当模组施加减速效果时，取消原版的镜头放大（FOV缩小）效果
 * 同时抵消增幅护盾移动速度加成带来的FOV缩放（护盾效果动态变化时镜头不抖动）
 *
 * 原版FOV计算公式（AbstractClientPlayer.getFieldOfViewModifier）：
 *   f = 1.0
 *   if flying: f *= 1.1
 *   f *= (getAttributeValue(MOVEMENT_SPEED) / walkingSpeed + 1.0) / 2.0
 *   if using bow: f *= ...
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
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

        // 增幅护盾移动速度加成：抵消其FOV缩放（护盾效果动态变化时不抖动镜头）
        double amplificationMultiplier = 1.0;
        boolean hasAmplification = false;

        for (AttributeModifier modifier : speedAttribute.getModifiers()) {
            // 1.20.1：AttributeModifier 归属标识在名称中（"gytrinket:xxx"），getId() 为 UUID 无法匹配前缀
            if (modifier.getName().startsWith(ModifierHelper.MOD_PREFIX)) {
                // MULTIPLY_TOTAL 的 amount = multiplier - 1
                double multiplier = modifier.getAmount() + 1.0;
                if (multiplier < 1.0 && multiplier > 0.0) {
                    totalDecayMultiplier *= multiplier;
                    hasDecay = true;
                }
                if (AmplificationShieldType.isMovementSpeedModifier(modifier.getId()) && multiplier > 1.0) {
                    amplificationMultiplier *= multiplier;
                    hasAmplification = true;
                }
            }
        }

        if (!hasDecay && !hasAmplification) {
            return;
        }

        float fovModifier = event.getFovModifier();

        double currentSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        // 去除所有模组减速及增幅护盾加速后的速度
        double speedWithoutCompensation = currentSpeed / (totalDecayMultiplier * amplificationMultiplier);
        float walkingSpeed = player.getAbilities().getWalkingSpeed();

        // 按原版公式重新计算速度部分的FOV贡献
        float correctedSpeedFov = (float)((speedWithoutCompensation / walkingSpeed + 1.0) / 2.0);
        float currentSpeedFov = (float)((currentSpeed / walkingSpeed + 1.0) / 2.0);

        // 将整个 fovModifier 乘以修正比来抵消减速/增幅护盾加速影响
        if (currentSpeedFov > 0.0F && !Float.isNaN(currentSpeedFov) && !Float.isInfinite(currentSpeedFov)) {
            float correctedFovModifier = fovModifier * (correctedSpeedFov / currentSpeedFov);
            float fovEffectScale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
            float correctedNewFovModifier = Mth.lerp(fovEffectScale, 1.0F, correctedFovModifier);
            event.setNewFovModifier(correctedNewFovModifier);
        }
    }
}
