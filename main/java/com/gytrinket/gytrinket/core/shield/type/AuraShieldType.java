package com.gytrinket.gytrinket.core.shield.type;
import com.gytrinket.gytrinket.core.defs.DefsQueries;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.ShieldValueDefs;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.burn.BurnManager;
import com.gytrinket.gytrinket.core.burn.IBurnSource;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.ignite.IIgniteSource;
import com.gytrinket.gytrinket.core.ignite.IgniteManager;
import com.gytrinket.gytrinket.core.projectile.MultipartHitScan;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

public class AuraShieldType implements IShieldType {

    /** 实例状态分桶：键 = playerUUID + "|" + itemId（护盾类型数值每物品实例独立） */
    private static final Map<String, Boolean> AURA_DAMAGING = new HashMap<>();
    private static final Map<String, Integer> TICK_COUNTERS = new HashMap<>();

    /** 实例状态键：UUID 与 itemId 均不含 '|'，前缀匹配可安全清理某玩家全部实例 */
    private static String instanceKey(UUID playerUUID, String itemId) {
        return playerUUID + "|" + itemId;
    }

    /**
     * 实例级激活条件：该物品的光环护盾池实例存在且有池量。
     * 实例破裂（池量归零）或实例不存在（物品卸下/禁用）→ 效果关闭
     */
    private static boolean isInstancePoolEmpty(UUID playerUUID, String itemId) {
        return ShieldManager.isInstancePoolEmpty(playerUUID, itemId, "aura");
    }

    /** 物品实例取值：UI 覆盖优先，未覆盖回退 Config 默认值 */
    private static double valueFor(MinecraftServer server, String itemId, String paramKey, double configDefault) {
        return DefsQueries.resolveShieldTypeValueForItem(server, itemId, "aura", paramKey, configDefault);
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
        if (player.getLevel().isClientSide) return;
        UUID uuid = player.getUUID();
        String prefix = uuid + "|";
        AURA_DAMAGING.keySet().removeIf(k -> k.startsWith(prefix));
        TICK_COUNTERS.keySet().removeIf(k -> k.startsWith(prefix));
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendShieldSyncToPlayer(serverPlayer,
                ShieldManager.getCurrentShield(uuid), ShieldManager.getMaxShield(uuid));
        }
    }

    @Override
    public void onTick(Player player, ItemStack source) {
        if (player.getLevel().isClientSide) {
            return;
        }

        UUID uuid = player.getUUID();
        String itemId = Registry.ITEM.getKey(source.getItem()).toString();
        String key = instanceKey(uuid, itemId);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        double currentShield = ShieldManager.getCurrentShield(uuid);
        if (currentShield <= 0) {
            AURA_DAMAGING.put(key, false);
            TICK_COUNTERS.remove(key);
            return;
        }

        // [实例级闸门] 本实例护盾池量归零或实例不存在时：光环失活（破裂实例不再攻击敌人、不再经全局护盾扣费）
        if (isInstancePoolEmpty(uuid, itemId)) {
            AURA_DAMAGING.put(key, false);
            TICK_COUNTERS.remove(key);
            return;
        }

        int triggerFrequency = (int) valueFor(server, itemId, "trigger_frequency", ShieldValueDefs.defaultValueOf("aura", "trigger_frequency", 5.0));
        int tickCounter = TICK_COUNTERS.getOrDefault(key, 0) + 1;
        if (tickCounter < triggerFrequency) {
            TICK_COUNTERS.put(key, tickCounter);
            return;
        }
        TICK_COUNTERS.put(key, 0);

        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(uuid, player.getLevel());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(uuid, "shield_effect_radius");
        double baseRadius = valueFor(server, itemId, "radius", ShieldValueDefs.defaultValueOf("aura", "radius", 3.5));
        double radius = baseRadius * shieldEffectRadius;

        double chargeRate = valueFor(server, itemId, "damage", ShieldValueDefs.defaultValueOf("aura", "damage", 0.75));
        double baseShieldCost = valueFor(server, itemId, "shield_cost", ShieldValueDefs.defaultValueOf("aura", "shield_cost", 0.042));

        float totalShieldCost = 0;
        boolean hasEnemies = false;

        Map<LivingEntity, Float> burnChargeMap = new HashMap<>();
        Set<LivingEntity> igniteTargets = new HashSet<>();

        for (LivingEntity center : effectCenters) {
            if (center == null || !center.isAlive()) {
                continue;
            }

            AABB boundingBox = center.getBoundingBox().inflate(radius);
            // 多体节：体节不注册进 level 实体列表，只查父实体会漏掉伸出的身体节段；
            // 每个父实体只记一次（灼烧/点燃按生物结算）
            List<MultipartHitScan.Hit> hits = MultipartHitScan.collectInRegion(
                player.getLevel(), boundingBox, center.position(),
                entity -> isValidTarget((LivingEntity) entity, center),
                boundingBox::intersects,
                false);

            for (MultipartHitScan.Hit hit : hits) {
                LivingEntity target = hit.target();
                hasEnemies = true;
                double shieldEffect = AttributeManager.getGroupAttribute(uuid, "shield_effect");
                float charge = (float)(chargeRate * shieldEffect);

                burnChargeMap.merge(target, charge, Float::sum);
                igniteTargets.add(target);
            }

            if (!hits.isEmpty()) {
                totalShieldCost += baseShieldCost * hits.size();
            }
        }

        AURA_DAMAGING.put(key, hasEnemies);

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
            DamageSource shieldSelfDamage = ModDamageTypes.getShieldSelfDamageSource(player.getLevel());
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

    /** 客户端渲染查询：玩家任一 aura 实例处于攻击状态即为 true（多实例聚合） */
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
        String prefix = playerUUID + "|";
        AURA_DAMAGING.keySet().removeIf(k -> k.startsWith(prefix));
        TICK_COUNTERS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clearAllData() {
        AURA_DAMAGING.clear();
        TICK_COUNTERS.clear();
    }
}
