package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.damage.DamageAttributionWindow;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

public class SiphonShieldType implements IShieldType {

    /** 虹吸状态：键 = playerUUID + "|" + itemId（每物品实例独立；同物品多件共享同一实例状态） */
    private static final Map<String, SiphonData> PLAYER_SIPHON_DATA = new HashMap<>();

    // 方案B追踪Map：目标UUID → (玩家UUID, 物品id)，用于SiphonDamageListener按实例取 heal_ratio
    private static final Map<UUID, SiphonTargetRef> SIPHON_TARGET_TO_PLAYER = new HashMap<>();

    /** 物品实例状态键（UUID 与 itemId 均不含 '|'，可安全拼接） */
    private static String instanceKey(UUID playerUUID, ItemStack source) {
        return playerUUID + "|" + BuiltInRegistries.ITEM.getKey(source.getItem());
    }

    /** 虹吸伤害归属引用：玩家 UUID + 提供虹吸的物品 id（数值按该物品实例取） */
    public record SiphonTargetRef(UUID playerUUID, String itemId) {}

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
        PLAYER_SIPHON_DATA.keySet().removeIf(key -> key.startsWith(uuid + "|"));
        AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_percent");
        AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_radius");
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(uuid), ShieldManager.getMaxShield(uuid));
        }
    }

    @Override
    public void onTick(Player player, ItemStack source) {
        if (player.level().isClientSide) return;
        double currentShield = ShieldManager.getCurrentShield(player.getUUID());
        if (currentShield <= 0) return;

        UUID uuid = player.getUUID();
        String itemKey = instanceKey(uuid, source);
        String itemId = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        SiphonData data = PLAYER_SIPHON_DATA.computeIfAbsent(itemKey, k -> new SiphonData());

        data.tickCounter++;
        int tickInterval = (int) DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                "siphon", "tick_interval", Config.SIPHON_TICK_INTERVAL.get());
        if (data.tickCounter >= tickInterval) {
            data.tickCounter = 0;
            performSiphon(player, data, itemId);
        }

        updateSiphonDecay(data, itemId);
        updateSiphonAttributes(uuid);

        if (data.stacks != data.lastSyncedStacks) {
            data.lastSyncedStacks = data.stacks;
            syncSiphonStacksToClient(player, data.stacks);
        }
    }

    private void performSiphon(Player player, SiphonData data, String itemId) {
        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double baseRadius = DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                "siphon", "radius", Config.SIPHON_RADIUS.get());
        double radius = baseRadius * shieldEffectRadius;

        double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
        double baseDamage = DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                "siphon", "damage", Config.SIPHON_DAMAGE.get());
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

                // 归属不再由伤害前预判（原始伤害会被护甲/免伤削减导致误判）：
                // 伤害源恒不带玩家，致死归属由 ExecuteAttributionHandler
                // 在施加窗口内按 LivingDeathEvent 实际致死结果判定
                DamageSource siphonSource = ModDamageTypes.getSiphonDamageSource(player.level(), null);
                DamageAttributionWindow.mark(target, player);

                // 记录目标→(玩家,物品)映射，供SiphonDamageListener按实例取值
                SIPHON_TARGET_TO_PLAYER.put(target.getUUID(), new SiphonTargetRef(player.getUUID(), itemId));

                try {
                    target.hurt(siphonSource, damagePerTarget);
                } finally {
                    DamageAttributionWindow.unmark(target);
                }
                target.invulnerableTime = 0;  //这个决定不能删除!害我找半天哪里有问题.

                // hurt()同步执行，监听器已处理完毕，移除追踪
                SIPHON_TARGET_TO_PLAYER.remove(target.getUUID());

                sendSiphonParticles(player, target);
            }

            data.stacks++;
            data.remainingTicks = (int) DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                    "siphon", "duration_ticks", Config.SIPHON_DURATION_TICKS.get());
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

    private void updateSiphonDecay(SiphonData data, String itemId) {
        if (data.stacks <= 0) return;

        if (!data.decaying) {
            data.remainingTicks--;
            if (data.remainingTicks <= 0) {
                data.decaying = true;
            }
        }

        if (data.decaying) {
            double decayRatio = DefsManager.resolveShieldTypeValueForItem(
                    ServerLifecycleHooks.getCurrentServer(), itemId,
                    "siphon", "decay_ratio", Config.SIPHON_DECAY_RATIO.get());
            int decayAmount = 1 + (int) Math.floor(data.stacks * decayRatio);
            data.stacks -= decayAmount;
            if (data.stacks <= 0) {
                data.stacks = 0;
                data.decaying = false;
                data.remainingTicks = 0;
            }
        }
    }

    /**
     * 重算玩家虹吸动态属性：聚合该玩家全部 siphon 实例的层数加成
     * （动态属性命名空间为单值，多实例并存时写入总和）。
     */
    private void updateSiphonAttributes(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        String prefix = uuid + "|";
        double shieldEffectBonus = 0;
        double shieldEffectRadiusBonus = 0;
        for (var e : PLAYER_SIPHON_DATA.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            SiphonData data = e.getValue();
            if (data.stacks <= 0) continue;
            String instItemId = e.getKey().substring(prefix.length());
            double effectPerStack = DefsManager.resolveShieldTypeValueForItem(server, instItemId,
                    "siphon", "effect_per_stack", Config.SIPHON_EFFECT_PER_STACK.get());
            double maxEffect = DefsManager.resolveShieldTypeValueForItem(server, instItemId,
                    "siphon", "max_effect", Config.SIPHON_MAX_EFFECT.get());
            shieldEffectBonus += Math.min(data.stacks * effectPerStack, maxEffect);
            shieldEffectRadiusBonus += Math.min(data.stacks * effectPerStack, maxEffect);
        }

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

    /** 玩家全部 siphon 实例的层数总和（显示/HUD 用） */
    public static int getSiphonStacks(UUID playerUUID) {
        int total = 0;
        String prefix = playerUUID + "|";
        for (var e : PLAYER_SIPHON_DATA.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                total += e.getValue().stacks;
            }
        }
        return total;
    }

    /** 单实例查询：该物品实例的虹吸层数 */
    public static int getSiphonStacksForItem(UUID playerUUID, String itemId) {
        SiphonData data = PLAYER_SIPHON_DATA.get(playerUUID + "|" + itemId);
        return data != null ? data.stacks : 0;
    }

    public static boolean hasSiphonShieldType(UUID playerUUID) {
        return ShieldTypeManager.hasActiveShieldType(playerUUID, "siphon");
    }

    /** 获取完整归属引用（玩家 + 物品实例），供 SiphonDamageListener 按实例取 heal_ratio */
    public static SiphonTargetRef getSiphonTargetRef(UUID targetUUID) {
        return SIPHON_TARGET_TO_PLAYER.get(targetUUID);
    }

    public static void clearPlayerData(UUID playerUUID) {
        boolean removed = PLAYER_SIPHON_DATA.keySet().removeIf(key -> key.startsWith(playerUUID + "|"));
        if (removed) {
            AttributeManager.removeDynamicAttribute(playerUUID, "siphon", "shield_effect_percent");
            AttributeManager.removeDynamicAttribute(playerUUID, "siphon", "shield_effect_radius");
        }
    }

    public static void clearAllData() {
        Set<UUID> uuids = new HashSet<>();
        for (String key : PLAYER_SIPHON_DATA.keySet()) {
            int idx = key.indexOf('|');
            if (idx > 0) {
                try {
                    uuids.add(UUID.fromString(key.substring(0, idx)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (UUID uuid : uuids) {
            AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_percent");
            AttributeManager.removeDynamicAttribute(uuid, "siphon", "shield_effect_radius");
        }
        PLAYER_SIPHON_DATA.clear();
    }
}
