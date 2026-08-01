package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.upgrade.UpgradeData;
import com.gytrinket.gytrinket.core.upgrade.UpgradeManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

public record UpgradeConsumePayload(int slotIndex, String baseItemKey, String upgradedItemKey) implements CustomPacketPayload {
    public static final Type<UpgradeConsumePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "upgrade_consume"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeConsumePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, UpgradeConsumePayload::slotIndex,
        ByteBufCodecs.STRING_UTF8, UpgradeConsumePayload::baseItemKey,
        ByteBufCodecs.STRING_UTF8, UpgradeConsumePayload::upgradedItemKey,
        UpgradeConsumePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UpgradeConsumePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!Config.UPGRADE_SYSTEM_ENABLED.get()) return;

            ItemStack clickedItem = player.getInventory().getItem(payload.slotIndex);
            if (clickedItem.isEmpty()) return;

            PlayerStore store = PlayerStoreManager.getPlayerStore(player);
            if (store == null) return;

            var handler = store.getItemHandler();
            UUID playerUUID = player.getUUID();
            UpgradeData upgradeData = UpgradeManager.getUpgradeData(playerUUID);

            ResourceLocation baseItemRes = ResourceLocation.parse(payload.baseItemKey);
            Item baseItem = BuiltInRegistries.ITEM.get(baseItemRes);
            if (baseItem == null) return;

            ResourceLocation upgradedItemRes = ResourceLocation.parse(payload.upgradedItemKey);
            Item upgradedItem = BuiltInRegistries.ITEM.get(upgradedItemRes);
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
    }
}
