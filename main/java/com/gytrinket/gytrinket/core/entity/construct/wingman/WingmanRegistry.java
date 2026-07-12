package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.core.entity.construct.ConstructTypeRegistry;

/**
 * 僚机构造体注册器
 */
public class WingmanRegistry implements ConstructTypeRegistry.IConstructRegistry {
    @Override
    public void register() {
        WingmanConstructTypes.register();
    }
}
