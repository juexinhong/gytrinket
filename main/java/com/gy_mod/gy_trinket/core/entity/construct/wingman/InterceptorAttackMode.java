package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 拦截机攻击模式注册表
 * <p>
 * 每个攻击模式关联一个序列化名称和翻译键。
 * 实际攻击逻辑由 {@link attack_mode.InterceptorWeaponMode} 实现，
 * 通过 {@link attack_mode.InterceptorAttackModeManager#getCurrentWeaponMode} 获取。
 * <p>
 * 新增攻击模式只需调用 {@link #register} 注册即可。
 */
public class InterceptorAttackMode {

    private static final Map<String, InterceptorAttackMode> MODES = new LinkedHashMap<>();

    // 内置攻击模式
    public static final InterceptorAttackMode MELEE = register("melee");
    public static final InterceptorAttackMode BOW = register("bow");

    private final String serializedName;

    private InterceptorAttackMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * 注册一个攻击模式
     *
     * @param serializedName 序列化名称（同时也是武器模式的查找键）
     * @return 注册的攻击模式实例
     */
    public static InterceptorAttackMode register(String serializedName) {
        if (MODES.containsKey(serializedName)) {
            throw new IllegalArgumentException("Duplicate interceptor attack mode: " + serializedName);
        }
        InterceptorAttackMode mode = new InterceptorAttackMode(serializedName);
        MODES.put(serializedName, mode);
        return mode;
    }

    /**
     * 通过序列化名称获取攻击模式
     *
     * @param name 序列化名称
     * @return 攻击模式，未找到时返回 MELEE
     */
    public static InterceptorAttackMode byName(String name) {
        return MODES.getOrDefault(name, MELEE);
    }

    /**
     * 获取所有已注册的攻击模式（按注册顺序）
     */
    public static InterceptorAttackMode[] values() {
        return MODES.values().toArray(new InterceptorAttackMode[0]);
    }

    /**
     * 获取序列化名称
     */
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * 获取翻译键
     */
    public String getTranslationKey() {
        return "screen.gytrinket.interceptor_attack_mode_" + serializedName;
    }

    /**
     * 循环切换到下一个攻击模式
     */
    public InterceptorAttackMode next() {
        InterceptorAttackMode[] all = values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == this) {
                return all[(i + 1) % all.length];
            }
        }
        return MELEE;
    }

    @Override
    public String toString() {
        return "InterceptorAttackMode{" + serializedName + "}";
    }
}

