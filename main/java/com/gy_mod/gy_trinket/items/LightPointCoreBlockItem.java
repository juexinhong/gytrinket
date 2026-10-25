package com.gy_mod.gy_trinket.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * 光点核心方块物品
 * <p>
 * 物品形态使用方块实体的原生 3D 模型渲染（BEWLR），而非独立 2D 贴图。
 * 客户端渲染器位于 com.gy_mod.gy_trinket.client.LightPointCoreItemRenderer，
 * 本类保持两侧通用，不含任何客户端专属引用，可在专用服务器上安全加载。
 */
public class LightPointCoreBlockItem extends BlockItem {
    public LightPointCoreBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /**
     * 注册客户端渲染器扩展（Forge 1.20.1 的 IClientItemExtensions 注册入口）
     * initializeClient 仅在客户端初始化时被调用，服务器上安全
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.gy_mod.gy_trinket.client.LightPointCoreItemRenderer.getRenderer();
            }
        });
    }
}
