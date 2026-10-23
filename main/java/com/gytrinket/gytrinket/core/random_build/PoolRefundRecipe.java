package com.gytrinket.gytrinket.core.random_build;

import com.gytrinket.gytrinket.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 构建池反悔配方：构建池物品（1 个）→ 点点光辉（1 个）。
 * <p>
 * 反悔机制：通过点点光辉从随机构建池获取的物品（基础护盾/机身/模块）可以合成换回点点光辉。
 * <p>
 * 配方本体静态注册（data/gytrinket/recipe/pool_refund.json），常驻生效，匹配条件动态判定：
 * <ul>
 * <li>输入恰好为 1 个<b>带有构建池标签（gytrinket:random_build_pool）</b>的物品，排除代币自身。
 *     标签成员由 {@link BuildPoolTagInjector} 自动注入（全部护盾（含 + 系列强化护盾）/机身/模块
 *     ∪ json 手写声明），与随机构建池的洗入范围明确区分；</li>
 * <li>产物为配置的代币物品（默认点点光辉）。</li>
 * </ul>
 * 动态条件放在 matches 而非运行时增删配方，避免操作 RecipeManager 的版本差异与同步问题。
 */
public class PoolRefundRecipe implements CraftingRecipe {

    /** 无状态配方单例：MapCodec/StreamCodec 的 unit 要求编码对象与注册时保存的实例同一，必须共享此单例 */
    public static final PoolRefundRecipe INSTANCE = new PoolRefundRecipe();
    public static final MapCodec<PoolRefundRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, PoolRefundRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // 配方常驻：不再随代币机制开关动态启停
        // 恰好 1 个非空物品，且带有构建池标签（标签内容由 BuildPoolTagInjector 自动注入：护盾/机身/模块 ∪ json 手写声明）
        ItemStack found = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (!found.isEmpty()) return false;
            found = stack;
        }
        if (found.isEmpty()) return false;
        Item item = found.getItem();
        // 排除代币自身，防止 tokenItem 配置为带属性物品时出现自转化
        return !RandomBuildManager.isTokenItem(item)
                && item.builtInRegistryHolder().is(RandomBuildManager.RANDOM_BUILD_POOL_TAG);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return RandomBuildManager.getTokenStack();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return RandomBuildManager.getTokenStack();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    /** 特殊配方：不进入配方书解锁体系（JEI 等查看器仍可见），避免无解锁进度的占位条目 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<? extends PoolRefundRecipe> getSerializer() {
        return ModRecipes.POOL_REFUND.get();
    }

    @Override
    public RecipeType<CraftingRecipe> getType() {
        return RecipeType.CRAFTING;
    }

    /** 序列化器：配方无字段，编解码均返回单例 */
    public static class Serializer implements RecipeSerializer<PoolRefundRecipe> {
        @Override
        public MapCodec<PoolRefundRecipe> codec() {
            return PoolRefundRecipe.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PoolRefundRecipe> streamCodec() {
            return PoolRefundRecipe.STREAM_CODEC;
        }
    }
}
