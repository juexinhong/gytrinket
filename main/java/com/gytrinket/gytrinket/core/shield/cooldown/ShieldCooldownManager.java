package com.gytrinket.gytrinket.core.shield.cooldown;

import com.gytrinket.gytrinket.core.attack_cooldown.AttackCooldownModifier;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.ShieldInstance;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.ShieldCooldownCompleteEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 护盾冷却管理：破盾实例逐实例推进冷却，冷却完成后回满该实例池量。
 * <p>
 * 攻击减速通过玩家级速度因子生效（进度增量 &lt;1，等效旧版 1.25 倍冷却时长），
 * 受击冷却延长见 {@link #applyHitExtension}。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldCooldownManager {

    private static final Map<UUID, Integer> PLAYER_TICK_COUNTER = new HashMap<>();
    private static final int PUSH_INTERVAL = 2;

    /** 玩家级冷却推进速度因子（攻击减速时 &lt;1），默认 1.0 */
    private static final Map<UUID, Double> SPEED_FACTOR = new HashMap<>();

    private static final List<IShieldCooldownModifier> MODIFIERS = new ArrayList<>();

    private ShieldCooldownManager() {}

    static {
        registerModifier(new AttackCooldownModifier());
    }

    public static void registerModifier(IShieldCooldownModifier modifier) {
        MODIFIERS.add(modifier);
        MODIFIERS.sort(Comparator.comparingInt(IShieldCooldownModifier::getPriority));
        gytrinket.LOGGER.info("注册冷却修饰器: {}", modifier.getName());
    }

    public static boolean unregisterModifier(String modifierName) {
        boolean removed = MODIFIERS.removeIf(m -> m.getName().equals(modifierName));
        if (removed) {
            gytrinket.LOGGER.info("移除冷却修饰器: {}", modifierName);
        }
        return removed;
    }

    /** 供修饰器设置每刻冷却推进速度因子 */
    public static void setSpeedFactor(UUID playerUUID, double speedFactor) {
        SPEED_FACTOR.put(playerUUID, Math.max(0, speedFactor));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        UUID playerUUID = player.getUUID();

        if (!ShieldManager.hasCoolingInstances(playerUUID)) {
            SPEED_FACTOR.remove(playerUUID);
            return;
        }

        CooldownContext context = createContext(playerUUID);
        for (IShieldCooldownModifier modifier : MODIFIERS) {
            modifier.onPreTick(context);
        }

        double speedFactor = SPEED_FACTOR.getOrDefault(playerUUID, 1.0);

        boolean anyCompleted = false;
        for (ShieldInstance instance : ShieldManager.getInstances(playerUUID)) {
            if (!instance.isCoolingDown()) {
                continue;
            }
            instance.tickCooldown(speedFactor);
            if (!instance.isCoolingDown()) {
                instance.completeCooldown();
                anyCompleted = true;
            }
        }

        for (IShieldCooldownModifier modifier : MODIFIERS) {
            modifier.onPostTick(context);
        }

        if (anyCompleted) {
            ShieldManager.afterInstanceMutation(playerUUID, null);
            if (!ShieldManager.hasCoolingInstances(playerUUID)) {
                NeoForge.EVENT_BUS.post(new ShieldCooldownCompleteEvent(playerUUID));
            }
        }

        // 冷却进度推送防抖
        int tickCounter = PLAYER_TICK_COUNTER.getOrDefault(playerUUID, 0) + 1;
        PLAYER_TICK_COUNTER.put(playerUUID, tickCounter);
        if (tickCounter >= PUSH_INTERVAL) {
            PLAYER_TICK_COUNTER.put(playerUUID, 0);
            syncCooldownToClient(playerUUID);
        }
    }

    /**
     * 受击冷却延长：对全部冷却中的实例按伤害扣除冷却进度。
     * <p>
     * extend = shield_hit_cooldown_extend（物品 shieldValues → 护盾类型默认值 →
     * 玩家全局聚合 fallback）× 组乘子；multiplier = shield_hit_cooldown_extend_multiplier 同链。
     */
    public static void applyHitExtension(UUID playerUUID, float damage) {
        List<ShieldInstance> coolingInstances = new ArrayList<>();
        for (ShieldInstance instance : ShieldManager.getInstances(playerUUID)) {
            if (instance.isCoolingDown()) {
                coolingInstances.add(instance);
            }
        }
        if (coolingInstances.isEmpty()) {
            return;
        }

        double extendMultiplier = AttributeManager.getGroupMultiplier(playerUUID, "shield_hit_cooldown_extend");
        double multMultiplier = AttributeManager.getGroupMultiplier(playerUUID, "shield_hit_cooldown_extend_multiplier");
        float finalMultiplier = (float) AttributeManager.getPlayerAttribute(playerUUID, "shield_hit_cooldown_extend_final_multiplier");

        for (ShieldInstance instance : coolingInstances) {
            String itemId = instance.getItemId();
            double extend = DefsManager.resolveShieldParam(ServerLifecycleHooks.getCurrentServer(), itemId, instance.getShieldTypeName(),
                    "shield_hit_cooldown_extend",
                    AttributeManager.getPlayerAttribute(playerUUID, "shield_hit_cooldown_extend")) * extendMultiplier;
            double multiplier = DefsManager.resolveShieldParam(ServerLifecycleHooks.getCurrentServer(), itemId, instance.getShieldTypeName(),
                    "shield_hit_cooldown_extend_multiplier",
                    AttributeManager.getPlayerAttribute(playerUUID, "shield_hit_cooldown_extend_multiplier")) * multMultiplier;

            // 伤害<=1 时延长值衰减：原值<5 取原值，否则取 5，再减 35
            if (damage <= 1.0f) {
                extend = Math.max(extend < 5 ? extend : 5, extend - 35);
            }

            if (extend == 0) {
                continue;
            }

            double factor = Math.max(1.0, 1.0 + Math.max(0, damage - 1) * (multiplier - 1));
            int reduction = (int) (factor * extend * finalMultiplier);
            if (reduction <= 0) {
                continue;
            }

            instance.setCooldownProgress(Math.max(0, instance.getCooldownProgress() - reduction));
        }
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (attrName.equals("shield_cooldown_reduction_percent") ||
            attrName.equals("shield_cooldown_reduction_independent") ||
            attrName.equals("recovery_efficiency_percent") ||
            attrName.equals("recovery_efficiency_independent")) {

            recalcCooldowns(playerUUID);
            syncCooldownToClient(playerUUID);
        }
    }

    /** 冷却缩减属性变化：各实例按新上限折算进度 */
    private static void recalcCooldowns(UUID playerUUID) {
        List<ShieldInstance> instances = ShieldManager.getInstances(playerUUID);
        for (ShieldInstance instance : instances) {
            int newMax = ShieldManager.computeMaxCooldown(playerUUID, instance.getItemId(), instance.getShieldTypeName());
            instance.updateMaxCooldown(newMax);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        syncCooldownToClient(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayerCooldown(event.getEntity().getUUID());
    }

    private static CooldownContext createContext(UUID playerUUID) {
        return new CooldownContext(
                playerUUID,
                ShieldManager.getCurrentShield(playerUUID),
                ShieldManager.getMaxShield(playerUUID)
        );
    }

    private static void syncCooldownToClient(UUID playerUUID) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                NetworkHandler.sendShieldSyncToPlayer(
                        player,
                        ShieldManager.getCurrentShield(playerUUID),
                        ShieldManager.getMaxShield(playerUUID)
                );
            }
        }
    }

    /** HUD 显示：第一个冷却中实例的进度 */
    public static int getCurrentCooldown(UUID playerUUID) {
        for (ShieldInstance instance : ShieldManager.getInstances(playerUUID)) {
            if (instance.isCoolingDown()) {
                return instance.getCurrentCooldown();
            }
        }
        return 0;
    }

    /** HUD 显示：第一个冷却中实例的上限 */
    public static int getMaxCooldown(UUID playerUUID) {
        for (ShieldInstance instance : ShieldManager.getInstances(playerUUID)) {
            if (instance.isCoolingDown()) {
                return instance.getMaxCooldown();
            }
        }
        return 0;
    }

    public static void clearPlayerCooldown(UUID playerUUID) {
        PLAYER_TICK_COUNTER.remove(playerUUID);
        SPEED_FACTOR.remove(playerUUID);
    }

    public static void clearAllCooldowns() {
        PLAYER_TICK_COUNTER.clear();
        SPEED_FACTOR.clear();
    }
}
