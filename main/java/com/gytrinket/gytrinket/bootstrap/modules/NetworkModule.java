package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.network.NetworkHandler;

/**
 * 网络模块：65 个包的注册入口（P5-6 换成 MessageSpec 单表）。
 */
public final class NetworkModule implements GyModule {

    @Override
    public String id() {
        return "core.network";
    }

    @Override
    public void setup(ModuleContext context) {
        NetworkHandler.registerMessages();
    }
}
