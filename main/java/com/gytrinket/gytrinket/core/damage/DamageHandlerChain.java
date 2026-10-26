package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.engine.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 护盾拦截链（{@code LivingAttackEvent} 阶段）。
 * <p>
 * 与其他链的关系：本链处理「受击最开始」的护盾归属、取消与伤害转化；
 * {@link com.gytrinket.gytrinket.core.damage_last.LastDamageHandlerChain} 处理扣血前的最终伤害，
 * 两条链阶段不同，<b>不能合并</b>。P5 只共用 {@link Pipeline} 实现原语。
 * <p>
 * 顺序：按 handler 优先级降序，同优先级保持注册顺序；context 取消后短路。
 */
public class DamageHandlerChain {

    private static final DamageHandlerChain INSTANCE = new DamageHandlerChain();

    private final List<DamageHandler> handlers = new ArrayList<>();
    private volatile Pipeline<DamageContext> pipeline = buildPipeline();

    private DamageHandlerChain() {
    }

    public static DamageHandlerChain getInstance() {
        return INSTANCE;
    }

    public synchronized void registerHandler(DamageHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(DamageHandler::getPriority).reversed());
        pipeline = buildPipeline();
    }

    public synchronized void unregisterHandler(DamageHandler handler) {
        handlers.remove(handler);
        pipeline = buildPipeline();
    }

    public void process(DamageContext context) {
        pipeline.process(context);
    }

    /** 诊断用：按实际执行顺序输出 handler 类名与优先级。 */
    public synchronized List<String> describeHandlers() {
        List<String> description = new ArrayList<>(handlers.size());
        for (DamageHandler handler : handlers) {
            description.add(handler.getClass().getSimpleName() + "@" + handler.getPriority());
        }
        return description;
    }

    private Pipeline<DamageContext> buildPipeline() {
        Pipeline.Builder<DamageContext> builder = Pipeline.builder();
        int index = 0;
        for (DamageHandler handler : handlers) {
            String stageId = "damage." + handler.getClass().getSimpleName() + "." + index++;
            builder.stage(stageId, handler.getPriority(), context -> {
                handler.handle(context);
                return context.isCanceled() ? Pipeline.Result.CANCEL : Pipeline.Result.CONTINUE;
            });
        }
        return builder.build();
    }
}
