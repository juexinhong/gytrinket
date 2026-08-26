package com.gy_mod.gy_trinket.core.ghost_fuselage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幽灵机身客户端数据缓存
 * <p>
 * 存储服务端同步的隐身进度（按实体ID），供客户端渲染器使用。
 */
public class GhostFuselageClientData {
    private static final Map<Integer, Float> STEALTH_PROGRESS = new ConcurrentHashMap<>();

    /**
     * 获取指定实体的隐身进度
     */
    public static float getStealthProgress(int entityId) {
        return STEALTH_PROGRESS.getOrDefault(entityId, 0f);
    }

    /**
     * 设置指定实体的隐身进度
     */
    public static void setStealthProgress(int entityId, float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress <= 0f) {
            STEALTH_PROGRESS.remove(entityId);
        } else {
            STEALTH_PROGRESS.put(entityId, progress);
        }
    }

    /**
     * 清除所有数据
     */
    public static void clearAll() {
        STEALTH_PROGRESS.clear();
    }
}

