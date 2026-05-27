package com.gy_mod.gy_trinket.core.shield;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.core.shield.type.IShieldType;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.event.ShieldBreakEvent;
import com.gy_mod.gy_trinket.event.ShieldCooldownCompleteEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldManager {

    private static final Map<UUID, ShieldData> SHIELD_MAP = new HashMap<>();

    private static final String NBT_CURRENT_SHIELD = "gy_trinket.current_shield";
    private static final String NBT_MAX_SHIELD = "gy_trinket.max_shield";

    private ShieldManager() {}

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        
        boolean hasActiveShieldType = ShieldTypeManager.getPlayerShieldTypes(playerUUID).stream()
                .anyMatch(IShieldType.ShieldTypeData::active);
        
        double newMaxShield = hasActiveShieldType ? AttributeManager.getGroupAttribute(playerUUID, "shield") : 0.0;

        ShieldData data = SHIELD_MAP.get(playerUUID);
        if (data != null) {
            double oldCurrent = data.getCurrentShield();
            data.updateMaxShield(newMaxShield);
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        } else {
            SHIELD_MAP.put(playerUUID, new ShieldData(newMaxShield));
            syncShieldToClient(playerUUID, 0, newMaxShield);
        }
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (attrName.equals("shield_base") || 
            attrName.equals("shield_percent") || 
            attrName.equals("shield_independent")) {
            
            boolean hasActiveShieldType = ShieldTypeManager.getPlayerShieldTypes(playerUUID).stream()
                    .anyMatch(IShieldType.ShieldTypeData::active);
            
            double newMaxShield = hasActiveShieldType ? AttributeManager.getGroupAttribute(playerUUID, "shield") : 0.0;

            ShieldData data = SHIELD_MAP.get(playerUUID);
            if (data != null) {
                data.updateMaxShield(newMaxShield);
                syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
            }
        }
    }

    @SubscribeEvent
    public static void onCooldownComplete(ShieldCooldownCompleteEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        ShieldData data = SHIELD_MAP.get(playerUUID);

        if (data != null) {
            data.setCurrentShield(data.getMaxShield());
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }

        var cooldownData = ShieldCooldownManager.getCooldownData(playerUUID);
        if (cooldownData != null) {
            cooldownData.reset();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        CompoundTag playerData = player.getPersistentData();

        double currentShield = 0;
        double maxShield = 0;

        if (playerData.contains(NBT_CURRENT_SHIELD)) {
            currentShield = playerData.getDouble(NBT_CURRENT_SHIELD);
        }
        if (playerData.contains(NBT_MAX_SHIELD)) {
            maxShield = playerData.getDouble(NBT_MAX_SHIELD);
        }

        if (maxShield > 0) {
            SHIELD_MAP.put(playerUUID, new ShieldData(currentShield, maxShield));
            syncShieldToClient(playerUUID, currentShield, maxShield);
            gytrinket.LOGGER.debug("玩家 {} 登录，加载护盾数据: 当前={}, 最大={}", playerUUID, currentShield, maxShield);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        ShieldData data = SHIELD_MAP.get(playerUUID);

        // 清除无敌状态，防止无敌永驻
        if (player.isInvulnerable()) {
            player.setInvulnerable(false);
        }

        if (data != null) {
            CompoundTag playerData = player.getPersistentData();
            playerData.putDouble(NBT_CURRENT_SHIELD, data.getCurrentShield());
            playerData.putDouble(NBT_MAX_SHIELD, data.getMaxShield());
            gytrinket.LOGGER.debug("玩家 {} 退出，保存护盾数据: 当前={}, 最大={}", playerUUID, data.getCurrentShield(), data.getMaxShield());
        }

        clearShieldData(playerUUID);
    }

    /**
     * 设置玩家当前护盾值
     * <p>
     * 当护盾值从大于0降低至0时，触发 ShieldBreakEvent 事件。
     *
     * @param playerUUID     玩家UUID
     * @param currentShield  新的当前护盾值
     */
    public static void setCurrentShield(UUID playerUUID, double currentShield) {
        ShieldData data = SHIELD_MAP.get(playerUUID);
        if (data != null) {
            double oldShield = data.getCurrentShield();
            double newShield = Math.max(0, Math.min(currentShield, data.getMaxShield()));

            data.setCurrentShield(newShield);

            if (oldShield > 0 && newShield <= 0) {
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                    if (player != null) {
                        MinecraftForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldShield));
                    }
                }
            }

            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }
    }

    /**
     * 设置玩家当前护盾值
     * <p>
     * 当护盾值从大于0降低至0时，触发 ShieldBreakEvent 事件。
     *
     * @param player         玩家对象
     * @param currentShield  新的当前护盾值
     */
    public static void setCurrentShield(ServerPlayer player, double currentShield) {
        UUID playerUUID = player.getUUID();
        ShieldData data = SHIELD_MAP.get(playerUUID);
        if (data != null) {
            double oldShield = data.getCurrentShield();
            double newShield = Math.max(0, Math.min(currentShield, data.getMaxShield()));

            data.setCurrentShield(newShield);

            if (oldShield > 0 && newShield <= 0) {
                MinecraftForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldShield));
            }

            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }
    }

    /**
     * 增加玩家护盾值
     * <p>
     * 增加后的护盾值不会超过最大护盾值。
     * 当护盾值从0增加至大于0时，不会触发冷却完成事件。
     *
     * @param playerUUID  玩家UUID
     * @param amount     要增加的护盾值（可以为负数表示减少）
     */
    public static void addShield(UUID playerUUID, double amount) {
        ShieldData data = SHIELD_MAP.get(playerUUID);
        if (data != null) {
            double oldShield = data.getCurrentShield();
            double maxShield = data.getMaxShield();
            double newShield = Math.max(0, Math.min(oldShield + amount, maxShield));

            data.setCurrentShield(newShield);
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }
    }

    public static void updateShieldData(UUID playerUUID, double maxShield) {
        ShieldData data = SHIELD_MAP.get(playerUUID);
        if (data != null) {
            data.updateMaxShield(maxShield);
        } else {
            SHIELD_MAP.put(playerUUID, new ShieldData(maxShield));
        }
    }

    public static void updateShieldData(UUID playerUUID, double currentShield, double maxShield) {
        SHIELD_MAP.put(playerUUID, new ShieldData(currentShield, maxShield));
    }

    private static void syncShieldToClient(UUID playerUUID, double currentShield, double maxShield) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                NetworkHandler.sendShieldSyncToPlayer(player, currentShield, maxShield);
            }
        }
    }

    public static ShieldData getShieldData(UUID playerUUID) {
        return SHIELD_MAP.get(playerUUID);
    }

    public static double getCurrentShield(UUID playerUUID) {
        ShieldData data = getShieldData(playerUUID);
        return data != null ? data.getCurrentShield() : 0.0;
    }

    public static double getMaxShield(UUID playerUUID) {
        ShieldData data = getShieldData(playerUUID);
        return data != null ? data.getMaxShield() : 0.0;
    }

    public static void updateMaxShield(UUID playerUUID, double newMaxShield) {
        ShieldData data = SHIELD_MAP.get(playerUUID);
        if (data != null) {
            data.updateMaxShield(newMaxShield);
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        } else {
            SHIELD_MAP.put(playerUUID, new ShieldData(newMaxShield));
            syncShieldToClient(playerUUID, 0, newMaxShield);
        }
    }

    public static void clearShieldData(UUID playerUUID) {
        SHIELD_MAP.remove(playerUUID);
    }

    public static void clearAllShieldData() {
        SHIELD_MAP.clear();
    }
}