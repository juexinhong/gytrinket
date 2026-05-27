package com.gy_mod.gy_trinket.core.burn;

import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class BurnManager {

    private static final int DEFAULT_BURN_DURATION = 20;
    private static final float MIN_BURN_DAMAGE = 1f;

    private static final Map<UUID, BurnData> ENTITY_BURN_DATA = new HashMap<>();
    private static final Map<String, Long> BURN_COOLDOWN = new HashMap<>();
    private static final long BURN_COOLDOWN_TICKS = 5;
    private static long lastProcessedTick = -1;

    private static String getCooldownKey(UUID targetUUID, String sourceName) {
        return targetUUID.toString() + "_" + sourceName;
    }

    public static BurnData getOrCreateBurnData(LivingEntity entity) {
        return ENTITY_BURN_DATA.computeIfAbsent(
            entity.getUUID(),
            uuid -> new BurnData(entity, DEFAULT_BURN_DURATION)
        );
    }

    public static BurnData getBurnData(LivingEntity entity) {
        return ENTITY_BURN_DATA.get(entity.getUUID());
    }

    public static boolean isBurning(LivingEntity entity) {
        BurnData data = getBurnData(entity);
        return data != null && data.isBurning();
    }

    public static void applyBurnCharge(LivingEntity target, float chargeRate, IBurnSource source) {
        if (target == null || target.isDeadOrDying()) {
            return;
        }

        UUID targetUUID = target.getUUID();
        String sourceName = source.getName();
        String cooldownKey = getCooldownKey(targetUUID, sourceName);

        if (isOnCooldown(cooldownKey)) {
            return;
        }

        long currentTick = target.level().getGameTime();
        BurnData burnData = getOrCreateBurnData(target);

        burnData.addCharge(chargeRate, source, currentTick);

        setCooldown(cooldownKey);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (event.level.isClientSide) {
            return;
        }

        long currentTick = event.level.getGameTime();

        if (currentTick == lastProcessedTick) {
            return;
        }
        lastProcessedTick = currentTick;

        for (UUID uuid : new HashMap<UUID, BurnData>(ENTITY_BURN_DATA).keySet()) {
            BurnData burnData = ENTITY_BURN_DATA.get(uuid);
            if (burnData == null) {
                continue;
            }

            LivingEntity target = burnData.getTarget();

            if (target == null || !target.isAlive()) {
                removeBurnData(uuid);
                continue;
            }

            processBurnTick(uuid, currentTick);
        }
    }

    private static void processBurnTick(UUID targetUUID, long currentTick) {
        BurnData burnData = ENTITY_BURN_DATA.get(targetUUID);
        if (burnData == null || !burnData.isBurning()) {
            return;
        }

        LivingEntity target = burnData.getTarget();
        if (target == null || !target.isAlive()) {
            removeBurnData(targetUUID);
            return;
        }

        if (burnData.isComplete(currentTick)) {
            completeBurn(burnData, target);
            return;
        }

        if (!burnData.isKillCheckPerformed()) {
            if (canKillTarget(burnData, target)) {
                executeEarlyKill(burnData, target);
            }
        }
    }

    private static void completeBurn(BurnData burnData, LivingEntity target) {
        float finalDamage = Math.max(MIN_BURN_DAMAGE, burnData.getAccumulatedDamage());
        Entity primaryInitiator = burnData.getPrimaryInitiator();

        applyBurnDamage(target, finalDamage, primaryInitiator);

        burnData.reset();
    }

    private static void executeEarlyKill(BurnData burnData, LivingEntity target) {
        float finalDamage = burnData.getAccumulatedDamageOrMin();
        Entity primaryInitiator = burnData.getPrimaryInitiator();

        applyBurnDamage(target, finalDamage, primaryInitiator);

        burnData.reset();
    }

    private static boolean canKillTarget(BurnData burnData, LivingEntity target) {
        if (target.getHealth() <= burnData.getAccumulatedDamageOrMin()) {
            return true;
        }
        burnData.setKillCheckPerformed();
        return false;
    }

    private static void applyBurnDamage(LivingEntity target, float damage, Entity initiator) {
        if (target.level().isClientSide) {
            return;
        }

        float halfDamage = damage / 2f;

        com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager.markNoKnockback(target.getUUID());
        target.invulnerableTime = 0;
        target.hurt(ModDamageTypes.getBurnDamageSource(target.level(), initiator), halfDamage);

        target.invulnerableTime = 0;
        target.hurt(target.damageSources().magic(), halfDamage);

        spawnBurnParticles(target);
    }

    @SuppressWarnings("resource")
    private static void spawnBurnParticles(LivingEntity target) {
        ServerLevel serverLevel = (ServerLevel) target.level();
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() / 2.0;
        double z = target.getZ();

        serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 20, 0.5, 0.5, 0.5, 0.05);
        serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 5, 0.3, 0.3, 0.3, 0.1);
        serverLevel.sendParticles(ParticleTypes.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0.05);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 5, 0.5, 0.5, 0.5, 0.05);
    }

    private static void setCooldown(String cooldownKey) {
        BURN_COOLDOWN.put(cooldownKey, System.currentTimeMillis());
    }

    private static boolean isOnCooldown(String cooldownKey) {
        Long lastTime = BURN_COOLDOWN.get(cooldownKey);
        if (lastTime == null) {
            return false;
        }
        if (System.currentTimeMillis() - lastTime > BURN_COOLDOWN_TICKS) {
            BURN_COOLDOWN.remove(cooldownKey);
            return false;
        }
        return true;
    }

    public static void removeBurnData(UUID targetUUID) {
        ENTITY_BURN_DATA.remove(targetUUID);
    }

    public static void clearAllBurnData() {
        ENTITY_BURN_DATA.clear();
        BURN_COOLDOWN.clear();
    }

    public static void clearBurn(LivingEntity entity) {
        if (entity != null) {
            removeBurnData(entity.getUUID());
        }
    }
}