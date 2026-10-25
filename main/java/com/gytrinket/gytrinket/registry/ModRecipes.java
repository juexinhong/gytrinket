package com.gytrinket.gytrinket.registry;

import com.gytrinket.gytrinket.core.random_build.PoolRefundRecipe;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 配方序列化器注册 */
public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, gytrinket.MODID);

    /** 构建池反悔配方（构建池物品 → 点点光辉） */
    public static final RegistryObject<PoolRefundRecipe.Serializer> POOL_REFUND =
            SERIALIZERS.register("pool_refund", PoolRefundRecipe.Serializer::new);

    private ModRecipes() {}
}
