package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.core.random_build.RandomBuildManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = gytrinket.MODID)
public class QuickEquipEvent {

    private QuickEquipEvent() {}

    @SubscribeEvent
    public static void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;

        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (player.level().isClientSide()) {
            return;
        }

        if (stack.isEmpty()) {
            return;
        }

        // 代币指定物品：即使注册了属性或特殊机制，也禁止快速装备（代币右键用于打开玩家面板）
        if (stack.getItem() == RandomBuildManager.getTokenItem()) {
            return;
        }

        Item item = stack.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        if (!isQuickEquipItem(itemId, item)) {
            return;
        }

        event.setCanceled(true);

        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        if (store == null) {
            return;
        }

        ItemStackHandler handler = store.getItemHandler();

        if (hasSameItemInStore(handler, item)) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.already_exists"));
            return;
        }

        List<String> newShieldTypes = getItemShieldTypes(item);

        if (!newShieldTypes.isEmpty()) {
            handleShieldEquip(player, handler, stack, item, newShieldTypes);
        } else {
            handleNormalEquip(player, handler, stack);
        }
    }

    private static void handleShieldEquip(Player player, ItemStackHandler handler, ItemStack stack, Item item, List<String> newShieldTypes) {
        List<Integer> shieldSlots = findShieldItemSlots(handler);

        if (shieldSlots.isEmpty()) {
            handleNormalEquip(player, handler, stack);
            return;
        }

        boolean newIsCompatible = newShieldTypes.stream().allMatch(Config::isShieldTypeCompatible);

        if (!newIsCompatible) {
            int cost = Config.getQuickEquipUpgradePointsCost();
            if (ModLevelManager.getUpgradePoints(player.getUUID()) < cost) {
                player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.not_enough_points"));
                return;
            }

            transferItemsToPlayer(player, handler, shieldSlots);

            ModLevelManager.consumeUpgradePoints(player.getUUID(), cost);

            addToStore(handler, stack);
            player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.shield_swapped"));
        } else {
            List<Integer> incompatibleSlots = new ArrayList<>();
            for (int slot : shieldSlots) {
                ItemStack slotStack = handler.getStackInSlot(slot);
                List<String> slotTypes = getItemShieldTypes(slotStack.getItem());
                boolean slotHasIncompatible = slotTypes.stream().anyMatch(type -> !Config.isShieldTypeCompatible(type));
                if (slotHasIncompatible) {
                    incompatibleSlots.add(slot);
                }
            }

            if (!incompatibleSlots.isEmpty()) {
                int cost = Config.getQuickEquipUpgradePointsCost();
                if (ModLevelManager.getUpgradePoints(player.getUUID()) < cost) {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.not_enough_points"));
                    return;
                }

                transferItemsToPlayer(player, handler, incompatibleSlots);

                ModLevelManager.consumeUpgradePoints(player.getUUID(), cost);
            }

            if (!hasEmptySlot(handler)) {
                player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.core_full"));
                return;
            }

            addToStore(handler, stack);
            if (!incompatibleSlots.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.shield_swapped"));
            } else {
                player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.success"));
            }
        }
    }

    private static void handleNormalEquip(Player player, ItemStackHandler handler, ItemStack stack) {
        if (!hasEmptySlot(handler)) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.core_full"));
            return;
        }

        int uniqueItemsCount = countUniqueItems(handler);
        int cost = Config.getQuickEquipUpgradePointsCost();
        if (ModLevelManager.getUpgradePoints(player.getUUID()) < cost) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.not_enough_points"));
            return;
        }

        ModLevelManager.consumeUpgradePoints(player.getUUID(), cost);

        addToStore(handler, stack);
        player.sendSystemMessage(Component.translatable("message.gytrinket.quick_equip.success"));
    }

    private static List<String> getItemShieldTypes(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl == null) {
            return List.of();
        }
        return Config.getItemShieldTypes(rl);
    }

    private static List<Integer> findShieldItemSlots(ItemStackHandler handler) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (!slotStack.isEmpty() && !getItemShieldTypes(slotStack.getItem()).isEmpty()) {
                slots.add(i);
            }
        }
        return slots;
    }

    /**
     * 判定物品是否"注册了属性或特殊机制"（可装备类物品：模块/护盾/机身等）。
     * <p>
     * 各路径职责单一：
     * <ol>
     *   <li>本模组属性（{@link AttributeManager#isItemAttributeRegistered}）</li>
     *   <li>护盾类型（item_shield_types）</li>
     *   <li>特殊机制声明（special_mechanics 路径，文件存在即声明，
     *       {@link Config#isSpecialMechanicItem}）——快速装备只检查该路径</li>
     * </ol>
     */
    public static boolean isQuickEquipItem(String itemId, Item item) {
        if (AttributeManager.isItemAttributeRegistered(itemId)) {
            return true;
        }

        return !getItemShieldTypes(item).isEmpty()
                || Config.isSpecialMechanicItem(item);
    }

    private static boolean hasSameItemInStore(ItemStackHandler handler, Item item) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (!slotStack.isEmpty() && slotStack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEmptySlot(ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int countUniqueItems(ItemStackHandler handler) {
        Set<Item> uniqueItems = new HashSet<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                uniqueItems.add(stack.getItem());
            }
        }
        return uniqueItems.size();
    }

    private static void addToStore(ItemStackHandler handler, ItemStack stack) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                ItemStack singleStack = stack.copy();
                singleStack.setCount(1);
                handler.setStackInSlot(i, singleStack);
                stack.shrink(1);
                break;
            }
        }
    }

    private static void transferItemsToPlayer(Player player, ItemStackHandler handler, List<Integer> slots) {
        for (int slot : slots) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (!slotStack.isEmpty()) {
                handler.setStackInSlot(slot, ItemStack.EMPTY);
                player.addItem(slotStack);
            }
        }
    }
}
