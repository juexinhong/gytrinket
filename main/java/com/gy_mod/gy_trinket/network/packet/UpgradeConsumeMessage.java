package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeData;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class UpgradeConsumeMessage {
    private int slotIndex;
    private String baseItemKey;
    private String upgradedItemKey;

    public UpgradeConsumeMessage() {}

    public UpgradeConsumeMessage(int slotIndex, String baseItemKey, String upgradedItemKey) {
        this.slotIndex = slotIndex;
        this.baseItemKey = baseItemKey;
        this.upgradedItemKey = upgradedItemKey;
    }

    public UpgradeConsumeMessage(FriendlyByteBuf buf) {
        this.slotIndex = buf.readInt();
        this.baseItemKey = buf.readUtf();
        this.upgradedItemKey = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(slotIndex);
        buf.writeUtf(baseItemKey);
        buf.writeUtf(upgradedItemKey);
    }

    public static void handle(UpgradeConsumeMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!Config.UPGRADE_SYSTEM_ENABLED.get()) return;

            ItemStack clickedItem = player.getInventory().getItem(msg.slotIndex);
            if (clickedItem.isEmpty()) return;

            PlayerStore store = PlayerStoreManager.getPlayerStore(player);
            if (store == null) return;

            var handler = store.getItemHandler();
            UUID playerUUID = player.getUUID();
            UpgradeData upgradeData = UpgradeManager.getUpgradeData(playerUUID);

            ResourceLocation baseItemRes = new ResourceLocation(msg.baseItemKey);
            Item baseItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(baseItemRes);
            if (baseItem == null) return;

            ResourceLocation upgradedItemRes = new ResourceLocation(msg.upgradedItemKey);
            Item upgradedItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(upgradedItemRes);
            if (upgradedItem == null) return;

            boolean foundInTargets = false;
            for (Item target : UpgradeManager.getUpgradeTargets(baseItem)) {
                if (target == upgradedItem) {
                    foundInTargets = true;
                    break;
                }
            }
            if (!foundInTargets) return;

            boolean baseItemInStore = false;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(baseItem)) {
                    baseItemInStore = true;
                    break;
                }
            }
            if (!baseItemInStore) return;

            Recipe<?> recipe = UpgradeManager.getUpgradeRecipe(
                player.serverLevel().getRecipeManager(),
                player.serverLevel().registryAccess(),
                upgradedItem
            );
            if (recipe == null) return;

            String baseKey = UpgradeManager.getItemKey(baseItem);
            String pathKey = baseKey + "->" + UpgradeManager.getItemKey(upgradedItem);

            Map<String, int[]> ingredientStatus = UpgradeManager.getIngredientStatus(
                    handler, upgradeData, pathKey, recipe);

            boolean isNeeded = false;
            for (Map.Entry<String, int[]> entry : ingredientStatus.entrySet()) {
                if (entry.getValue()[1] < entry.getValue()[0]) {
                    Ingredient ingredient = UpgradeManager.getIngredientForItemKey(recipe, entry.getKey());
                    if (ingredient != null && ingredient.test(clickedItem)) {
                        isNeeded = true;
                        break;
                    }
                }
            }
            if (!isNeeded) return;

            upgradeData.addMaterial(pathKey, clickedItem);
            clickedItem.shrink(1);
            UpgradeManager.setUpgradeData(playerUUID, upgradeData);

            int[] checkResult = UpgradeManager.checkIngredients(handler, upgradeData, pathKey, recipe);
            if (checkResult[0] >= checkResult[1]) {
                if (UpgradeManager.performUpgrade(handler, upgradeData, baseItem, upgradedItem, recipe, playerUUID)) {
                    UpgradeManager.setUpgradeData(playerUUID, upgradeData);
                    player.sendSystemMessage(Component.translatable(
                        "message.gytrinket.upgrade.success",
                        baseItem.getName(new ItemStack(baseItem)),
                        upgradedItem.getName(new ItemStack(upgradedItem))
                    ));
                } else {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.upgrade.no_space"));
                }
            } else {
                player.sendSystemMessage(Component.translatable(
                    "message.gytrinket.upgrade.material_collected",
                    checkResult[0], checkResult[1]
                ));
            }

            NetworkHandler.sendPanelUpdate(player);
        });

        context.setPacketHandled(true);
    }
}
