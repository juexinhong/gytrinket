package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.core.damage.GyDamageType;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.ShieldValueDefs;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class SiphonDamageListener {

    private SiphonDamageListener() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        GyDamageType damageType = ModDamageTypes.typeOf(event.getSource());
        if (damageType != ModDamageTypes.SIPHON_DAMAGE) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (target.getLevel().isClientSide) {
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

        // heal_ratio 按产生虹吸的物品实例取值
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        double healRatio = DefsManager.resolveShieldTypeValueForItem(server, ref.itemId(),
                "siphon", "heal_ratio", ShieldValueDefs.defaultValueOf("siphon", "heal_ratio", 0.3));

        float damageAmount = event.getAmount();
        double shieldRecovery = damageAmount * healRatio;

        if (shieldRecovery > 0) {
            ShieldManager.addShield(ref.playerUUID(), shieldRecovery);
        }
    }
}
