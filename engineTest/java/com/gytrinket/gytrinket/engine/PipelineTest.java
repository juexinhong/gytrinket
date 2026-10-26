package com.gytrinket.gytrinket.engine;

import com.gytrinket.gytrinket.engine.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.List;

final class PipelineTest {

    private PipelineTest() {
    }

    static void run(Checker checker) {
        priorityOrder(checker);
        cancelStops(checker);
        skipRestOfStage(checker);
        exceptionPropagatesByDefault(checker);
        errorHandlerIsolates(checker);
        size(checker);
    }

    private static void priorityOrder(Checker checker) {
        List<String> calls = new ArrayList<>();
        Pipeline<Object> pipeline = Pipeline.<Object>builder()
                .stage("low", 10, context -> {
                    calls.add("low");
                    return Pipeline.Result.CONTINUE;
                })
                .stage("high", 100, context -> {
                    calls.add("high");
                    return Pipeline.Result.CONTINUE;
                })
                .build();
        pipeline.process(new Object());
        checker.check("pipeline priority order", calls.equals(List.of("high", "low")));
    }

    private static void cancelStops(Checker checker) {
        List<String> calls = new ArrayList<>();
        Pipeline<Object> pipeline = Pipeline.<Object>builder()
                .stage("first", 100, context -> {
                    calls.add("first");
                    return Pipeline.Result.CANCEL;
                })
                .stage("second", 10, context -> {
                    calls.add("second");
                    return Pipeline.Result.CONTINUE;
                })
                .build();
        pipeline.process(new Object());
        checker.check("pipeline cancel stops", calls.equals(List.of("first")));
    }

    private static void skipRestOfStage(Checker checker) {
        List<String> calls = new ArrayList<>();
        Pipeline<Object> pipeline = Pipeline.<Object>builder()
                .stage("stage-a", 100, context -> {
                    calls.add("a1");
                    return Pipeline.Result.SKIP_REST_OF_STAGE;
                })
                .stage("stage-a", 90, context -> {
                    calls.add("a2");
                    return Pipeline.Result.CONTINUE;
                })
                .stage("stage-b", 80, context -> {
                    calls.add("b1");
                    return Pipeline.Result.CONTINUE;
                })
                .build();
        pipeline.process(new Object());
        checker.check("pipeline skip rest of stage", calls.equals(List.of("a1", "b1")));
    }

    private static void exceptionPropagatesByDefault(Checker checker) {
        Pipeline<Object> pipeline = Pipeline.<Object>builder()
                .stage("boom", 100, context -> {
                    throw new IllegalStateException("boom");
                })
                .build();
        boolean thrown = false;
        try {
            pipeline.process(new Object());
        } catch (IllegalStateException expected) {
            thrown = true;
        }
        checker.check("pipeline exception propagates", thrown);
    }

    private static void errorHandlerIsolates(Checker checker) {
        List<String> calls = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Pipeline<Object> pipeline = Pipeline.<Object>builder()
                .stage("boom", 100, context -> {
                    throw new IllegalStateException("boom");
                })
                .stage("after", 10, context -> {
                    calls.add("after");
                    return Pipeline.Result.CONTINUE;
                })
                .onError((stageId, exception) -> errors.add(stageId + ":" + exception.getMessage()))
                .build();
        pipeline.process(new Object());
        checker.check("pipeline error handler isolates and continues", calls.equals(List.of("after")));
        checker.check("pipeline error handler receives stage id", errors.equals(List.of("boom:boom")));
    }

    private static void size(Checker checker) {
        Pipeline<Object> pipeline = Pipeline.<Object>builder()
                .stage("a", 1, context -> Pipeline.Result.CONTINUE)
                .stage("b", 2, context -> Pipeline.Result.CONTINUE)
                .build();
        checker.check("pipeline size", pipeline.size() == 2);
    }
}
