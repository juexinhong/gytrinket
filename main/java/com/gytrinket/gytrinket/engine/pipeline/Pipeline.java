package com.gytrinket.gytrinket.engine.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 有序阶段流水线。编译期/构建期把阶段排成数组，派发零分配。
 * <p>
 * 用途：伤害链等「按优先级依次处理、可短路、可跳过同阶段剩余项」的流程。
 * 语义：
 * <ul>
 *   <li>阶段按 priority 降序执行，同优先级保持注册顺序；</li>
 *   <li>返回 {@link Result#CANCEL} 立即结束整条流水线；</li>
 *   <li>返回 {@link Result#SKIP_REST_OF_STAGE} 跳过同 stageId 的后续阶段，继续下一个 stage；</li>
 *   <li>默认异常上抛；配置 {@link Builder#onError(BiConsumer)} 后按阶段隔离并继续。</li>
 * </ul>
 * 两个伤害链必须各自持有独立实例（护盾拦截链 / 最终伤害链阶段不同，不能合并）。
 */
public final class Pipeline<C> {

    public enum Result {
        CONTINUE,
        SKIP_REST_OF_STAGE,
        CANCEL
    }

    @FunctionalInterface
    public interface Stage<C> {
        Result apply(C context);
    }

    private static final Comparator<Entry<?>> PRIORITY_ORDER =
            (left, right) -> Integer.compare(right.priority, left.priority);

    private final Entry<C>[] entries;
    private final BiConsumer<String, RuntimeException> errorHandler;

    private Pipeline(Entry<C>[] entries, BiConsumer<String, RuntimeException> errorHandler) {
        this.entries = entries;
        this.errorHandler = errorHandler;
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    public void process(C context) {
        String skipStage = null;
        for (Entry<C> entry : entries) {
            if (skipStage != null) {
                if (entry.stageId.equals(skipStage)) {
                    continue;
                }
                skipStage = null;
            }
            Result result;
            try {
                result = entry.stage.apply(context);
            } catch (RuntimeException exception) {
                if (errorHandler == null) {
                    throw exception;
                }
                errorHandler.accept(entry.stageId, exception);
                continue;
            }
            if (result == Result.CANCEL) {
                return;
            }
            if (result == Result.SKIP_REST_OF_STAGE) {
                skipStage = entry.stageId;
            }
        }
    }

    public int size() {
        return entries.length;
    }

    public static final class Builder<C> {
        private final List<Entry<C>> entries = new ArrayList<>();
        private BiConsumer<String, RuntimeException> errorHandler;

        public Builder<C> stage(String stageId, int priority, Stage<C> stage) {
            entries.add(new Entry<>(stageId, priority, stage));
            return this;
        }

        public Builder<C> onError(BiConsumer<String, RuntimeException> handler) {
            this.errorHandler = handler;
            return this;
        }

        public Pipeline<C> build() {
            Entry<C>[] array = newEntries(entries.size());
            entries.toArray(array);
            Arrays.sort(array, PRIORITY_ORDER);
            return new Pipeline<>(array, errorHandler);
        }

        @SuppressWarnings("unchecked")
        private static <C> Entry<C>[] newEntries(int size) {
            return (Entry<C>[]) new Entry[size];
        }
    }

    private static final class Entry<C> {
        private final String stageId;
        private final int priority;
        private final Stage<C> stage;

        private Entry(String stageId, int priority, Stage<C> stage) {
            this.stageId = stageId;
            this.priority = priority;
            this.stage = stage;
        }
    }
}
