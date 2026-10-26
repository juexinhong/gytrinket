package com.gytrinket.gytrinket;

import com.gytrinket.gytrinket.bootstrap.ModRuntime;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 模组入口。
 * <p>
 * P5-2 起只做两件事：构造 {@link ModRuntime} 跑模块注册，再在 FMLCommonSetup 阶段跑模块初始化。
 * 所有注册表/配置/网络/伤害链/构造体/调度器的接线都在 {@code bootstrap.ModModules} 清单里。
 */
@Mod(gytrinket.MODID)
public class gytrinket {
    public static final String MODID = "gytrinket";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final ModRuntime runtime;

    public gytrinket(FMLJavaModLoadingContext context) {
        this.runtime = new ModRuntime(context);
        runtime.registerAll();
        context.getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        runtime.setupAll();
    }
}
