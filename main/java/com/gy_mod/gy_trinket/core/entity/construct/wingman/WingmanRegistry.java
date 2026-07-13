package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructTypeRegistry;

/**
 * 僚机构造体注册器
 */
public class WingmanRegistry implements ConstructTypeRegistry.IConstructRegistry {
    @Override
    public void register() {
        WingmanConstructTypes.register();
    }
}
