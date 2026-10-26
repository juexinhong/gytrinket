package com.gytrinket.gytrinket.engine.bus;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * 引擎内部事件总线：按运行时精确事件类派发，无反射、无注解扫描。
 * <p>
 * 设计要点（对应《迁移规划》§4.3）：
 * <ul>
 *   <li>「事件类 → 监听器数组」用 {@link ClassValue} 缓存，派发就是数组遍历；</li>
 *   <li>注册时 copy-on-write 生成新的有序数组（优先级降序、同优先级保持注册顺序）；</li>
 *   <li>取消语义与 Forge 对齐：事件取消后，未声明 {@code receiveCanceled} 的监听器跳过；</li>
 *   <li>只在初始化/重载期注册，不在 tick 热路径动态注册。</li>
 * </ul>
 * 线程模型：注册可与派发并发（派发读 volatile 快照）；监听器自身需保证线程安全。
 */
public final class GyBus {

    public static final int HIGHEST = 400;
    public static final int HIGH = 300;
    public static final int NORMAL = 200;
    public static final int LOW = 100;
    public static final int LOWEST = 0;

    private static final Comparator<Entry<?>> PRIORITY_ORDER =
            (left, right) -> Integer.compare(right.priority, left.priority);

    private final ClassValue<DispatchList<?>> lists = new ClassValue<>() {
        @Override
        protected DispatchList<?> computeValue(Class<?> type) {
            return new DispatchList<>();
        }
    };

    public <E extends GyEvent> Subscription on(Class<E> type, int priority, Listener<E> listener) {
        return on(type, priority, false, listener);
    }

    public <E extends GyEvent> Subscription on(Class<E> type, int priority, boolean receiveCanceled,
                                               Listener<E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        Entry<E> entry = new Entry<>(priority, receiveCanceled, listener);
        listFor(type).add(entry);
        return entry;
    }

    /**
     * 派发事件，返回同一个事件对象，方便调用方读取取消状态。
     */
    public <E extends GyEvent> E post(E event) {
        Objects.requireNonNull(event, "event");
        @SuppressWarnings("unchecked")
        Class<E> type = (Class<E>) event.getClass();
        listFor(type).dispatch(event);
        return event;
    }

    /** 诊断/测试用：当前活跃监听器数量。 */
    public <E extends GyEvent> int listenerCount(Class<E> type) {
        return listFor(type).activeCount();
    }

    @SuppressWarnings("unchecked")
    private <E extends GyEvent> DispatchList<E> listFor(Class<E> type) {
        return (DispatchList<E>) lists.get(type);
    }

    private static final class DispatchList<E extends GyEvent> {
        private volatile Entry<E>[] entries = newEntries(0);

        synchronized void add(Entry<E> entry) {
            entry.owner = this;
            Entry<E>[] next = Arrays.copyOf(entries, entries.length + 1);
            next[entries.length] = entry;
            Arrays.sort(next, PRIORITY_ORDER);
            entries = next;
        }

        synchronized void remove(Entry<E> entry) {
            Entry<E>[] snapshot = entries;
            int index = -1;
            for (int i = 0; i < snapshot.length; i++) {
                if (snapshot[i] == entry) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                return;
            }
            Entry<E>[] next = newEntries(snapshot.length - 1);
            System.arraycopy(snapshot, 0, next, 0, index);
            System.arraycopy(snapshot, index + 1, next, index, snapshot.length - index - 1);
            entries = next;
        }

        int activeCount() {
            int count = 0;
            for (Entry<E> entry : entries) {
                if (entry.active) {
                    count++;
                }
            }
            return count;
        }

        void dispatch(E event) {
            Entry<E>[] snapshot = entries;
            boolean canceled = event instanceof Cancelable cancelable && cancelable.isCanceled();
            for (Entry<E> entry : snapshot) {
                if (!entry.active) {
                    continue;
                }
                if (canceled && !entry.receiveCanceled) {
                    continue;
                }
                entry.listener.accept(event);
                if (!canceled && event instanceof Cancelable cancelable && cancelable.isCanceled()) {
                    canceled = true;
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static <E extends GyEvent> Entry<E>[] newEntries(int size) {
            return (Entry<E>[]) new Entry[size];
        }
    }

    private static final class Entry<E extends GyEvent> implements Subscription {
        private final int priority;
        private final boolean receiveCanceled;
        private final Listener<E> listener;
        private volatile boolean active = true;
        private DispatchList<E> owner;

        private Entry(int priority, boolean receiveCanceled, Listener<E> listener) {
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.listener = listener;
        }

        @Override
        public void unsubscribe() {
            if (!active) {
                return;
            }
            active = false;
            DispatchList<E> currentOwner = owner;
            if (currentOwner != null) {
                currentOwner.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
