package com.gytrinket.gytrinket.core.shield;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;
import com.gytrinket.gytrinket.core.shield.type.IShieldType;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.event.ShieldCooldownCompleteEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldManager {

    private static final String SLOT_KEY = "shield";

    private ShieldManager() {}

    private static ShieldData getOrCreate(UUID playerUUID) {
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (data == null) {
            data = new ShieldData(0);
            PlayerDataCenter.setData(playerUUID, SLOT_KEY, data);
        }
        return data;
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        boolean hasActiveShieldType = ShieldTypeManager.getPlayerShieldTypes(playerUUID).stream()
                .anyMatch(IShieldType.ShieldTypeData::active);

        double newMaxShield = hasActiveShieldType ? AttributeManager.getGroupAttribute(playerUUID, "shield") : 0.0;

        ShieldData data = getOrCreate(playerUUID);
        data.updateMaxShield(newMaxShield);
        syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
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

            ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
            if (data != null) {
                data.updateMaxShield(newMaxShield);
                syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
            }
        }
    }

    @SubscribeEvent
    public static void onCooldownComplete(ShieldCooldownCompleteEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);

        if (data != null) {
            data.setCurrentShield(data.getMaxShield());
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }

        var cooldownData = ShieldCooldownManager.getCooldownData(playerUUID);
        if (cooldownData != null) {
            cooldownData.reset();
        }
    }

    public static void setCurrentShield(UUID playerUUID, double currentShield) {
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (data != null) {
            double oldShield = data.getCurrentShield();
            double newShield = Math.max(0, Math.min(currentShield, data.getMaxShield()));

            data.setCurrentShield(newShield);

            if (oldShield > 0 && newShield <= 0) {
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                    if (player != null) {
                        NeoForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldShield));
                    }
                }
            }

            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }
    }

    public static void setCurrentShield(ServerPlayer player, double currentShield) {
        UUID playerUUID = player.getUUID();
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (data != null) {
            double oldShield = data.getCurrentShield();
            double newShield = Math.max(0, Math.min(currentShield, data.getMaxShield()));

            data.setCurrentShield(newShield);

            if (oldShield > 0 && newShield <= 0) {
                NeoForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldShield));
            }

            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }
    }

    public static void addShield(UUID playerUUID, double amount) {
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (data != null) {
            double oldShield = data.getCurrentShield();
            double maxShield = data.getMaxShield();
            double newShield = Math.max(0, Math.min(oldShield + amount, maxShield));

            data.setCurrentShield(newShield);
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        }
    }

    public static void updateShieldData(UUID playerUUID, double maxShield) {
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (data != null) {
            data.updateMaxShield(maxShield);
        } else {
            PlayerDataCenter.setData(playerUUID, SLOT_KEY, new ShieldData(maxShield));
        }
    }

    public static void updateShieldData(UUID playerUUID, double currentShield, double maxShield) {
        PlayerDataCenter.setData(playerUUID, SLOT_KEY, new ShieldData(currentShield, maxShield));
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
        return PlayerDataCenter.getData(playerUUID, SLOT_KEY);
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
        ShieldData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        if (data != null) {
            data.updateMaxShield(newMaxShield);
            syncShieldToClient(playerUUID, data.getCurrentShield(), data.getMaxShield());
        } else {
            PlayerDataCenter.setData(playerUUID, SLOT_KEY, new ShieldData(newMaxShield));
            syncShieldToClient(playerUUID, 0, newMaxShield);
        }
    }

    public static void clearShieldData(UUID playerUUID) {
        PlayerDataCenter.removeData(playerUUID, SLOT_KEY);
    }

    public static void clearAllShieldData() {
    }
}
