package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.core.TickScheduler;
import com.gytrinket.gytrinket.core.tick.PlayerTickDispatcher;

/**
 * tick 模块：服务端任务调度器（P5-7 增加玩家 tick 单扇出）。
 */
public final class TickModule implements GyModule {

    @Override
    public String id() {
        return "core.tick";
    }

    @Override
    public void register(ModuleContext context) {
        context.registerForge(TickScheduler.class);
    }

    @Override
    public void setup(ModuleContext context) {
        PlayerTickDispatcher.init();
    }
}
