package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.execute.ExecuteToggleManager;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

public class SiphonShieldType implements IShieldType {

    private static final Map<UUID, SiphonData> PLAYER_SIPHON_DATA = new HashMap<>();

    // 方案B追踪Map：目标UUID → 玩家UUID，用于SiphonDamageListener获取玩家引用
    private static final Map<UUID, UUID> SIPHON_TARGET_TO_PLAYER = new HashMap<>();

    private static class SiphonData {
        int stacks;
        int remainingTicks;
        int tickCounter;
        boolean decaying;
        int lastSyncedStacks;

        SiphonData() {
            this.stacks = 0;
            this.remainingTicks = 0;
            this.tickCounter = 0;
            this.decaying = false;
            this.lastSyncedStacks = 0;
        }
    }

    @Override
    public String getName() {
        return "siphon";
    }

    @Override
    public boolean isCompatible() {
        return false;
    }

    @Override
    public void onRemoved(Player player) {
        if (player.level().isClientSide) return;
        UUID uuid = player.getUUID();
        SiphonData data = PLAYER_SIPHON_DATA.get(uuid);
        if (data != null) {
            data.stacks = 0;
            data.remainingTicks = 0;
            data.decaying = false;
            data.lastSyncedStacks = 0;
        }
        AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_percent");
        AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_radius");
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(uuid), ShieldManager.getMaxShield(uuid));
        }
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide) return;
        double currentShield = ShieldManager.getCurrentShield(player.getUUID());
        if (currentShield <= 0) return;

        UUID uuid = player.getUUID();
        SiphonData data = PLAYER_SIPHON_DATA.computeIfAbsent(uuid, k -> new SiphonData());

        data.tickCounter++;
        if (data.tickCounter >= Config.SIPHON_TICK_INTERVAL.get()) {
            data.tickCounter = 0;
            performSiphon(player, data);
        }

        updateSiphonDecay(uuid, data);
        updateSiphonAttributes(uuid, data);

        if (data.stacks != data.lastSyncedStacks) {
            data.lastSyncedStacks = data.stacks;
            syncSiphonStacksToClient(player, data.stacks);
        }
    }

    private void performSiphon(Player player, SiphonData data) {
        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double baseRadius = Config.SIPHON_RADIUS.get();
        double radius = baseRadius * shieldEffectRadius;

        double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
        double baseDamage = Config.SIPHON_DAMAGE.get();
        double totalDamage = baseDamage * shieldEffect;

        List<LivingEntity> targets = new ArrayList<>();

        for (LivingEntity center : effectCenters) {
            if (center == null || !center.isAlive()) continue;

            AABB boundingBox = center.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, boundingBox,
                entity -> isValidTarget(entity, center)
            );

            for (LivingEntity target : entities) {
                if (target.isAlive() && target.getHealth() > 0) {
                    targets.add(target);
                }
            }
        }

        if (!targets.isEmpty()) {
            float damagePerTarget = (float) (totalDamage / targets.size());

            for (LivingEntity target : targets) {
                KnockbackManager.markNoKnockback(target.getUUID());
                target.invulnerableTime = 0;

                // 斩杀归属：仅当伤害足以击杀且斩杀开关启用时，将玩家作为伤害源（归属击杀）
                boolean canKill = target.getHealth() <= damagePerTarget;
                boolean executeEnabled = canKill && ExecuteToggleManager.isExecuteEnabled(player);
                DamageSource siphonSource = ModDamageTypes.getSiphonDamageSource(
                    player.level(), executeEnabled ? player : null);

                // 记录目标→玩家映射，供SiphonDamageListener使用
                SIPHON_TARGET_TO_PLAYER.put(target.getUUID(), player.getUUID());

                target.hurt(siphonSource, damagePerTarget);
                target.invulnerableTime = 0;  //这个决定不能删除!害我找半天哪里有问题.

                // hurt()同步执行，监听器已处理完毕，移除追踪
                SIPHON_TARGET_TO_PLAYER.remove(target.getUUID());

                sendSiphonParticles(player, target);
            }

            data.stacks++;
            data.remainingTicks = Config.SIPHON_DURATION_TICKS.get();
            data.decaying = false;
        }
    }

    private boolean isValidTarget(LivingEntity entity, LivingEntity center) {
        if (entity == center) return false;
        if (entity instanceof Player targetPlayer) {
            if (!HostileTargetManager.shouldAttackPlayer(center, targetPlayer)) return false;
        }
        if (center instanceof Player centerPlayer) {
            return HostileTargetManager.shouldAttackPlayer(entity, centerPlayer);
        }
        UUID ownerUUID = ShieldTransferManager.getShieldOwnerUUID(center);
        if (ownerUUID != null) {
            Player owner = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) return HostileTargetManager.shouldAttackPlayer(entity, owner);
        }
        return false;
    }

    private void updateSiphonDecay(UUID uuid, SiphonData data) {
        if (data.stacks <= 0) return;

        if (!data.decaying) {
            data.remainingTicks--;
            if (data.remainingTicks <= 0) {
                data.decaying = true;
            }
        }

        if (data.decaying) {
            int decayAmount = 1 + (int) Math.floor(data.stacks * Config.SIPHON_DECAY_RATIO.get());
            data.stacks -= decayAmount;
            if (data.stacks <= 0) {
                data.stacks = 0;
                data.decaying = false;
                data.remainingTicks = 0;
            }
        }
    }

    private void updateSiphonAttributes(UUID uuid, SiphonData data) {
        double effectPerStack = Config.SIPHON_EFFECT_PER_STACK.get();
        double maxEffect = Config.SIPHON_MAX_EFFECT.get();
        double shieldEffectBonus = Math.min(data.stacks * effectPerStack, maxEffect);
        double shieldEffectRadiusBonus = Math.min(data.stacks * effectPerStack, maxEffect);

        AttributeManager.setDynamicAttribute(uuid, "siphon", "shield_effect_percent", shieldEffectBonus);
        AttributeManager.setDynamicAttribute(uuid, "siphon", "shield_effect_radius", shieldEffectRadiusBonus);
    }

    private void syncSiphonStacksToClient(Player player, int stacks) {
        if (player instanceof ServerPlayer serverPlayer) {
            double currentShield = ShieldManager.getCurrentShield(player.getUUID());
            double maxShield = ShieldManager.getMaxShield(player.getUUID());
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer, currentShield, maxShield);
        }
    }

    private void sendSiphonParticles(Player player, LivingEntity target) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        double targetCenterX = target.getX();
        double targetCenterY = target.getY() + target.getBbHeight() / 2.0;
        double targetCenterZ = target.getZ();
        double targetHeight = target.getBbHeight();

        LivingEntity nearestCenter = findNearestEffectCenter(player, target);
        double destX = nearestCenter.getX();
        double destY = nearestCenter.getY() + nearestCenter.getBbHeight() / 2.0;
        double destZ = nearestCenter.getZ();

        NetworkHandler.sendSiphonParticlesToPlayer(serverPlayer,
            targetCenterX, targetCenterY, targetCenterZ, targetHeight,
            destX, destY, destZ);
    }

    private LivingEntity findNearestEffectCenter(Player player, LivingEntity target) {
        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            return player;
        }

        LivingEntity nearest = player;
        double minDist = target.distanceToSqr(player);

        for (LivingEntity center : effectCenters) {
            if (center == null || !center.isAlive()) continue;
            double dist = target.distanceToSqr(center);
            if (dist < minDist) {
                minDist = dist;
                nearest = center;
            }
        }

        return nearest;
    }

    public static int getSiphonStacks(UUID playerUUID) {
        SiphonData data = PLAYER_SIPHON_DATA.get(playerUUID);
        return data != null ? data.stacks : 0;
    }

    public static boolean hasSiphonShieldType(UUID playerUUID) {
        return ShieldTypeManager.hasActiveShieldType(playerUUID, "siphon");
    }

    /**
     * 获取虹吸伤害追踪Map中目标对应的玩家UUID，供SiphonDamageListener使用
     */
    public static UUID getSiphonPlayerUUID(UUID targetUUID) {
        return SIPHON_TARGET_TO_PLAYER.get(targetUUID);
    }

    public static void clearPlayerData(UUID playerUUID) {
        SiphonData data = PLAYER_SIPHON_DATA.remove(playerUUID);
        if (data != null) {
            AttributeManager.removeDynamicAttribute(playerUUID, "siphon", "shield_effect_percent");
            AttributeManager.removeDynamicAttribute(playerUUID, "siphon", "shield_effect_radius");
        }
    }

    public static void clearAllData() {
        for (UUID uuid : PLAYER_SIPHON_DATA.keySet()) {
            AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_percent");
            AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_radius");
        }
        PLAYER_SIPHON_DATA.clear();
    }
}
