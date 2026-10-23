package com.gytrinket.gytrinket.registry;

import com.gytrinket.gytrinket.core.random_build.PoolRefundRecipe;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 配方序列化器注册 */
public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, gytrinket.MODID);

    /** 构建池反悔配方（构建池物品 → 点点光辉） */
    public static final Supplier<PoolRefundRecipe.Serializer> POOL_REFUND =
            SERIALIZERS.register("pool_refund", PoolRefundRecipe.Serializer::new);

    private ModRecipes() {}
}
