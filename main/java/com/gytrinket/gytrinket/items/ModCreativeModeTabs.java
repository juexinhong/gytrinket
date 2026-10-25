package com.gytrinket.gytrinket.items;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 本模组的创造模式标签页。
 * <p>
 * 1.19.2 的创造标签页**不是注册表对象**（1.19.3+ 才有
 * {@code Registries.CREATIVE_MODE_TAB} / {@code CreativeModeTab.builder()}）：
 * Forge 把 {@code CreativeModeTab} 的构造放开，构造即登记到静态 {@code TABS} 数组，
 * 物品归属则通过 {@code Item.Properties#tab(CreativeModeTab)} 声明。
 * <p>
 * 与原 1.20.1 实现的差异：原版 {@code fillItemList} 是按注册表顺序遍历、
 * 由 {@code Item#fillItemCategory} 过滤，因此本标签页内的物品顺序等于
 * {@code ModItems} 的注册顺序，而不是原先手工编排的分组顺序。
 * 标签页标题键为 {@code itemGroup.gytrinket}（1.19.2 由构造器生成）。
 */
public class ModCreativeModeTabs {

    /** 本模组唯一标签页（构造即注册，详见类注释）。 */
    public static final CreativeModeTab GY_TRINKET_TAB = new CreativeModeTab(gytrinket.MODID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.LIGHT_POINT_CORE.get());
        }
    };

    private ModCreativeModeTabs() {
    }

    /** 1.19.2 无需注册表注册，保留空实现以对齐原调用点。 */
    public static void register(IEventBus eventBus) {
        // no-op
    }
}
