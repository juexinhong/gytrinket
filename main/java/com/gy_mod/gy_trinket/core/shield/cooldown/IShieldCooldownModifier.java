package com.gy_mod.gy_trinket.core.shield.cooldown;

/**
 * 护盾冷却修饰器接口
 * <p>
 * 提供冷却过程的扩展点，可以在特定时机修改冷却行为。
 * <p>
 * 修饰器应用场景：
 * <ul>
 *   <li>受到攻击时减少冷却进度</li>
 *   <li>特定buff增加冷却速度</li>
 *   <li>护盾破裂时重置冷却</li>
 *   <li>冷却完成时触发额外效果</li>
 * </ul>
 * <p>
 * 使用装饰器模式，所有修饰器按优先级依次执行。
 */
public interface IShieldCooldownModifier {

    /**
     * 获取修饰器名称
     *
     * @return 修饰器标识名称
     */
    String getName();

    /**
     * 获取修饰器优先级
     * <p>
     * 数值越小越先执行，负数表示在核心逻辑之前执行。
     *
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 每刻更新前触发
     * <p>
     * 在冷却进度更新之前调用，可以修改冷却状态。
     *
     * @param state     当前冷却状态
     * @param context    冷却上下文
     * @return true表示阻止默认更新，false表示继续默认更新
     */
    default boolean onPreTick(ShieldCooldownManager.CooldownData state, CooldownContext context) {
        return false;
    }

    /**
     * 每刻更新后触发
     * <p>
     * 在冷却进度更新之后调用，可以执行额外操作。
     *
     * @param state     当前冷却状态
     * @param context    冷却上下文
     */
    default void onPostTick(ShieldCooldownManager.CooldownData state, CooldownContext context) {}

    /**
     * 护盾破裂时触发
     * <p>
     * 当护盾值从大于0降至0时调用。
     *
     * @param state     当前冷却状态
     * @param context    冷却上下文
     */
    default void onShieldBreak(ShieldCooldownManager.CooldownData state, CooldownContext context) {}

    /**
     * 冷却完成时触发
     * <p>
     * 当冷却进度达到最大值时调用。
     *
     * @param state     当前冷却状态
     * @param context    冷却上下文
     */
    default void onCooldownComplete(ShieldCooldownManager.CooldownData state, CooldownContext context) {}

    /**
     * 玩家受到伤害时触发
     * <p>
     * 当玩家受到伤害时调用，可以用于减少冷却进度。
     *
     * @param state     当前冷却状态
     * @param context    冷却上下文
     * @param damage    受到的伤害值
     */
    default void onDamageTaken(ShieldCooldownManager.CooldownData state, CooldownContext context, float damage) {}
}