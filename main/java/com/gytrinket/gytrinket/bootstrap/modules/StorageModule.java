package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.storage.datacenter.DataCenterLifecycleHandler;

/**
 * 玩家数据模块：数据槽位注册与登录/登出生命周期。
 */
public final class StorageModule implements GyModule {

    @Override
    public String id() {
        return "core.storage";
    }

    @Override
    public void register(ModuleContext context) {
        context.registerForge(DataCenterLifecycleHandler.class);
    }

    @Override
    public void setup(ModuleContext context) {
        DataCenterLifecycleHandler.init();
    }
}
