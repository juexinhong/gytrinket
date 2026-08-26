package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.extensions.IForgeMenuType;

import java.util.UUID;

/**
 * 拦截机配置容器
 * <p>
 * 自定义紧凑布局，包含武器槽、弹药槽和玩家背包。
 * 武器槽接受任意物品，弹药槽仅接受箭矢类物品。
 * 物品变更实时同步到 InterceptorWeaponManager。
 */
public class InterceptorConfigContainer extends AbstractContainerMenu {

    public static final MenuType<InterceptorConfigContainer> TYPE = IForgeMenuType.create(
            (windowId, inv, data) -> {
                ItemStack weapon = data.readItem();
                ItemStack ammo = data.readItem();
                String attackModeName = data.readUtf();
                return new InterceptorConfigContainer(windowId, inv, weapon, ammo, attackModeName);
            }
    );

    private final SimpleContainer interceptorSlots = new SimpleContainer(2);
    private final UUID playerUUID;
    private InterceptorAttackMode attackMode;

    // 客户端构造器（从 FriendlyByteBuf 读取）
    public InterceptorConfigContainer(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, data.readItem(), data.readItem(), data.readUtf());
    }

    // 服务端构造器（直接传入数据）
    public InterceptorConfigContainer(int containerId, Inventory inventory, ItemStack weapon, ItemStack ammo, String attackModeName) {
        super(TYPE, containerId);
        this.playerUUID = inventory.player.getUUID();

        // 初始化拦截机槽位内容
        this.interceptorSlots.setItem(0, weapon.copy());
        this.interceptorSlots.setItem(1, ammo.copy());
        this.attackMode = InterceptorAttackMode.byName(attackModeName);

        // 武器槽（index 0）：左上区域
        this.addSlot(new Slot(interceptorSlots, 0, 26, 22) {
            @Override
            public int getMaxStackSize(ItemStack stack) {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty();
            }
        });

        // 弹药槽（index 1）：武器槽右侧
        this.addSlot(new Slot(interceptorSlots, 1, 80, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ArrowItem;
            }
        });

        // 玩家背包（3行9列）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 20, 84 + row * 20));
            }
        }

        // 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 20, 148));
        }

        // 监听器：物品变更时同步到 InterceptorWeaponManager
        this.interceptorSlots.addListener(container -> {
            if (!inventory.player.level().isClientSide) {
                InterceptorWeaponManager.setWeapon(playerUUID, interceptorSlots.getItem(0));
                InterceptorWeaponManager.setAmmo(playerUUID, interceptorSlots.getItem(1));
            }
        });
    }

    public InterceptorAttackMode getAttackMode() {
        return attackMode;
    }

    public void setAttackMode(InterceptorAttackMode mode) {
        this.attackMode = mode;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            if (index < 2) {
                // 从拦截机槽位移到玩家背包
                if (!this.moveItemStackTo(slotStack, 2, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到拦截机槽位
                if (slotStack.getItem() instanceof ArrowItem) {
                    if (!this.moveItemStackTo(slotStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            // 保存到 Manager（攻击模式已由 SetInterceptorAttackModeMessage 实时更新）
            InterceptorWeaponManager.setWeapon(playerUUID, interceptorSlots.getItem(0));
            InterceptorWeaponManager.setAmmo(playerUUID, interceptorSlots.getItem(1));
            // 刷新所有僚机实体
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                InterceptorWeaponManager.refreshAllWingmen(serverPlayer);
            }
        }
    }
}
