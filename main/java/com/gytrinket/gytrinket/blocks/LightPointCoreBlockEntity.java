package com.gytrinket.gytrinket.blocks;

import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 光点核心方块实体
 * 负责处理方块的渲染、菜单打开等功能
 * 存储逻辑已移到 PlayerStoreManager 中
 */
public class LightPointCoreBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {
    // GeckoLib 动画实例缓存
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * 构造函数
     * @param pos 方块位置
     * @param state 方块状态
     */
    public LightPointCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_POINT_CORE.get(), pos, state);
    }

    /**
     * 获取方块的显示名称
     * @return 方块的显示名称
     */
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gytrinket.light_point_core_block_gy");
    }

    /**
     * 创建菜单
     * 当玩家右键点击方块时调用此方法
     * @param containerId 菜单容器 ID
     * @param playerInventory 玩家的物品栏
     * @param player 玩家实例
     * @return 创建的菜单实例
     */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // 获取或创建玩家的存储
        PlayerStore store = PlayerStoreManager.getOrCreatePlayerStore(player);
        ItemStackHandler playerHandler = store.getItemHandler();

        // 创建 Container 接口的匿名实现
        return ChestMenu.threeRows(containerId, playerInventory, new net.minecraft.world.Container() {
            /**
             * 获取容器大小
             * @return 容器大小，这里是 27
             */
            @Override
            public int getContainerSize() {
                return playerHandler.getSlots();
            }

            /**
             * 检查容器是否为空
             * @return 如果所有槽位都是空的则返回 true
             */
            @Override
            public boolean isEmpty() {
                for (int i = 0; i < playerHandler.getSlots(); i++) {
                    if (!playerHandler.getStackInSlot(i).isEmpty()) {
                        return false;
                    }
                }
                return true;
            }

            /**
             * 获取指定槽位的物品
             * @param slot 槽位索引
             * @return 槽位中的物品
             */
            @Override
            public ItemStack getItem(int slot) {
                return playerHandler.getStackInSlot(slot);
            }

            /**
             * 从指定槽位移除一定数量的物品
             * @param slot 槽位索引
             * @param count 要移除的物品数量
             * @return 移除的物品
             */
            @Override
            public ItemStack removeItem(int slot, int count) {
                return playerHandler.extractItem(slot, count, false);
            }

            /**
             * 从指定槽位移除所有物品
             * @param slot 槽位索引
             * @return 移除的物品
             */
            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                ItemStack stack = playerHandler.getStackInSlot(slot).copy();
                playerHandler.setStackInSlot(slot, ItemStack.EMPTY);
                return stack;
            }

            /**
             * 设置指定槽位的物品
             * @param slot 槽位索引
             * @param stack 要设置的物品
             */
            @Override
            public void setItem(int slot, ItemStack stack) {
                playerHandler.setStackInSlot(slot, stack);
            }

            /**
             * 标记容器发生变化
             * 这里不需要做任何事，ItemStackHandler 已经处理了
             */
            @Override
            public void setChanged() {}

            /**
             * 检查玩家是否仍然可以使用此容器
             * @param player 玩家实例
             * @return 总是返回 true，因为存储是玩家绑定的
             */
            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            /**
             * 当玩家开始打开容器时调用
             * @param player 玩家实例
             */
            @Override
            public void startOpen(Player player) {}

            /**
             * 当玩家停止打开容器时调用
             * @param player 玩家实例
             */
            @Override
            public void stopOpen(Player player) {}

            /**
             * 检查物品是否可以放入指定槽位
             * @param slot 槽位索引
             * @param stack 要放入的物品
             * @return 总是返回 true
             */
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return true;
            }

            /**
             * 清空容器
             */
            @Override
            public void clearContent() {
                for (int i = 0; i < playerHandler.getSlots(); i++) {
                    playerHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        });
    }

    /**
     * 注册动画控制器
     * @param controllers 动画控制器注册器
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 添加一个循环播放 idle 动画的控制器
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    /**
     * 动画状态判断
     * @param event 动画状态事件
     * @return 动画状态
     */
    private PlayState predicate(AnimationState<LightPointCoreBlockEntity> event) {
        // 循环播放 recharge 动画
        event.setAndContinue(RawAnimation.begin().thenLoop("recharge"));
        return PlayState.CONTINUE;
    }

    /**
     * 获取 GeckoLib 的动画实例缓存
     * @return 动画实例缓存
     */
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
