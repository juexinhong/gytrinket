package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class LastDamageManager {

    private static boolean initialized = false;

    private LastDamageManager() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LastDamageHandlerChain.getInstance().registerHandler(new PlayerDamageLastHandler());
        LastDamageHandlerChain.getInstance().registerHandler(new PlayerSelfDamageLastHandler());
        LastDamageHandlerChain.getInstance().registerHandler(new ReshapingLastDamageHandler());
        LastDamageHandlerChain.getInstance().registerHandler(new CoatingDamageLastHandler());
        LastDamageHandlerChain.getInstance().registerHandler(new AdaptiveArmorLastDamageHandler());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0) {
            return;
        }
        LastDamageContext context = new LastDamageContext(event);
        LastDamageHandlerChain.getInstance().process(context);
    }
}