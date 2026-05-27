package com.gy_mod.gy_trinket.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 光点核心方块类
 * 实现发光、打开存储菜单等功能
 */
public class LightPointCoreBlock extends BaseEntityBlock {
    // 方块碰撞箱形状，完整方块大小
    private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 1, 1);

    /**
     * 构造函数
     * @param properties 方块属性
     */
    public LightPointCoreBlock(Properties properties) {
        super(properties
            .noOcclusion() // 不遮挡光照
            .isSuffocating((state, level, pos) -> false) // 不是固体
            .isRedstoneConductor((state, level, pos) -> false) // 不导电红石
            .lightLevel(state -> 15) // 设置光照强度为 15 级（最亮）
        );
    }

    /**
     * 获取方块碰撞箱形状
     * @param state 方块状态
     * @param level 方块访问器
     * @param pos 方块位置
     * @param context 碰撞上下文
     * @return 方块的体素形状
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 获取渲染形状
     * 为了支持 GeckoLib 动画，使用 ENTITYBLOCK_ANIMATED
     * @param state 方块状态
     * @return 渲染形状
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /**
     * 创建新的方块实体
     * @param pos 方块位置
     * @param state 方块状态
     * @return 新的光点核心方块实体
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightPointCoreBlockEntity(pos, state);
    }

    /**
     * 方块右键交互事件
     * 当玩家右键点击方块时，打开存储菜单
     * @param state 方块状态
     * @param level 世界
     * @param pos 方块位置
     * @param player 玩家
     * @param hand 手持物品的手
     * @param hit 点击结果
     * @return 交互结果
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 只在服务端打开菜单
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            // 如果方块实体是菜单提供者，则打开菜单
            if (blockEntity instanceof MenuProvider) {
                player.openMenu((MenuProvider) blockEntity);
                return InteractionResult.SUCCESS;
            }
        }
        // 客户端显示成功交互
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}