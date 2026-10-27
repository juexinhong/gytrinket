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
        // 必须走 ModLoadingContext 而不是 FMLJavaModLoadingContext：1.19.2 的整合包绝大多数停在
        // Forge 43.1~43.3（ATM8 用 43.2.14、FTB Skies 43.3.9、FTB Genesis 43.2.21），
        // 43.2.x 的 FMLJavaModLoadingContext 并不继承 ModLoadingContext，上面没有 registerConfig；
        // 43.5.x 才改成继承。用父类静态入口两边都能编译/运行。
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(type, spec, fileName);
    }
}
