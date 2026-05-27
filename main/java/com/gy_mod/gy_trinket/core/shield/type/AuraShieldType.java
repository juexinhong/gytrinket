package com.gy_mod.gy_trinket.core.shield.type;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.burn.BurnManager;
import com.gy_mod.gy_trinket.core.burn.IBurnSource;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.ignite.IIgniteSource;
import com.gy_mod.gy_trinket.core.ignite.IgniteManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class AuraShieldType implements IShieldType {

    private static final Map<UUID, Integer> PARTICLE_COUNTER = new HashMap<>();
    private static final int PARTICLE_TRIGGER_INTERVAL = 3;

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
    public void onTick(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        double currentShield = ShieldManager.getCurrentShield(player.getUUID());
        if (currentShield <= 0) {
            return;
        }

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
                sendParticles(player, center, radius);
            }
        }

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
            Player owner = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                return HostileTargetManager.shouldAttackPlayer(entity, owner);
            }
        }

        return false;
    }

    private void sendParticles(Player player, LivingEntity center, double radius) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        UUID playerId = player.getUUID();

        int particleCounter = PARTICLE_COUNTER.getOrDefault(playerId, 0);
        particleCounter++;
        PARTICLE_COUNTER.put(playerId, particleCounter);

        if (particleCounter < PARTICLE_TRIGGER_INTERVAL) {
            return;
        }
        PARTICLE_COUNTER.put(playerId, 0);

        double x = center.getX();
        double y = center.getY() + center.getBbHeight() / 4.0;
        double z = center.getZ();
        NetworkHandler.sendAuraParticlesToPlayer(serverPlayer, x, y, z, radius);
    }

    public static void clearPlayerData(UUID playerUUID) {
        PARTICLE_COUNTER.remove(playerUUID);
    }

    public static void clearAllData() {
        PARTICLE_COUNTER.clear();
    }
}