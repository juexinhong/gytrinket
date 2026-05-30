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
import com.gy_mod.gy_trinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.common.MinecraftForge;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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
                        MinecraftForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldShield));
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
                MinecraftForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldShield));
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
