package com.gytrinket.gytrinket.core.shield.cooldown;

import com.gytrinket.gytrinket.core.attack_cooldown.AttackCooldownModifier;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.event.ShieldCooldownCompleteEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldCooldownManager {

    private static final Map<UUID, CooldownData> COOLDOWN_MAP = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_TICK_COUNTER = new HashMap<>();
    public static final Map<UUID, Integer> BASE_MAX_COOLDOWN = new HashMap<>();
    private static final int PUSH_INTERVAL = 2;

    private static final List<IShieldCooldownModifier> MODIFIERS = new ArrayList<>();

    private ShieldCooldownManager() {}

    static {
        registerModifier(new DamageReductionModifier());
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

    public static CooldownData getCooldownData(UUID playerUUID) {
        return COOLDOWN_MAP.get(playerUUID);
    }

    public static List<IShieldCooldownModifier> getModifiers() {
        return Collections.unmodifiableList(MODIFIERS);
    }

    public static CooldownContext createContext(UUID playerUUID) {
        return new CooldownContext(
            playerUUID,
            ShieldManager.getCurrentShield(playerUUID),
            ShieldManager.getMaxShield(playerUUID)
        );
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        Map<String, Double> attributes = event.getAttributes();

        double cooldownTime = attributes.getOrDefault("shield_cooldown_time", 0.0);
        double cooldownReduction = AttributeManager.getGroupAttribute(playerUUID, "shield_cooldown_reduction");
        double finalCooldownTime = cooldownTime * (1.0 / cooldownReduction);

        int baseMaxCooldown = (int) (finalCooldownTime * 20);
        BASE_MAX_COOLDOWN.put(playerUUID, baseMaxCooldown);

        CooldownData data = COOLDOWN_MAP.get(playerUUID);
        if (data != null) {
            data.updateMaxCooldown(baseMaxCooldown);
        } else {
            COOLDOWN_MAP.put(playerUUID, new CooldownData(baseMaxCooldown));
        }

        syncCooldownToClient(playerUUID);
    }

    /**
     * 监听属性动态变化事件
     * 当护盾冷却缩减属性组变化时，重新计算基础冷却时间
     */
    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        
        String attrName = event.getAttributeName();
        if (attrName.equals("shield_cooldown_reduction_percent") || 
            attrName.equals("shield_cooldown_reduction_independent") ||
            attrName.equals("recovery_efficiency_percent") ||
            attrName.equals("recovery_efficiency_independent")) {
            
            double cooldownTime = AttributeManager.getPlayerAttribute(playerUUID, "shield_cooldown_time");
            double cooldownReduction = AttributeManager.getGroupAttribute(playerUUID, "shield_cooldown_reduction");
            double finalCooldownTime = cooldownTime * (1.0 / cooldownReduction);

            int baseMaxCooldown = (int) (finalCooldownTime * 20);
            BASE_MAX_COOLDOWN.put(playerUUID, baseMaxCooldown);

            CooldownData data = COOLDOWN_MAP.get(playerUUID);
            if (data != null) {
                data.updateMaxCooldown(baseMaxCooldown);
            }

            syncCooldownToClient(playerUUID);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        CooldownData data = COOLDOWN_MAP.get(playerUUID);
        if (data == null) {
            data = new CooldownData(0);
            COOLDOWN_MAP.put(playerUUID, data);
        }

        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        double maxShield = ShieldManager.getMaxShield(playerUUID);
        CooldownContext context = createContext(playerUUID);

        if (currentShield >= maxShield && maxShield > 0) {
            int oldCooldown = data.getCurrentCooldown();
            data.reset();
            if (oldCooldown > 0) {
                syncCooldownToClient(playerUUID);
            }
            return;
        }

        if (currentShield < maxShield && maxShield > 0 && !data.isComplete()) {
            boolean wasComplete = data.isComplete();
            int oldCooldown = data.getCurrentCooldown();

            boolean skipDefault = false;
            for (IShieldCooldownModifier modifier : MODIFIERS) {
                skipDefault |= modifier.onPreTick(data, context);
            }

            if (!skipDefault) {
                data.tick();
            }

            for (IShieldCooldownModifier modifier : MODIFIERS) {
                modifier.onPostTick(data, context);
            }

            if (data.getCurrentCooldown() != oldCooldown) {
                int tickCounter = PLAYER_TICK_COUNTER.getOrDefault(playerUUID, 0) + 1;
                PLAYER_TICK_COUNTER.put(playerUUID, tickCounter);

                if (tickCounter >= PUSH_INTERVAL) {
                    PLAYER_TICK_COUNTER.put(playerUUID, 0);
                    syncCooldownToClient(playerUUID);
                }
            }

            if (!wasComplete && data.isComplete()) {
                for (IShieldCooldownModifier modifier : MODIFIERS) {
                    modifier.onCooldownComplete(data, context);
                }
                NeoForge.EVENT_BUS.post(new ShieldCooldownCompleteEvent(playerUUID));
                syncCooldownToClient(playerUUID);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onShieldBreak(ShieldBreakEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        CooldownData data = COOLDOWN_MAP.get(playerUUID);

        if (data != null) {
            CooldownContext context = createContext(playerUUID);
            for (IShieldCooldownModifier modifier : MODIFIERS) {
                modifier.onShieldBreak(data, context);
            }
            data.reset();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        UUID playerUUID = event.getEntity().getUUID();
        syncCooldownToClient(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        clearPlayerCooldown(playerUUID);
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

    public static boolean isCooldownComplete(UUID playerUUID) {
        CooldownData data = COOLDOWN_MAP.get(playerUUID);
        return data != null && data.isComplete();
    }

    public static int getCurrentCooldown(UUID playerUUID) {
        CooldownData data = COOLDOWN_MAP.get(playerUUID);
        return data != null ? data.getCurrentCooldown() : 0;
    }

    public static int getMaxCooldown(UUID playerUUID) {
        CooldownData data = COOLDOWN_MAP.get(playerUUID);
        return data != null ? data.getMaxCooldown() : 0;
    }

    public static void clearPlayerCooldown(UUID playerUUID) {
        COOLDOWN_MAP.remove(playerUUID);
        PLAYER_TICK_COUNTER.remove(playerUUID);
        BASE_MAX_COOLDOWN.remove(playerUUID);
    }

    public static void clearAllCooldowns() {
        COOLDOWN_MAP.clear();
        PLAYER_TICK_COUNTER.clear();
        BASE_MAX_COOLDOWN.clear();
    }

    public static class CooldownData {
        private int currentCooldown;
        private int maxCooldown;

        public CooldownData(int maxCooldown) {
            this.maxCooldown = maxCooldown;
            this.currentCooldown = 0;
        }

        public boolean isComplete() {
            return maxCooldown > 0 && currentCooldown >= maxCooldown;
        }

        public void tick() {
            if (currentCooldown < maxCooldown) {
                currentCooldown++;
            }
        }

        public void reset() {
            this.currentCooldown = 0;
        }

        public void updateMaxCooldown(int newMaxCooldown) {
            if (maxCooldown > 0 && newMaxCooldown > 0) {
                float ratio = (float) currentCooldown / maxCooldown;
                currentCooldown = (int) (newMaxCooldown * ratio);
            }
            this.maxCooldown = newMaxCooldown;
        }

        public int getCurrentCooldown() {
            return currentCooldown;
        }

        public int getMaxCooldown() {
            return maxCooldown;
        }

        public void setCurrentCooldown(int currentCooldown) {
            this.currentCooldown = currentCooldown;
        }
    }
}
