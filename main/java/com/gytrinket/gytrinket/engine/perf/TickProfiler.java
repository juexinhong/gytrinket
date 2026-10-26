package com.gytrinket.gytrinket.engine.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级分层耗时采样器。
 * <p>
 * 设计约束（对应《迁移规划》§4.7）：
 * <ul>
 *   <li>关闭时每次 begin/end 只有一次 volatile 读，无分配；</li>
 *   <li>开启后按线程保存嵌套栈，样本写入固定容量环形数组，不产生每 tick 垃圾；</li>
 *   <li>只在服务端线程/客户端线程本地读写，{@link #dumpJson()} 时才复制样本。</li>
 * </ul>
 * 由 observer 的 {@code /gytrinket perf start|stop|reset|dump} 控制；正式 jar 默认关闭。
 */
public final class TickProfiler {

    private static final int CAPACITY = 4096;
    private static final int INITIAL_DEPTH = 8;

    private static volatile boolean enabled = false;

    private static final Map<String, Recorder> RECORDERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

    private TickProfiler() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 开启一个采样区间；必须与同名 {@link #end(String)} 成对。 */
    public static void begin(String scope) {
        if (!enabled) {
            return;
        }
        FRAMES.get().push(scope, System.nanoTime());
    }

    /** 结束一个采样区间并记录耗时。 */
    public static void end(String scope) {
        if (!enabled) {
            return;
        }
        long start = FRAMES.get().pop(scope);
        if (start == 0L) {
            return;
        }
        recorder(scope).record(System.nanoTime() - start);
    }

    public static void reset() {
        RECORDERS.clear();
        FRAMES.remove();
    }

    public static int sampleCount(String scope) {
        Recorder recorder = RECORDERS.get(scope);
        return recorder == null ? 0 : recorder.count();
    }

    /** 输出一行 JSON：{scope: {n, p50_ms, p95_ms, p99_ms, mean_ms, max_ms}}。 */
    public static String dumpJson() {
        List<String> scopes = new ArrayList<>(RECORDERS.keySet());
        scopes.sort(Comparator.naturalOrder());

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String scope : scopes) {
            long[] samples = RECORDERS.get(scope).snapshot();
            if (samples.length == 0) {
                continue;
            }
            Arrays.sort(samples);
            double mean = 0.0D;
            for (long sample : samples) {
                mean += sample;
            }
            mean /= samples.length;

            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(scope)).append("\":{")
                    .append("\"n\":").append(samples.length)
                    .append(",\"p50_ms\":").append(ms(percentile(samples, 50)))
                    .append(",\"p95_ms\":").append(ms(percentile(samples, 95)))
                    .append(",\"p99_ms\":").append(ms(percentile(samples, 99)))
                    .append(",\"mean_ms\":").append(ms(mean))
                    .append(",\"max_ms\":").append(ms(samples[samples.length - 1]))
                    .append('}');
        }
        return sb.append('}').toString();
    }

    private static Recorder recorder(String scope) {
        return RECORDERS.computeIfAbsent(scope, key -> new Recorder());
    }

    private static long percentile(long[] sorted, int percent) {
        int index = (int) Math.ceil(sorted.length * (percent / 100.0D)) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    private static String ms(double nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 每个线程一个嵌套栈，容量按需增长，不参与全局同步。 */
    private static final class Frame {
        private long[] starts = new long[INITIAL_DEPTH];
        private String[] names = new String[INITIAL_DEPTH];
        private int depth;

        void push(String scope, long start) {
            if (depth == starts.length) {
                int next = starts.length * 2;
                starts = Arrays.copyOf(starts, next);
                names = Arrays.copyOf(names, next);
            }
            starts[depth] = start;
            names[depth] = scope;
            depth++;
        }

        long pop(String scope) {
            if (depth == 0) {
                return 0L;
            }
            int index = depth - 1;
            if (!scope.equals(names[index])) {
                // 容忍不严格配对：回退到最近的同名区间，并丢弃其上的残帧。
                index = -1;
                for (int i = depth - 1; i >= 0; i--) {
                    if (scope.equals(names[i])) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    return 0L;
                }
            }
            long start = starts[index];
            names[index] = null;
            depth = index;
            return start;
        }
    }

    /** 单 scope 的固定容量环形样本。 */
    private static final class Recorder {
        private final long[] samples = new long[CAPACITY];
        private int count;
        private int cursor;

        synchronized void record(long nanos) {
            samples[cursor] = nanos;
            cursor = (cursor + 1) % CAPACITY;
            if (count < CAPACITY) {
                count++;
            }
        }

        synchronized int count() {
            return count;
        }

        synchronized long[] snapshot() {
            long[] copy = new long[count];
            System.arraycopy(samples, 0, copy, 0, count);
            return copy;
        }
    }
}
