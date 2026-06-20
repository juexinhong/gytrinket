package com.gytrinket.gytrinket.core.damage_last;

/**
 * 最终伤害处理器接口
 * <p>
 * 该接口是最终伤害处理责任链模式的核心，用于定义在 LivingDamageEvent 阶段的伤害处理逻辑。
 * <p>
 * 使用方式：
 * <ol>
 *   <li>实现此接口，创建具体的处理器类</li>
 *   <li>实现 {@link #handle(LastDamageContext)} 方法编写处理逻辑</li>
 *   <li>实现 {@link #getPriority()} 方法指定处理器优先级</li>
 *   <li>通过 {@link LastDamageHandlerChain#registerHandler(LastDamageHandler)} 注册处理器</li>
 * </ol>
 */
public interface LastDamageHandler {

    /**
     * 处理最终伤害
     *
     * @param context 最终伤害上下文对象，包含伤害事件的信息
     */
    void handle(LastDamageContext context);

    /**
     * 获取处理器优先级
     * 优先级越高的处理器越先执行
     *
     * @return 处理器优先级，数值越大越先执行
     */
    int getPriority();
}