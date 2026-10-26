package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.core.damage.DamageManager;
import com.gytrinket.gytrinket.core.damage.InvincibilityMarkerManager;
import com.gytrinket.gytrinket.core.damage_last.LastDamageManager;

import java.util.Set;

/**
 * 伤害模块：护盾拦截链与最终伤害链的装配入口。
 * <p>
 * 注意：两条链阶段不同，模块只负责各自装配，不能合并。
 */
public final class DamageModule implements GyModule {

    @Override
    public String id() {
        return "core.damage";
    }

    @Override
    public Set<String> requires() {
        return Set.of("core.attribute");
    }

    @Override
    public void register(ModuleContext context) {
        context.registerForge(InvincibilityMarkerManager.class);
    }

    @Override
    public void setup(ModuleContext context) {
        DamageManager.init();
        LastDamageManager.init();
    }
}
