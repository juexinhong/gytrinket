package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.EventWiring;
import com.gytrinket.gytrinket.bootstrap.GyBuses;
import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;

import java.util.Set;

/**
 * 内部事件接线模块：把 P5-3 迁移后的监听器注册到 GyBus。
 * <p>
 * 必须早于任何内部事件 post；FMLCommonSetup 已足够（事件首次发生在数据包重载/玩家登录之后）。
 */
public final class EventWiringModule implements GyModule {

    @Override
    public String id() {
        return "core.events";
    }

    @Override
    public Set<String> requires() {
        return Set.of("core.registry", "core.attribute");
    }

    @Override
    public void setup(ModuleContext context) {
        EventWiring.register(GyBuses.INTERNAL);
    }
}
