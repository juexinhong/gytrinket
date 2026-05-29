package com.gy_mod.gy_trinket.core.shield.type;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class SiphonDamageListener {

    private SiphonDamageListener() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        ResourceKey<DamageType> damageType = event.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType != ModDamageTypes.SIPHON_DAMAGE) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        if (player.level().isClientSide) {
            return;
        }

        if (!SiphonShieldType.hasSiphonShieldType(player.getUUID())) {
            return;
        }

        float damageAmount = event.getAmount();
        double shieldRecovery = damageAmount * Config.SIPHON_HEAL_RATIO.get();

        if (shieldRecovery > 0) {
            ShieldManager.addShield(player.getUUID(), shieldRecovery);
        }
    }
}
