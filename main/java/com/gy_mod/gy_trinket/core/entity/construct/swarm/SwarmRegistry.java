package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructTypeRegistry;

/**
 * 蜂群构造体注册器
 */
public class SwarmRegistry implements ConstructTypeRegistry.IConstructRegistry {
    @Override
    public void register() {
        SwarmConstructTypes.register();
    }
}
