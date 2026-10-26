package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.compat.CuriosCompat;
import com.gytrinket.gytrinket.compat.TaczCompat;

import java.util.Set;

/**
 * 兼容模块：Curios 未安装时静默降级。
 */
public final class CompatModule implements GyModule {

    @Override
    public String id() {
        return "core.compat";
    }

    @Override
    public Set<String> requires() {
        return Set.of("core.registry");
    }

    @Override
    public void register(ModuleContext context) {
        CuriosCompat.init();
        TaczCompat.init();
    }
}
