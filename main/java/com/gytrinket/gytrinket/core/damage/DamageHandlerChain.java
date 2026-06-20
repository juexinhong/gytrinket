package com.gytrinket.gytrinket.core.damage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 伤害处理器责任链管理器
 * <p>
 * 该类是责任链模式的核心实现，负责管理和执行所有的伤害处理器。
 * 采用单例模式，确保整个游戏中只有一个责任链实例。
 * <p>
 * 功能特点：
 * <ul>
 *   <li>单例模式，保证全局唯一性</li>
 *   <li>自动按优先级排序处理器</li>
 *   <li>支持动态注册和注销处理器</li>
 *   <li>当伤害被取消时自动跳过后续处理器</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * // 注册处理器
 * DamageHandlerChain.getInstance().registerHandler(new ShieldHandler());
 *
 * // 处理伤害
 * DamageContext context = new DamageContext(...);
 * DamageHandlerChain.getInstance().process(context);
 * </pre>
 */
public class DamageHandlerChain {

    /** 单例实例 */
    private static final DamageHandlerChain INSTANCE = new DamageHandlerChain();

    /** 处理器列表，按优先级排序 */
    private final List<DamageHandler> handlers = new ArrayList<>();

    /**
     * 私有构造函数，确保单例模式
     */
    private DamageHandlerChain() {}

    /**
     * 获取单例实例
     *
     * @return 责任链管理器实例
     */
    public static DamageHandlerChain getInstance() {
        return INSTANCE;
    }

    /**
     * 注册伤害处理器
     * <p>
     * 注册后处理器会自动按优先级从高到低排序。
     * 相同优先级的处理器按注册顺序排列。
     *
     * @param handler 要注册的伤害处理器
     */
    public void registerHandler(DamageHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(DamageHandler::getPriority).reversed());
    }

    /**
     * 注销伤害处理器
     *
     * @param handler 要注销的伤害处理器
     */
    public void unregisterHandler(DamageHandler handler) {
        handlers.remove(handler);
    }

    /**
     * 处理伤害上下文
     * <p>
     * 遍历所有已注册的处理器，按优先级从高到低依次执行。
     * 如果context被标记为取消状态，则停止执行后续处理器。
     *
     * @param context 伤害上下文对象
     */
    public void process(DamageContext context) {
        for (DamageHandler handler : handlers) {
            if (context.isCanceled()) {
                break;
            }
            handler.handle(context);
        }
    }
}