package com.gy_mod.gy_trinket.core.attack_cooldown;

import java.util.UUID;

/**
 * 攻击冷却效果接口
 * <p>
 * 定义攻击冷却期间可以应用的效果的标准接口。
 * 通过实现此接口，可以在玩家攻击冷却期间添加各种自定义效果。
 * <p>
 * 效果激活条件：当玩家攻击强度(attackStrength) < 0.9f时认为处于攻击冷却状态
 * <p>
 * 使用示例：
 * <pre>{@code
 * public class MyEffect implements IAttackCooldownEffect {
 *     @Override
 *     public String getName() { return "my_effect"; }
 *     
 *     @Override
 *     public void applyEffect(AttackCooldownContext context) {
 *         // 在攻击冷却期间应用效果
 *     }
 * }
 * }</pre>
 */
public interface IAttackCooldownEffect {

    /**
     * 获取效果名称
     * <p>
     * 用于标识和注册效果，应保证唯一性
     *
     * @return 效果名称
     */
    String getName();

    /**
     * 获取效果优先级
     * <p>
     * 数值越小越先执行，用于控制多个效果的执行顺序
     * 默认优先级为0
     *
     * @return 优先级数值
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 判断效果是否激活
     * <p>
     * 默认实现：当攻击强度 < 0.9f 时激活
     * 可根据需要重写此方法实现自定义激活条件
     *
     * @param playerUUID    玩家UUID
     * @param attackStrength 攻击强度(0.0f ~ 1.0f)，值越低表示冷却越严重
     * @return true表示效果应激活，false表示不激活
     */
    default boolean isActive(UUID playerUUID, float attackStrength) {
        return attackStrength < 0.9f;
    }

    /**
     * 应用效果
     * <p>
     * 在攻击冷却期间每刻调用，用于应用具体的效果逻辑
     *
     * @param context 攻击冷却上下文，包含玩家状态和效果累积值
     */
    void applyEffect(AttackCooldownContext context);

    /**
     * 攻击开始时触发
     * <p>
     * 当玩家开始攻击时调用，可用于初始化效果状态
     *
     * @param playerUUID 玩家UUID
     */
    default void onAttackStarted(UUID playerUUID) {}

    /**
     * 攻击完成时触发
     * <p>
     * 当玩家攻击冷却结束时调用，可用于清理效果状态
     *
     * @param playerUUID 玩家UUID
     */
    default void onAttackCompleted(UUID playerUUID) {}
}