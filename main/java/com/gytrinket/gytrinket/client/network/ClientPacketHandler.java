package com.gytrinket.gytrinket.client.network;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.client.screen.AbstractPanelScreen;
import com.gytrinket.gytrinket.client.screen.ConfigPanelScreen;
import com.gytrinket.gytrinket.client.screen.PlayerPanelScreen;
import com.gytrinket.gytrinket.client.screen.UpgradeSelectScreen;
import com.gytrinket.gytrinket.client.screen.UpgradeTargetScreen;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleResponsePanelData(NetworkHandler.ResponsePanelDataPayload msg) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        PlayerPanelScreen panelScreen = null;

        if (currentScreen instanceof UpgradeSelectScreen selectScreen) {
            if (selectScreen.getParentScreen() instanceof PlayerPanelScreen ps) {
                panelScreen = ps;
            }
            String baseKey = selectScreen.getBaseItemKey();
            String upgradedKey = selectScreen.getUpgradedItemKey();
            boolean stillValid = false;
            ListTag matchedIngredients = null;
            for (int i = 0; i < msg.upgradeTargets().size(); i++) {
                CompoundTag targetTag = msg.upgradeTargets().getCompound(i);
                if (targetTag.getString("baseItemKey").equals(baseKey)
                        && targetTag.getString("upgradedItemKey").equals(upgradedKey)) {
                    stillValid = true;
                    matchedIngredients = targetTag.getList("ingredients", 10);
                    break;
                }
            }
            if (!stillValid) {
                mc.setScreen(panelScreen);
                currentScreen = panelScreen;
            } else if (matchedIngredients != null) {
                selectScreen.updateIngredients(matchedIngredients);
            }
        } else if (currentScreen instanceof UpgradeTargetScreen targetScreen) {
            if (targetScreen.getParentScreen() instanceof PlayerPanelScreen ps) {
                panelScreen = ps;
            }
        }

        if (currentScreen instanceof PlayerPanelScreen ps) {
            ps.updateData(msg.attributes(), msg.items(), msg.slotCount(), msg.upgradeData(), msg.upgradeTargets(),
                    msg.modLevel(), msg.upgradeExp(), msg.upgradePoints());
        } else if (panelScreen != null) {
            panelScreen.updateData(msg.attributes(), msg.items(), msg.slotCount(), msg.upgradeData(), msg.upgradeTargets(),
                    msg.modLevel(), msg.upgradeExp(), msg.upgradePoints());
        } else {
            mc.setScreen(new PlayerPanelScreen(
                msg.attributes(), msg.items(), msg.slotCount(), msg.upgradeData(), msg.upgradeTargets(),
                msg.modLevel(), msg.upgradeExp(), msg.upgradePoints()));
        }
    }

    public static void handleResponseConfigData(NetworkHandler.ResponseConfigDataPayload msg) {
        syncLocalAttributeManager(msg.itemConfigData());
        Config.saveItemAttributesConfig();

        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        if (currentScreen instanceof ConfigPanelScreen configScreen) {
            configScreen.updateData(msg.itemConfigData(), msg.allAttributeNames());
        } else if (msg.openScreen()) {
            Screen parent = findParentPanel(currentScreen);
            mc.setScreen(new ConfigPanelScreen(parent, msg.itemConfigData(), msg.allAttributeNames()));
        }
    }

    private static Screen findParentPanel(Screen screen) {
        if (screen instanceof PlayerPanelScreen) return screen;
        if (screen instanceof AbstractPanelScreen aps) {
            Screen parent = findParentPanel(aps.getParentScreen());
            if (parent instanceof PlayerPanelScreen) return parent;
        }
        return screen;
    }

    private static void syncLocalAttributeManager(ListTag itemConfigData) {
        AttributeManager.clearAllItemAttributes();

        for (int i = 0; i < itemConfigData.size(); i++) {
            CompoundTag itemTag = itemConfigData.getCompound(i);
            String itemId = itemTag.getString("itemId");
            ListTag attrsTag = itemTag.getList("attributes", 10);
            Map<String, Double> attrs = new HashMap<>();
            for (int j = 0; j < attrsTag.size(); j++) {
                CompoundTag attrTag = attrsTag.getCompound(j);
                attrs.put(attrTag.getString("name"), attrTag.getDouble("value"));
            }
            if (!attrs.isEmpty()) {
                AttributeManager.registerItemAttributes(itemId, attrs);
            }
        }
    }
}
