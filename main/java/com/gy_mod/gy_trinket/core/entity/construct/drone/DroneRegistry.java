package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructTypeRegistry;

/**
 * 无人机构造体注册器
 * <p>
 * 实现构造体类型注册接口，用于注册无人机及相关类型。
 */
public class DroneRegistry implements ConstructTypeRegistry.IConstructRegistry {
    @Override
    public void register() {
        DroneConstructTypes.register();
    }
}