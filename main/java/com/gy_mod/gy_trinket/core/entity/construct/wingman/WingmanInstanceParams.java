package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * 僚机实例参数（实例化机制：每个来源物品一个实例，各用各的物品级参数）
 * <p>
 * 机制集合 {@link #MECHANIC_SET}：装备中声明该集合的物品（或 Config 僚机模块物品）
 * 各自成为一个僚机实例，实例键为物品 ID。实例参数包括构建时间、基础生命值、
 * 基础伤害（爆破弹）、基础数量、攻击间隔、攻击范围，未覆盖的参数回退 Config 默认值。
 */
public final class WingmanInstanceParams {
    /** 僚机构建模块机制集合（实例来源物品声明此集合） */
    public static final String MECHANIC_SET = "wingman_module_items";

    // ===== 物品级参数键 =====
    /** 构建时间（tick） */
    public static final String KEY_BUILD_TIME = "build_time";
    /** 基础生命值 */
    public static final String KEY_BASE_HEALTH = "base_health";
    /** 基础伤害（爆破弹） */
    public static final String KEY_BASE_DAMAGE = "base_damage";
    /** 基础数量上限（属性修正在此之上叠加） */
    public static final String KEY_BASE_COUNT = "base_count";
    /** 攻击间隔（秒）；0 = 未定义，沿用 Config 默认 */
    public static final String KEY_ATTACK_INTERVAL = "attack_interval";
    /** 攻击范围（格）；0 = 未定义，沿用 Config 默认 */
    public static final String KEY_ATTACK_RANGE = "attack_range";

    /** 默认构建时间：与 WingmanConstructTypes 注册的类型级构建时间一致（500tick = 25秒） */
    private static final double DEFAULT_BUILD_TIME = 500.0D;

    private WingmanInstanceParams() {}

    /**
     * 解析实例物品的指定参数值（覆盖层优先，未覆盖回退默认值）
     *
     * @param server   服务端实例
     * @param itemId   实例物品 ID（实例键）
     * @param paramKey 参数键（见 {@code KEY_*} 常量）
     * @return 参数值
     */
    public static double resolve(@Nullable MinecraftServer server, String itemId, String paramKey) {
        double fallback = fallbackFor(paramKey);
        return DefsManager.resolveMechanicValueForItem(server, itemId, MECHANIC_SET, paramKey, fallback);
    }

    /**
     * 客户端镜像：解析实例物品指定参数值（与 {@link #resolve} 同语义，读客户端覆盖表）。
     * 供逻辑客户端在无法获取服务端覆盖表时（如渲染/朝向决策）使用，保证客户端表现与服务端一致。
     */
    public static double resolveClient(String itemId, String paramKey) {
        double fallback = fallbackFor(paramKey);
        return DefsManager.clientResolveMechanicValueForItem(itemId, MECHANIC_SET, paramKey, fallback);
    }

    /** 各参数未覆盖时的默认值（服务端/客户端共用） */
    private static double fallbackFor(String paramKey) {
        return switch (paramKey) {
            case KEY_BUILD_TIME -> DEFAULT_BUILD_TIME;
            case KEY_BASE_HEALTH -> Config.getWingmanBaseHealth();
            case KEY_BASE_DAMAGE -> Config.getWingmanExplosiveDamage();
            case KEY_BASE_COUNT -> Config.getWingmanMaxCount();
            case KEY_ATTACK_INTERVAL -> 0.0D;
            case KEY_ATTACK_RANGE -> 0.0D;
            default -> 0.0D;
        };
    }

    /** 实例构建时间（tick） */
    public static int getBuildTime(MinecraftServer server, String itemId) {
        return Math.max(1, (int) resolve(server, itemId, KEY_BUILD_TIME));
    }

    /** 实例基础数量上限（可为 0：0 = 该实例无法构建，除非属性修正使有效上限 > 0） */
    public static int getBaseCount(MinecraftServer server, String itemId) {
        return Math.max(0, (int) resolve(server, itemId, KEY_BASE_COUNT));
    }
}
