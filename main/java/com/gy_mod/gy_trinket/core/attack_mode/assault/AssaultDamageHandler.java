package com.gy_mod.gy_trinket.core.attack_mode.assault;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AssaultDamageHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
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
