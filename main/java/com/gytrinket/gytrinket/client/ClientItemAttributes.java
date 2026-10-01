package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

import java.util.Map;

/**
 * 客户端物品属性查询（带回退链）。
 * <p>
 * 取值优先级：
 * <ol>
 *   <li>AttributeManager 静态属性表（服务端经 ConfigDefsSyncPayload 下发，权威：含运行时覆盖层）；</li>
 *   <li>数据包 item_definitions 的 attributes 段——该注册表随数据包自动同步到客户端，
 *       不依赖自定义网络包；静态表未收到同步时兜底（重启进服后未打开过配置界面也能取到属性）。</li>
 * </ol>
 * 仅客户端调用（tooltip、弹射物渲染缩放等），专用服务器不加载本类。
 */
public final class ClientItemAttributes {

    private ClientItemAttributes() {}

    public static ItemAttributeConfig getItemAttributes(String itemId) {
        ItemAttributeConfig cached = AttributeManager.getItemAttributes(itemId);
        if (cached != null && !cached.getAttributes().isEmpty()) {
            return cached;
        }
        Map<String, Double> attrs = DefsManager.clientEffectiveItemAttributes(clientRegistryAccess(), itemId);
        if (attrs.isEmpty()) {
            return cached;
        }
        ItemAttributeConfig config = new ItemAttributeConfig(itemId);
        attrs.forEach(config::addAttribute);
        return config;
    }

    private static RegistryAccess clientRegistryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        return mc.getConnection() != null ? mc.getConnection().registryAccess() : null;
    }
}
