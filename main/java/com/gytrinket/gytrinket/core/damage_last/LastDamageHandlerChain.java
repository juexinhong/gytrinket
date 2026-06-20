package com.gytrinket.gytrinket.core.damage_last;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 最终伤害处理器责任链管理器
 * <p>
 * 单例模式，负责管理和执行所有的最终伤害处理器
 */
public class LastDamageHandlerChain {

    private static final LastDamageHandlerChain INSTANCE = new LastDamageHandlerChain();
    private final List<LastDamageHandler> handlers = new ArrayList<>();
    private boolean initialized = false;

    private LastDamageHandlerChain() {}

    public static LastDamageHandlerChain getInstance() {
        return INSTANCE;
    }

    public void registerHandler(LastDamageHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(LastDamageHandler::getPriority).reversed());
    }

    public void unregisterHandler(LastDamageHandler handler) {
        handlers.remove(handler);
    }

    public void process(LastDamageContext context) {
        for (LastDamageHandler handler : handlers) {
            if (context.isCanceled()) {
                break;
            }
            handler.handle(context);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}