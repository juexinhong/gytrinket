package com.gytrinket.gytrinket.engine;

/**
 * P5 引擎测试入口：零外部依赖，直接跑在 JDK 上。
 * <p>
 * 由 Gradle 任务 {@code engineTest} 启动；失败时进程退出码为 1，进构建门禁。
 * 这个源集的编译 classpath 只有主源集输出、没有 MC/Forge，因此引擎层一旦被 MC 类型污染会直接编译失败。
 */
public final class EngineTestMain {

    private EngineTestMain() {
    }

    public static void main(String[] args) {
        Checker checker = new Checker();
        ProfilerTest.run(checker);
        BusTest.run(checker);
        PipelineTest.run(checker);
        AttributeMathTest.run(checker);

        System.out.println("[engineTest] checks=" + checker.checks() + " failures=" + checker.failures());
        if (checker.failures() > 0) {
            System.exit(1);
        }
    }
}
