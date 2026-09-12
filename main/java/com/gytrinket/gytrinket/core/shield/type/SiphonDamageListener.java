package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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

        // 从追踪Map获取归属引用（玩家+物品实例），而非从伤害源获取（避免非斩杀时触发仇恨）
        SiphonShieldType.SiphonTargetRef ref = SiphonShieldType.getSiphonTargetRef(target.getUUID());
        if (ref == null) {
            return;
        }

        if (!SiphonShieldType.hasSiphonShieldType(ref.playerUUID())) {
            return;
        }

        float damageAmount = event.getNewDamage();
        double healRatio = DefsManager.resolveShieldTypeValueForItem(
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), ref.itemId(),
                "siphon", "heal_ratio", Config.SIPHON_HEAL_RATIO.get());
        double shieldRecovery = damageAmount * healRatio;

        if (shieldRecovery > 0) {
            ShieldManager.addShield(ref.playerUUID(), shieldRecovery);
        }
    }
}
