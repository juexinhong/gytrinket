package com.gytrinket.gytrinket.observer;

import com.gytrinket.gytrinket.bootstrap.GyBuses;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内部事件 post 计数（仅 observer 开发模组使用）。
 * 通过 {@link GyBuses#INTERNAL} 的 post hook 统计，不额外注册监听器，避免污染 listener 数量。
 */
final class EventProbe {

    private static final Map<Class<?>, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    private static boolean registered;

    private EventProbe() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GyBuses.INTERNAL.setPostHook((type, event) ->
                COUNTS.computeIfAbsent(type, key -> new AtomicLong()).incrementAndGet());
    }

    static synchronized void reset() {
        COUNTS.clear();
    }

    static synchronized String dump() {
        Map<String, Long> sorted = new TreeMap<>();
        for (Map.Entry<Class<?>, AtomicLong> entry : COUNTS.entrySet()) {
            sorted.put(entry.getKey().getSimpleName(), entry.getValue().get());
        }
        return sorted.toString();
    }
}
