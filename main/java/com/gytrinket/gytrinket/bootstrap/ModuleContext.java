package com.gytrinket.gytrinket.bootstrap;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 模块可用的接线上下文：把 Forge 的注册入口收在一处，业务模块不直接摸 FML 上下文。
 */
public final class ModuleContext {

    private final FMLJavaModLoadingContext loadingContext;
    private final IEventBus modBus;
    private final IEventBus forgeBus;

    ModuleContext(FMLJavaModLoadingContext loadingContext) {
        this.loadingContext = loadingContext;
        this.modBus = loadingContext.getModEventBus();
        this.forgeBus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
    }

    public IEventBus modBus() {
        return modBus;
    }

    public IEventBus forgeBus() {
        return forgeBus;
    }

    public void registerForge(Object target) {
        forgeBus.register(target);
    }

    public void registerConfig(ModConfig.Type type, ForgeConfigSpec spec, String fileName) {
        loadingContext.registerConfig(type, spec, fileName);
    }
}
