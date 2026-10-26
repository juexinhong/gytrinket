package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;

import java.util.Set;

/**
 * 客户端模块：客户端初始化目前由 {@code client.ModClient} 的 {@code @EventBusSubscriber}
 * 自行接管；此模块只用于在启动清单里保留客户端分层位置与依赖关系。
 */
public final class ClientModule implements GyModule {

    @Override
    public String id() {
        return "client";
    }

    @Override
    public Set<String> requires() {
        return Set.of("core.registry", "core.network");
    }
}
