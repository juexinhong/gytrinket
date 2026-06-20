package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

@EventBusSubscriber(modid = gytrinket.MODID)
public class SiphonDamageListener {

    private SiphonDamageListener() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        ResourceKey<DamageType> damageType = event.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType != ModDamageTypes.SIPHON_DAMAGE) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }

        // 从追踪Map获取玩家UUID，而非从伤害源获取（避免非斩杀时触发仇恨）
        UUID playerUUID = SiphonShieldType.getSiphonPlayerUUID(target.getUUID());
        if (playerUUID == null) {
            return;
        }

        if (!SiphonShieldType.hasSiphonShieldType(playerUUID)) {
            return;
        }

        float damageAmount = event.getNewDamage();
        double shieldRecovery = damageAmount * Config.SIPHON_HEAL_RATIO.get();

        if (shieldRecovery > 0) {
            ShieldManager.addShield(playerUUID, shieldRecovery);
        }
    }
}
