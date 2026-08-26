package com.gy_mod.gy_trinket.items;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorConfigContainer;
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

    public static final RegistryObject<MenuType<com.gy_mod.gy_trinket.menu.LightPointCoreMenu>> LIGHT_POINT_CORE =
            MENUS.register("light_point_core", () -> com.gy_mod.gy_trinket.menu.LightPointCoreMenu.TYPE);
}
