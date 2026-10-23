package com.gy_mod.gy_trinket.core.random_build;

import com.gy_mod.gy_trinket.registry.ModRecipes;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 构建池反悔配方：构建池物品（1 个）→ 点点光辉（1 个）。
 * <p>
 * 反悔机制：通过点点光辉从随机构建池获取的物品（全部护盾（含 + 系列强化护盾）/机身/模块）可以合成换回点点光辉。
 * <p>
 * 配方本体静态注册（data/gytrinket/recipes/pool_refund.json），常驻生效（不随代币机制开关动态启停），
 * 匹配条件动态判定：
 * <ul>
 * <li>输入恰好为 1 个<b>带有构建池标签（gytrinket:random_build_pool）</b>的物品，排除代币自身。
 *     标签成员由 {@link BuildPoolTagInjector} 自动注入（全部护盾（含 + 系列强化护盾）/机身/模块
 *     ∪ json 手写声明），与随机构建池的洗入范围明确区分；</li>
 * <li>产物为配置的代币物品（默认点点光辉）。</li>
 * </ul>
 * 动态条件放在 matches 而非运行时增删配方，避免操作 RecipeManager 的版本差异与同步问题。
 */
public class PoolRefundRecipe implements CraftingRecipe {

    /** 配方 id（与 data/gytrinket/recipes/pool_refund.json 对应；1.20.1 Recipe 接口要求实现 getId） */
    private static final ResourceLocation ID =
            new ResourceLocation(com.gy_mod.gy_trinket.gytrinket.MODID, "pool_refund");

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // 配方常驻：不再随代币机制开关动态启停
        // 恰好 1 个非空物品，且带有构建池标签（标签内容由 BuildPoolTagInjector 自动注入：护盾/机身/模块 ∪ json 手写声明）
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
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
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return RandomBuildManager.getTokenStack();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return RandomBuildManager.getTokenStack();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    /** 1.20.1 CraftingRecipe 接口要求实现 category（配方书分类，特殊配方不进书但仍需返回） */
    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    /** 1.20.1 Recipe 接口要求实现 getId（1.20.2 起由 RecipeHolder 包装移除） */
    @Override
    public ResourceLocation getId() {
        return ID;
    }

    /** 特殊配方：不进入配方书解锁体系（JEI 等查看器仍可见），避免无解锁进度的占位条目 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.POOL_REFUND.get();
    }

    @Override
    public RecipeType<CraftingRecipe> getType() {
        return RecipeType.CRAFTING;
    }

    /** 序列化器：配方无字段，读档/网络传输均返回新实例 */
    public static class Serializer implements RecipeSerializer<PoolRefundRecipe> {
        @Override
        public PoolRefundRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            // 类别字段由原版配方管理器读取，反悔配方自身无字段
            GsonHelper.getAsString(json, "category", "misc");
            return new PoolRefundRecipe();
        }

        @Override
        public PoolRefundRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            return new PoolRefundRecipe();
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, PoolRefundRecipe recipe) {
        }
    }
}
