package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.burn.BurnManager;
import com.gytrinket.gytrinket.core.burn.IBurnSource;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.ignite.IIgniteSource;
import com.gytrinket.gytrinket.core.ignite.IgniteManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class AuraShieldType implements IShieldType {

    /** 实例状态分桶：键 = playerUUID + "|" + itemId（护盾类型数值每物品实例独立） */
    private static final Map<String, Boolean> AURA_DAMAGING = new HashMap<>();
    /** tick 计数：键 = playerUUID + "|" + itemId（每物品实例独立计数；同物品多件共享计数，数值相同等效叠加触发） */
    private static final Map<String, Integer> TICK_COUNTERS = new HashMap<>();

    /** 实例状态键：UUID 与 itemId 均不含 '|'，前缀匹配可安全清理某玩家全部实例 */
    private static String instanceKey(UUID playerUUID, String itemId) {
        return playerUUID + "|" + itemId;
    }

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
        AURA_DAMAGING.keySet().removeIf(key -> key.startsWith(uuid + "|"));
        TICK_COUNTERS.keySet().removeIf(key -> key.startsWith(uuid + "|"));
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(uuid), ShieldManager.getMaxShield(uuid));
        }
    }

    @Override
    public void onTick(Player player, ItemStack source) {
        if (player.level().isClientSide) {
            return;
        }

        UUID uuid = player.getUUID();
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        String itemKey = instanceKey(uuid, itemId);

        double currentShield = ShieldManager.getCurrentShield(uuid);
        if (currentShield <= 0) {
            AURA_DAMAGING.put(itemKey, false);
            TICK_COUNTERS.remove(itemKey);
            return;
        }

        int tickCounter = TICK_COUNTERS.getOrDefault(itemKey, 0) + 1;
        int triggerFrequency = (int) DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                "aura", "trigger_frequency", Config.AURA_TRIGGER_FREQUENCY.get());
        if (tickCounter < triggerFrequency) {
            TICK_COUNTERS.put(itemKey, tickCounter);
            return;
        }
        TICK_COUNTERS.put(itemKey, 0);

        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double baseRadius = DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                "aura", "radius", Config.AURA_RADIUS.get());
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
                double chargeRate = DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                        "aura", "damage", Config.AURA_DAMAGE.get());
                double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
                float charge = (float)(chargeRate * shieldEffect);
                
                burnChargeMap.merge(target, charge, Float::sum);
                igniteTargets.add(target);
            }

            if (!entities.isEmpty()) {
                double baseShieldCost = DefsManager.resolveShieldTypeValueForItem(player.getServer(), itemId,
                        "aura", "shield_cost", Config.AURA_SHIELD_COST.get());
                totalShieldCost += baseShieldCost * entities.size();
            }
        }

        AURA_DAMAGING.put(itemKey, hasEnemies);

        // 同步auraDamaging到客户端：光环渲染贴图透明度由客户端damaging状态驱动，
        // 若不同步，客户端10刻确认超时后会将damaging置false，导致纯光环攻击时贴图不显示。
        // 光环触发频率默认5刻（< 客户端超时10刻），每次触发同步即可保持贴图可见。
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(uuid), ShieldManager.getMaxShield(uuid));
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
            Player owner = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                return HostileTargetManager.shouldAttackPlayer(entity, owner);
            }
        }

        return false;
    }

    /** 玩家全部 aura 实例任一处于攻击状态（HUD/聚合显示用） */
    public static boolean isAuraDamaging(UUID playerUUID) {
        String prefix = playerUUID + "|";
        for (Map.Entry<String, Boolean> e : AURA_DAMAGING.entrySet()) {
            if (e.getKey().startsWith(prefix) && Boolean.TRUE.equals(e.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** 单实例查询：仅查询该物品实例的攻击状态（同步/渲染按实例独立） */
    public static boolean isAuraDamagingForItem(UUID playerUUID, String itemId) {
        return AURA_DAMAGING.getOrDefault(instanceKey(playerUUID, itemId), false);
    }

    public static void clearPlayerData(UUID playerUUID) {
        AURA_DAMAGING.remove(playerUUID);
        TICK_COUNTERS.keySet().removeIf(key -> key.startsWith(playerUUID + "|"));
    }

    public static void clearAllData() {
        AURA_DAMAGING.clear();
        TICK_COUNTERS.clear();
    }
}
