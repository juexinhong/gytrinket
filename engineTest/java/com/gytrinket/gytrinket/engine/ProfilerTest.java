package com.gytrinket.gytrinket.engine;

import com.gytrinket.gytrinket.engine.perf.TickProfiler;

final class ProfilerTest {

    private ProfilerTest() {
    }

    static void run(Checker checker) {
        startsDisabled(checker);
        recordsWhenEnabled(checker);
        supportsNestedScopes(checker);
        resetClearsSamples(checker);
        toleratesUnbalancedEnd(checker);
        TickProfiler.setEnabled(false);
    }

    private static void startsDisabled(Checker checker) {
        TickProfiler.setEnabled(false);
        TickProfiler.reset();
        TickProfiler.begin("disabled.scope");
        TickProfiler.end("disabled.scope");
        checker.check("profiler disabled => no samples", TickProfiler.sampleCount("disabled.scope") == 0);
        checker.check("profiler disabled => empty json", "{}".equals(TickProfiler.dumpJson()));
    }

    private static void recordsWhenEnabled(Checker checker) {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        for (int i = 0; i < 5; i++) {
            TickProfiler.begin("unit.tick");
            TickProfiler.end("unit.tick");
        }
        checker.check("profiler enabled => 5 samples", TickProfiler.sampleCount("unit.tick") == 5);
        String json = TickProfiler.dumpJson();
        checker.check("profiler json contains scope", json.contains("\"unit.tick\""));
        checker.check("profiler json contains p95", json.contains("\"p95_ms\""));
        checker.check("profiler json contains n=5", json.contains("\"n\":5"));
    }

    private static void supportsNestedScopes(Checker checker) {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        TickProfiler.begin("outer");
        TickProfiler.begin("inner");
        TickProfiler.end("inner");
        TickProfiler.end("outer");
        checker.check("nested inner recorded", TickProfiler.sampleCount("inner") == 1);
        checker.check("nested outer recorded", TickProfiler.sampleCount("outer") == 1);
    }

    private static void resetClearsSamples(Checker checker) {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        TickProfiler.begin("reset.me");
        TickProfiler.end("reset.me");
        TickProfiler.reset();
        checker.check("reset clears samples", TickProfiler.sampleCount("reset.me") == 0);
    }

    private static void toleratesUnbalancedEnd(Checker checker) {
        TickProfiler.setEnabled(true);
        TickProfiler.reset();
        TickProfiler.end("never.started");
        checker.check("unbalanced end is ignored", TickProfiler.sampleCount("never.started") == 0);
    }
}
