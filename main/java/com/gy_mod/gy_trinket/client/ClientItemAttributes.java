package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.ItemAttributeConfig;
import com.gy_mod.gy_trinket.core.defs.DefsManager;

import java.util.Map;

/**
 * 客户端物品属性查询（带回退链）。
 * 取值优先级：
 * 1. AttributeManager 静态属性表（服务端经 ConfigDefsSyncMessage 登录/编辑时下发，权威，含运行时覆写层）
 * 2. 本地 item_definitions 的 attributes 段（客户端惰性加载自身 JAR/资源包数据，静态表未同步时兜底）
 * 仅客户端调用，专用服务器不加载本类。
 */
public final class ClientItemAttributes {
    private ClientItemAttributes() {}

    public static ItemAttributeConfig getItemAttributes(String itemId) {
        ItemAttributeConfig cached = AttributeManager.getItemAttributes(itemId);
        if (cached != null && !cached.getAttributes().isEmpty()) {
            return cached;
        }
        Map<String, Double> attrs = DefsManager.clientEffectiveItemAttributes(itemId);
        if (attrs.isEmpty()) {
            return cached;
        }
        ItemAttributeConfig config = new ItemAttributeConfig(itemId);
        attrs.forEach(config::addAttribute);
        return config;
    }
}
