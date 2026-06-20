package com.gytrinket.gytrinket.core.damage_last;

import com.gytrinket.gytrinket.gytrinket;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = gytrinket.MODID)
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
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getNewDamage() <= 0) {
            return;
        }
        LastDamageContext context = new LastDamageContext(event);
        LastDamageHandlerChain.getInstance().process(context);
    }
}