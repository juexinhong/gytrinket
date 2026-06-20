package com.gytrinket.gytrinket.core.damage;

/**
 * 伤害处理器接口
 * <p>
 * 该接口是伤害处理责任链模式的核心，用于定义各种伤害处理逻辑。
 * 实现该接口可以创建自定义的伤害处理器，如护盾处理、护甲处理、效果处理等。
 * <p>
 * 使用方式：
 * <ol>
 *   <li>实现此接口，创建具体的处理器类</li>
 *   <li>实现 {@link #handle(DamageContext)} 方法编写处理逻辑</li>
 *   <li>实现 {@link #getPriority()} 方法指定处理器优先级</li>
 *   <li>通过 {@link DamageHandlerChain#registerHandler(DamageHandler)} 注册处理器</li>
 * </ol>
 * <p>
 * 处理器按优先级从高到低依次执行，高优先级处理器会先处理伤害。
 * 如果当前处理器将伤害取消（context.setCanceled(true)），后续处理器将跳过执行。
 */
public interface DamageHandler {

    /**
     * 处理伤害
     * <p>
     * 在此方法中实现具体的伤害处理逻辑，如：
     * <ul>
     *   <li>护盾伤害抵挡</li>
     *   <li>护甲伤害减免</li>
     *   <li>伤害增强或削弱</li>
     *   <li>特殊效果触发</li>
     * </ul>
     *
     * @param context 伤害上下文对象，包含伤害的所有信息和状态
     */
    void handle(DamageContext context);

    /**
     * 获取处理器优先级
     * <p>
     * 优先级越高的处理器越先执行。
     * 数值越大优先级越高，建议使用1000的倍数来留出扩展空间。
     * <p>
     * 示例优先级：
     * <ul>
     *   <li>1000 - 护盾处理（最高优先级，需要最先抵挡伤害）</li>
     *   <li>500 - 护甲处理</li>
     *   <li>0 - 默认处理（最低优先级）</li>
     * </ul>
     *
     * @return 处理器优先级，数值越大越先执行
     */
    int getPriority();
}