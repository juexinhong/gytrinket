package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.core.entity.construct.ConstructTypeRegistry;

/**
 * 蜂群构造体注册器
 */
public class SwarmRegistry implements ConstructTypeRegistry.IConstructRegistry {
    @Override
    public void register() {
        SwarmConstructTypes.register();
    }
}
