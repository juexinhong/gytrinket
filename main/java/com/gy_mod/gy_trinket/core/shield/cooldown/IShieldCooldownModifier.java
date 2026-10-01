package com.gy_mod.gy_trinket.core.shield.cooldown;

/**
 * 护盾冷却修饰器接口
 * <p>
 * 提供冷却推进过程的扩展点，所有修饰器按优先级依次执行。
 */
public interface IShieldCooldownModifier {

    /** 修饰器标识名称 */
    String getName();

    /** 优先级：数值越小越先执行 */
    default int getPriority() {
        return 0;
    }

    /** 每刻冷却推进前触发 */
    default void onPreTick(CooldownContext context) {}

    /** 每刻冷却推进后触发 */
    default void onPostTick(CooldownContext context) {}
}
