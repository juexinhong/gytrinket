package com.gytrinket.gytrinket.engine;

import com.gytrinket.gytrinket.engine.perf.TickProfiler;

/**
 * P5 引擎测试入口：零外部依赖，直接跑在 JDK 上。
 * <p>
 * 由 Gradle 任务 {@code engineTest} 启动；失败时进程退出码为 1，进构建门禁。
 * 这个源集的编译 classpath 只有主源集输出、没有 MC/Forge，因此引擎层一旦被 MC 类型污染会直接编译失败。
 */
public final class EngineTestMain {

    private static int checks;
    private static int failures;

    private EngineTestMain() {
    }

    public static void main(String[] args) {
        profilerStartsDisabled();
        profilerRecordsWhenEnabled();
        profilerSupportsNestedScopes();
        profilerResetClearsSamples();
        profilerToleratesUnbalancedEnd();

        TickProfiler.setEnabled(false);
        System.out.println("[engineTest] checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void profilerStartsDisabled() {
        TickProfiler.setEnabled(false);
        TickProfiler.reset();
        TickProfiler.begin("disabled.scope");
        TickProfiler.end("disabled.scope");
        check("profiler disabled => no samples", TickProfiler.sampleCount("disabled.scope") == 0);
        check("profiler disabled => empty json", "{}".equals(TickProfiler.dumpJson()));
    }

    private static void profilerRecordsWhenEnabled() {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        for (int i = 0; i < 5; i++) {
            TickProfiler.begin("unit.tick");
            TickProfiler.end("unit.tick");
        }
        check("profiler enabled => 5 samples", TickProfiler.sampleCount("unit.tick") == 5);
        String json = TickProfiler.dumpJson();
        check("profiler json contains scope", json.contains("\"unit.tick\""));
        check("profiler json contains p95", json.contains("\"p95_ms\""));
        check("profiler json contains n=5", json.contains("\"n\":5"));
    }

    private static void profilerSupportsNestedScopes() {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        TickProfiler.begin("outer");
        TickProfiler.begin("inner");
        TickProfiler.end("inner");
        TickProfiler.end("outer");
        check("nested inner recorded", TickProfiler.sampleCount("inner") == 1);
        check("nested outer recorded", TickProfiler.sampleCount("outer") == 1);
    }

    private static void profilerResetClearsSamples() {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        TickProfiler.begin("reset.me");
        TickProfiler.end("reset.me");
        TickProfiler.reset();
        check("reset clears samples", TickProfiler.sampleCount("reset.me") == 0);
    }

    private static void profilerToleratesUnbalancedEnd() {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        TickProfiler.end("never.started");
        check("unbalanced end is ignored", TickProfiler.sampleCount("never.started") == 0);
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
