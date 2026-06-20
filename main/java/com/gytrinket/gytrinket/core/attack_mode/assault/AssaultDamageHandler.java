package com.gytrinket.gytrinket.core.attack_mode.assault;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = gytrinket.MODID)
public class AssaultDamageHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (!AssaultManager.hasAssault(player)) {
                return;
            }

            if (AssaultManager.getAssaultStacks(player) < 1) {
                return;
            }

            LivingEntity target = event.getEntity();
            target.invulnerableTime = 0;
        }
    }
}
