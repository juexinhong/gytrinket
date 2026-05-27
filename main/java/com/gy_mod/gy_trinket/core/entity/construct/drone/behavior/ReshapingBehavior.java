package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ArmorShardEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReshapingBehavior implements IDroneSpecialBehavior {

    private static final Map<UUID, Float> droneDamageRecords = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerDamageReductionInfo> playerDamageReductionMap = new ConcurrentHashMap<>();

    private static final int HEAL_INTERVAL = 20;

    @Override
    public String getId() {
        return "reshaping";
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public void onTick(DroneConstructEntity drone) {
        if (!drone.isDefenseDrone()) return;
        if (drone.level().isClientSide) return;
        if (!hasRequiredItems(drone)) return;

        if (drone.tickCount % HEAL_INTERVAL == 0) {
            float maxHealth = drone.getMaxHealth();
            float healRate = Config.RESHAPING_HEAL_RATE.get().floatValue();
            float healAmount = maxHealth * healRate;
            drone.heal(healAmount);
        }

        cleanupPlayerDamageReduction(drone);
    }

    @Override
    public void onDamageTaken(DroneConstructEntity drone, DamageSource source, float amount) {
        if (!drone.isDefenseDrone()) return;
        if (drone.level().isClientSide) return;
        if (!hasRequiredItems(drone)) return;

        UUID droneUUID = drone.getUUID();
        float currentDamage = droneDamageRecords.getOrDefault(droneUUID, 0.0f);
        droneDamageRecords.put(droneUUID, currentDamage + amount);
    }

    @Override
    public void onDeath(DroneConstructEntity drone, DamageSource source) {
        if (!drone.isDefenseDrone()) return;
        if (drone.level().isClientSide) return;
        if (!hasRequiredItems(drone)) return;

        UUID droneUUID = drone.getUUID();
        float totalDamage = droneDamageRecords.getOrDefault(droneUUID, 0.0f);

        float damageReductionBonus = calculateDamageReductionBonus(totalDamage);

        generateArmorShards(drone, damageReductionBonus);

        droneDamageRecords.remove(droneUUID);
    }

    private float calculateDamageReductionBonus(float totalDamage) {
        float baseBonus = totalDamage * 1.0f;
        float scaledBonus = (float) (100 * Math.log1p(baseBonus / 100));
        return scaledBonus;
    }

    private void generateArmorShards(DroneConstructEntity drone, float damageReductionBonus) {
        if (!(drone.level() instanceof ServerLevel serverLevel)) return;

        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return;

        double x = drone.getX();
        double y = drone.getY();
        double z = drone.getZ();

        ArmorShardEntity armorShard = new ArmorShardEntity(serverLevel, x, y, z, ownerUUID, damageReductionBonus);
        serverLevel.addFreshEntity(armorShard);

        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                x, y, z,
                15, 0.5, 0.5, 0.5, 0.3
        );
    }

    public static void collectArmorShard(ArmorShardEntity armorShard, Player player) {
        float baseReduction = Config.RESHAPING_BASE_DAMAGE_REDUCTION.get().floatValue();
        float bonusMultiplier = armorShard.getDamageReductionBonus();
        float multiplier = 1.0f + (bonusMultiplier / 100.0f);
        float totalReduction = baseReduction * multiplier;

        applyDamageReduction(player, totalReduction);
    }

    private static void applyDamageReduction(Player player, float damageReduction) {
        UUID playerUUID = player.getUUID();
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) return;

        int currentTick = server.getTickCount();
        int duration = Config.RESHAPING_DAMAGE_REDUCTION_DURATION.get();

        PlayerDamageReductionInfo info = playerDamageReductionMap.get(playerUUID);

        if (info == null) {
            info = new PlayerDamageReductionInfo(damageReduction, currentTick + duration);
        } else {
            float existingReduction = info.getDamageReduction();
            float newReduction = Math.max(existingReduction, damageReduction);
            info = new PlayerDamageReductionInfo(newReduction, currentTick + duration);
        }

        playerDamageReductionMap.put(playerUUID, info);
    }

    private void cleanupPlayerDamageReduction(DroneConstructEntity drone) {
        Entity owner = drone.getOwner();
        if (!(owner instanceof Player player)) return;

        UUID playerUUID = player.getUUID();
        PlayerDamageReductionInfo info = playerDamageReductionMap.get(playerUUID);
        if (info == null) return;

        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) return;

        int currentTick = server.getTickCount();
        if (currentTick > info.getExpiryTick()) {
            playerDamageReductionMap.remove(playerUUID);
        }
    }

    public static float getPlayerDamageReduction(Player player) {
        if (player.level().isClientSide) return 0.0f;

        UUID playerUUID = player.getUUID();
        PlayerDamageReductionInfo info = playerDamageReductionMap.get(playerUUID);
        if (info == null) return 0.0f;

        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) return 0.0f;

        int currentTick = server.getTickCount();
        if (currentTick <= info.getExpiryTick()) {
            return info.getDamageReduction();
        }
        return 0.0f;
    }

    private boolean hasRequiredItems(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return false;
        PlayerStore store = PlayerStoreManager.getPlayerStore(ownerUUID);
        if (store == null) return false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(ownerUUID, stack)) continue;

            if (Config.isReshapingItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static class PlayerDamageReductionInfo {
        private final float damageReduction;
        private final int expiryTick;

        public PlayerDamageReductionInfo(float damageReduction, int expiryTick) {
            this.damageReduction = damageReduction;
            this.expiryTick = expiryTick;
        }

        public float getDamageReduction() {
            return damageReduction;
        }

        public int getExpiryTick() {
            return expiryTick;
        }
    }
}
