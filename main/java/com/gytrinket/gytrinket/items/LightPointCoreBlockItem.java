package com.gytrinket.gytrinket.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 光点核心方块物品
 * <p>
 * 物品形态使用方块实体的原生 3D 模型渲染（BEWLR），而非独立 2D 贴图。
 * 客户端渲染器位于 com.gytrinket.gytrinket.client.LightPointCoreItemRenderer，
 * 本类保持两侧通用，不含任何客户端专属引用，可在专用服务器上安全加载。
 */
public class LightPointCoreBlockItem extends BlockItem {
    public LightPointCoreBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }
}
