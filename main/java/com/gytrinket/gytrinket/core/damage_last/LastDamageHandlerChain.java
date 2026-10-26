package com.gytrinket.gytrinket.core.damage_last;

import com.gytrinket.gytrinket.engine.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 最终伤害链（{@code LivingDamageEvent} 阶段）。
 * <p>
 * 与其他链的关系：本链在护甲/附魔/抗性减伤之后、扣血之前修改最终数值；
 * {@link com.gytrinket.gytrinket.core.damage.DamageHandlerChain} 处理受击最开始的护盾拦截。
 * 两条链阶段不同，<b>不能合并</b>。P5 只共用 {@link Pipeline} 实现原语。
 * <p>
 * 顺序：按 handler 优先级降序，同优先级保持注册顺序；context 取消后短路。
 */
public class LastDamageHandlerChain {

    private static final LastDamageHandlerChain INSTANCE = new LastDamageHandlerChain();

    private final List<LastDamageHandler> handlers = new ArrayList<>();
    private volatile Pipeline<LastDamageContext> pipeline = buildPipeline();
    private boolean initialized = false;

    private LastDamageHandlerChain() {
    }

    public static LastDamageHandlerChain getInstance() {
        return INSTANCE;
    }

    public synchronized void registerHandler(LastDamageHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(LastDamageHandler::getPriority).reversed());
        pipeline = buildPipeline();
    }

    public synchronized void unregisterHandler(LastDamageHandler handler) {
        handlers.remove(handler);
        pipeline = buildPipeline();
    }

    public void process(LastDamageContext context) {
        pipeline.process(context);
    }

    /** 诊断用：按实际执行顺序输出 handler 类名与优先级。 */
    public synchronized List<String> describeHandlers() {
        List<String> description = new ArrayList<>(handlers.size());
        for (LastDamageHandler handler : handlers) {
            description.add(handler.getClass().getSimpleName() + "@" + handler.getPriority());
        }
        return description;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    private Pipeline<LastDamageContext> buildPipeline() {
        Pipeline.Builder<LastDamageContext> builder = Pipeline.builder();
        int index = 0;
        for (LastDamageHandler handler : handlers) {
            String stageId = "last_damage." + handler.getClass().getSimpleName() + "." + index++;
            builder.stage(stageId, handler.getPriority(), context -> {
                handler.handle(context);
                return context.isCanceled() ? Pipeline.Result.CANCEL : Pipeline.Result.CONTINUE;
            });
        }
        return builder.build();
    }
}
