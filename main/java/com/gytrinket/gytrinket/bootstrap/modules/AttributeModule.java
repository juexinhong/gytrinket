package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;

/**
 * 属性模块：属性定义表与属性重算事件的总线入口。
 */
public final class AttributeModule implements GyModule {

    @Override
    public String id() {
        return "core.attribute";
    }

    @Override
    public void register(ModuleContext context) {
        context.registerForge(AttributeManager.class);
    }
}
