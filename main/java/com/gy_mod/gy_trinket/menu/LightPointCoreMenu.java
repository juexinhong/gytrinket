package com.gy_mod.gy_trinket.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;

/**
 * 光点核心容器菜单（3 行 27 槽）
 * 槽位 18px 格子、20px 间隔（与玩家面板一致），用于显示禁用物品遮罩
 */
public class LightPointCoreMenu extends AbstractContainerMenu {

    public static final MenuType<LightPointCoreMenu> TYPE = IForgeMenuType.create(
            (containerId, inventory, buf) -> new LightPointCoreMenu(containerId, inventory, new SimpleContainer(27)));

    private final Container container;

    public LightPointCoreMenu(int containerId, Inventory playerInventory, Container container) {
        super(TYPE, containerId);
        this.container = container;
        checkContainerSize(container, 27);
        container.startOpen(playerInventory.player);

        // 光点核心物品槽：3 行 9 列，18px 格子 / 20px 间隔
        // 与 1.21.1 一致：槽位整体向右下偏移 1px（物品渲染位置 = slot.x + 1）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, col + row * 9, 8 + col * 20 + 1, 18 + row * 20 + 1));
            }
        }
        // 玩家背包：3 行（与光点核心容器间距 14px，容纳"玩家背包"分割标签）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 20, 90 + row * 20));
            }
        }
        // 玩家快捷栏：1 行
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 20, 154));
        }
    }

    /** Shift + 左键快速移动：容器槽 <-> 玩家背包/快捷栏 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index < 27) {
                // 容器 -> 玩家背包（优先快捷栏）
                if (!this.moveItemStackTo(stack, 27, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 玩家 -> 容器
                if (!this.moveItemStackTo(stack, 0, 27, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return itemstack;
    }

    /**
     * 一键整理光点核心容器（鼠标中键触发）：先合并同类可堆叠物品，再按创造物品栏排序
     * 排序规则 = 物品注册 ID 升序（与创造物品栏一致），同物品按数量降序
     */
    public void sortContainer() {
        // 取出全部非空物品并清空容器
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = this.container.getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
                this.container.setItem(i, ItemStack.EMPTY);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        // 合并同类可堆叠物品（物品 + NBT 一致且目标未满堆）
        java.util.List<ItemStack> merged = new java.util.ArrayList<>();
        for (ItemStack stack : items) {
            boolean placed = false;
            if (stack.isStackable()) {
                for (ItemStack target : merged) {
                    if (target.getCount() < target.getMaxStackSize()
                            && ItemStack.isSameItemSameTags(target, stack)) {
                        int canAdd = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
                        target.grow(canAdd);
                        stack.shrink(canAdd);
                        if (stack.isEmpty()) {
                            placed = true;
                            break;
                        }
                    }
                }
            }
            if (!placed && !stack.isEmpty()) {
                merged.add(stack);
            }
        }
        // 排序：注册 ID 升序，同 ID 按数量降序
        merged.sort((a, b) -> {
            int ia = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(a.getItem());
            int ib = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(b.getItem());
            if (ia != ib) {
                return Integer.compare(ia, ib);
            }
            return Integer.compare(b.getCount(), a.getCount());
        });
        // 写回容器（防越界保护）
        for (int i = 0; i < merged.size() && i < 27; i++) {
            this.container.setItem(i, merged.get(i));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
