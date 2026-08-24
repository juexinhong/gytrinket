package com.gytrinket.gytrinket.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/**
 * 光点核心容器菜单（3 行 27 槽）
 * 槽位 18px 格子、20px 间隔（与玩家面板一致），用于显示禁用物品遮罩
 */
public class LightPointCoreMenu extends AbstractContainerMenu {

    public static final MenuType<LightPointCoreMenu> TYPE = IMenuTypeExtension.create(
            (containerId, inventory, buf) -> new LightPointCoreMenu(containerId, inventory, new SimpleContainer(27)));

    private final Container container;

    public LightPointCoreMenu(int containerId, Inventory playerInventory, Container container) {
        super(TYPE, containerId);
        this.container = container;
        checkContainerSize(container, 27);
        container.startOpen(playerInventory.player);

        // 光点核心物品槽：3 行 9 列，18px 格子 / 20px 间隔
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, col + row * 9, 8 + col * 20, 18 + row * 20));
            }
        }
        // 玩家背包：3 行
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 20, 86 + row * 20));
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
