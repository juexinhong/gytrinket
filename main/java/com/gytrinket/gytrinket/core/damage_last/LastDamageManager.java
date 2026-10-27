package com.gytrinket.gytrinket.core.damage_last;

import com.gytrinket.gytrinket.gytrinket;
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
        // 仅服务端：客户端本地预测伤害也会触发本事件，最终伤害链会读写共享的属性/护盾状态
        if (event.getEntity().getLevel().isClientSide) {
            return;
        }
        if (event.getAmount() <= 0) {
            return;
        }
        LastDamageContext context = new LastDamageContext(event);
        LastDamageHandlerChain.getInstance().process(context);
    }
}