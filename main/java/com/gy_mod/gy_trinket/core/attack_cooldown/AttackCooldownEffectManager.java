package com.gy_mod.gy_trinket.core.attack_cooldown;

import com.gy_mod.gy_trinket.gytrinket;

import java.util.*;

/**
 * 攻击冷却效果管理器
 * <p>
 * 负责管理所有攻击冷却效果的注册、注销和执行。
 * 使用单例模式，提供静态方法供外部调用。
 * <p>
 * 效果执行顺序按优先级排序，优先级数值越小越先执行。
 * <p>
 * 内置注册的效果：
 * - {@link CooldownSlowdownEffect} - 冷却减慢效果
 */
public class AttackCooldownEffectManager {

    /** 效果注册表：效果名称 -> 效果实例 */
    private static final Map<String, IAttackCooldownEffect> EFFECTS = new LinkedHashMap<>();

    /** 排序后的效果列表，用于快速遍历执行 */
    private static List<IAttackCooldownEffect> sortedEffects;

    /**
     * 静态初始化：注册内置效果
     */
    static {
        registerEffect(new CooldownSlowdownEffect());
    }

    /**
     * 私有构造函数，防止实例化
     */
    private AttackCooldownEffectManager() {}

    /**
     * 注册攻击冷却效果
     * <p>
     * 效果会自动按优先级排序
     *
     * @param effect 要注册的效果实例
     */
    public static void registerEffect(IAttackCooldownEffect effect) {
        EFFECTS.put(effect.getName(), effect);
        sortEffects();
        gytrinket.LOGGER.info("注册攻击冷却效果: {}", effect.getName());
    }

    /**
     * 注销攻击冷却效果
     *
     * @param effectName 要注销的效果名称
     * @return true表示成功注销，false表示未找到该效果
     */
    public static boolean unregisterEffect(String effectName) {
        boolean removed = EFFECTS.remove(effectName) != null;
        if (removed) {
            sortEffects();
            gytrinket.LOGGER.info("移除攻击冷却效果: {}", effectName);
        }
        return removed;
    }

    /**
     * 对已注册的效果按优先级排序
     */
    private static void sortEffects() {
        sortedEffects = new ArrayList<>(EFFECTS.values());
        sortedEffects.sort(Comparator.comparingInt(IAttackCooldownEffect::getPriority));
    }

    /**
     * 应用所有激活的攻击冷却效果
     * <p>
     * 遍历所有已注册的效果，对激活的效果调用 {@link IAttackCooldownEffect#applyEffect}
     *
     * @param context 攻击冷却上下文
     */
    public static void applyEffects(AttackCooldownContext context) {
        if (sortedEffects == null) {
            sortEffects();
        }

        for (IAttackCooldownEffect effect : sortedEffects) {
            if (effect.isActive(context.getPlayerUUID(), context.getAttackStrength())) {
                effect.applyEffect(context);
            }
        }
    }

    /**
     * 通知所有效果攻击已开始
     * <p>
     * 调用所有效果的 {@link IAttackCooldownEffect#onAttackStarted} 方法
     *
     * @param playerUUID 玩家UUID
     */
    public static void onAttackStarted(UUID playerUUID) {
        if (sortedEffects == null) {
            sortEffects();
        }

        for (IAttackCooldownEffect effect : sortedEffects) {
            effect.onAttackStarted(playerUUID);
        }
    }

    /**
     * 通知所有效果攻击已完成
     * <p>
     * 调用所有效果的 {@link IAttackCooldownEffect#onAttackCompleted} 方法
     *
     * @param playerUUID 玩家UUID
     */
    public static void onAttackCompleted(UUID playerUUID) {
        if (sortedEffects == null) {
            sortEffects();
        }

        for (IAttackCooldownEffect effect : sortedEffects) {
            effect.onAttackCompleted(playerUUID);
        }
    }

    /**
     * 获取指定名称的效果
     *
     * @param name 效果名称
     * @return 效果实例，如果未找到返回null
     */
    public static IAttackCooldownEffect getEffect(String name) {
        return EFFECTS.get(name);
    }

    /**
     * 获取所有已注册的效果
     *
     * @return 不可修改的效果集合
     */
    public static Collection<IAttackCooldownEffect> getAllEffects() {
        return Collections.unmodifiableCollection(EFFECTS.values());
    }

    /**
     * 判断是否存在指定名称的效果
     *
     * @param name 效果名称
     * @return true表示存在，false表示不存在
     */
    public static boolean hasEffect(String name) {
        return EFFECTS.containsKey(name);
    }
}