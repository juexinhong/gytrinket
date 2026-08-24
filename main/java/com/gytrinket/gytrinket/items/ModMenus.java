package com.gytrinket.gytrinket.items;

import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorConfigContainer;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型注册
 */
public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, gytrinket.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<InterceptorConfigContainer>> INTERCEPTOR_CONFIG =
            MENUS.register("interceptor_config", () -> InterceptorConfigContainer.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<com.gytrinket.gytrinket.menu.LightPointCoreMenu>> LIGHT_POINT_CORE =
            MENUS.register("light_point_core", () -> com.gytrinket.gytrinket.menu.LightPointCoreMenu.TYPE);
}
