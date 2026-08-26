package com.gy_mod.gy_trinket.core.shield.type;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class SiphonDamageListener {

    private SiphonDamageListener() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
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

        float damageAmount = event.getAmount();
        double shieldRecovery = damageAmount * Config.SIPHON_HEAL_RATIO.get();

        if (shieldRecovery > 0) {
            ShieldManager.addShield(playerUUID, shieldRecovery);
        }
    }
}
