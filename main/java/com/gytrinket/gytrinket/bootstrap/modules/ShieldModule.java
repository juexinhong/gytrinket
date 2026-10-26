package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.core.shield.type.ReflectShieldType;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;

import java.util.Set;

/**
 * 护盾模块：护盾类型注册与初始化。
 */
public final class ShieldModule implements GyModule {

    @Override
    public String id() {
        return "core.shield";
    }

    @Override
    public Set<String> requires() {
        return Set.of("core.attribute");
    }

    @Override
    public void register(ModuleContext context) {
        context.registerForge(ShieldTypeManager.class);
        context.registerForge(ReflectShieldType.class);
    }

    @Override
    public void setup(ModuleContext context) {
        ShieldTypeManager.init();
    }
}
