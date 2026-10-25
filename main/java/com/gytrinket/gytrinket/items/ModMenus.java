package com.gytrinket.gytrinket.items;

import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorConfigContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 菜单类型注册
 */
public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "gytrinket");

    public static final RegistryObject<MenuType<InterceptorConfigContainer>> INTERCEPTOR_CONFIG =
            MENUS.register("interceptor_config", () -> InterceptorConfigContainer.TYPE);

    public static final RegistryObject<MenuType<com.gytrinket.gytrinket.menu.LightPointCoreMenu>> LIGHT_POINT_CORE =
            MENUS.register("light_point_core", () -> com.gytrinket.gytrinket.menu.LightPointCoreMenu.TYPE);
}
