package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.config.ClientConfig;
import com.gytrinket.gytrinket.config.Config;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 配置模块：两份 ForgeConfigSpec 的注册点。
 */
public final class ConfigModule implements GyModule {

    @Override
    public String id() {
        return "core.config";
    }

    @Override
    public void register(ModuleContext context) {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "gytrinket/gytrinket-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "gytrinket/gytrinket-client.toml");
    }
}
