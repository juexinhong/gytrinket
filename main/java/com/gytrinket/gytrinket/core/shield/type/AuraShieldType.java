package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.burn.BurnManager;
import com.gytrinket.gytrinket.core.burn.IBurnSource;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.ignite.IIgniteSource;
import com.gytrinket.gytrinket.core.ignite.IgniteManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class AuraShieldType implements IShieldType {

    private static final Map<UUID, Boolean> AURA_DAMAGING = new HashMap<>();
    private static final Map<UUID, Integer> TICK_COUNTERS = new HashMap<>();

    private static class AuraBurnSource implements IBurnSource {
        private final Player player;

        public AuraBurnSource(Player player) {
            this.player = player;
        }

        @Override
        public net.minecraft.world.entity.Entity getInitiator() {
            return player;
        }

        @Override
        public String getName() {
            return "AuraShield";
        }
    }

    private static class AuraIgniteSource implements IIgniteSource {
        private final Player player;

        public AuraIgniteSource(Player player) {
            this.player = player;
        }

        @Override
        public net.minecraft.world.entity.Entity getInitiator() {
            return player;
        }

        @Override
        public String getName() {
            return "AuraShield";
        }
    }

    @Override
    public String getName() {
        return "aura";
    }

    @Override
    public boolean isCompatible() {
        return false;
    }

    @Override
    public void onRemoved(Player player) {
        if (player.level().isClientSide) return;
        UUID uuid = player.getUUID();
        AURA_DAMAGING.put(uuid, false);
        TICK_COUNTERS.remove(uuid);
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(uuid), ShieldManager.getMaxShield(uuid));
        }
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        UUID uuid = player.getUUID();

        double currentShield = ShieldManager.getCurrentShield(uuid);
        if (currentShield <= 0) {
            AURA_DAMAGING.put(uuid, false);
            TICK_COUNTERS.remove(uuid);
            return;
        }

        int tickCounter = TICK_COUNTERS.getOrDefault(uuid, 0) + 1;
        if (tickCounter < Config.AURA_TRIGGER_FREQUENCY.get()) {
            TICK_COUNTERS.put(uuid, tickCounter);
            return;
        }
        TICK_COUNTERS.put(uuid, 0);

        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double baseRadius = Config.AURA_RADIUS.get();
        double radius = baseRadius * shieldEffectRadius;

        float totalShieldCost = 0;
        boolean hasEnemies = false;

        Map<LivingEntity, Float> burnChargeMap = new HashMap<>();
        Set<LivingEntity> igniteTargets = new HashSet<>();

        for (LivingEntity center : effectCenters) {
            if (center == null || !center.isAlive()) {
                continue;
            }

            AABB boundingBox = center.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, boundingBox,
                entity -> isValidTarget(entity, center)
            );

            for (LivingEntity target : entities) {
                hasEnemies = true;
                double chargeRate = Config.AURA_DAMAGE.get();
                double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
                float charge = (float)(chargeRate * shieldEffect);
                
                burnChargeMap.merge(target, charge, Float::sum);
                igniteTargets.add(target);
            }

            if (!entities.isEmpty()) {
                double baseShieldCost = Config.AURA_SHIELD_COST.get();
                totalShieldCost += baseShieldCost * entities.size();
            }
        }

        AURA_DAMAGING.put(player.getUUID(), hasEnemies);

        IBurnSource burnSource = new AuraBurnSource(player);
        for (Map.Entry<LivingEntity, Float> entry : burnChargeMap.entrySet()) {
            BurnManager.applyBurnCharge(entry.getKey(), entry.getValue(), burnSource);
        }

        IIgniteSource igniteSource = new AuraIgniteSource(player);
        for (LivingEntity target : igniteTargets) {
            IgniteManager.applyIgnite(target, igniteSource, "AuraShield", false);
        }

        if (hasEnemies) {
            DamageSource shieldSelfDamage = ModDamageTypes.getShieldSelfDamageSource(player.level());
            player.hurt(shieldSelfDamage, totalShieldCost);
        }
    }

    private boolean isValidTarget(LivingEntity entity, LivingEntity center) {
        if (entity == center) {
            return false;
        }

        if (entity instanceof Player targetPlayer) {
            if (!HostileTargetManager.shouldAttackPlayer(center, targetPlayer)) {
                return false;
            }
        }

        if (center instanceof Player centerPlayer) {
            return HostileTargetManager.shouldAttackPlayer(entity, centerPlayer);
        }

        UUID ownerUUID = ShieldTransferManager.getShieldOwnerUUID(center);
        if (ownerUUID != null) {
            Player owner = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                return HostileTargetManager.shouldAttackPlayer(entity, owner);
            }
        }

        return false;
    }

    public static boolean isAuraDamaging(UUID playerUUID) {
        return AURA_DAMAGING.getOrDefault(playerUUID, false);
    }

    public static void clearPlayerData(UUID playerUUID) {
        AURA_DAMAGING.remove(playerUUID);
        TICK_COUNTERS.remove(playerUUID);
    }

    public static void clearAllData() {
        AURA_DAMAGING.clear();
        TICK_COUNTERS.clear();
    }
}
