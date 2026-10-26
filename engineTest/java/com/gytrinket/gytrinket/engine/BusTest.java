package com.gytrinket.gytrinket.engine;

import com.gytrinket.gytrinket.engine.bus.Cancelable;
import com.gytrinket.gytrinket.engine.bus.GyBus;
import com.gytrinket.gytrinket.engine.bus.GyEvent;
import com.gytrinket.gytrinket.engine.bus.Subscription;

import java.util.ArrayList;
import java.util.List;

final class BusTest {

    private BusTest() {
    }

    static void run(Checker checker) {
        priorityOrder(checker);
        samePriorityKeepsRegistrationOrder(checker);
        cancelSkipsNormalListeners(checker);
        receiveCanceledStillRuns(checker);
        preCanceledEvent(checker);
        unsubscribe(checker);
        registerDuringPost(checker);
        listenerCount(checker);
    }

    private static void priorityOrder(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        bus.on(TestEvent.class, GyBus.LOW, event -> calls.add("low"));
        bus.on(TestEvent.class, GyBus.HIGHEST, event -> calls.add("highest"));
        bus.on(TestEvent.class, GyBus.HIGH, event -> calls.add("high"));
        bus.post(new TestEvent());
        checker.check("bus priority order", calls.equals(List.of("highest", "high", "low")));
    }

    private static void samePriorityKeepsRegistrationOrder(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        bus.on(TestEvent.class, GyBus.NORMAL, event -> calls.add("first"));
        bus.on(TestEvent.class, GyBus.NORMAL, event -> calls.add("second"));
        bus.post(new TestEvent());
        checker.check("bus same priority stable", calls.equals(List.of("first", "second")));
    }

    private static void cancelSkipsNormalListeners(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        bus.on(CancelableEvent.class, GyBus.HIGH, event -> {
            calls.add("cancel");
            event.setCanceled(true);
        });
        bus.on(CancelableEvent.class, GyBus.NORMAL, event -> calls.add("normal"));
        bus.post(new CancelableEvent());
        checker.check("bus cancel skips later normal listener", calls.equals(List.of("cancel")));
    }

    private static void receiveCanceledStillRuns(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        bus.on(CancelableEvent.class, GyBus.HIGH, event -> event.setCanceled(true));
        bus.on(CancelableEvent.class, GyBus.LOW, true, event -> calls.add("canceled-aware"));
        bus.post(new CancelableEvent());
        checker.check("bus receiveCanceled listener runs", calls.equals(List.of("canceled-aware")));
    }

    private static void preCanceledEvent(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        bus.on(CancelableEvent.class, GyBus.NORMAL, event -> calls.add("normal"));
        bus.on(CancelableEvent.class, GyBus.NORMAL, true, event -> calls.add("aware"));
        CancelableEvent event = new CancelableEvent();
        event.setCanceled(true);
        bus.post(event);
        checker.check("bus pre-canceled event", calls.equals(List.of("aware")));
    }

    private static void unsubscribe(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        Subscription subscription = bus.on(TestEvent.class, GyBus.NORMAL, event -> calls.add("gone"));
        subscription.unsubscribe();
        bus.post(new TestEvent());
        checker.check("bus unsubscribe", calls.isEmpty() && !subscription.isActive());
    }

    private static void registerDuringPost(Checker checker) {
        GyBus bus = new GyBus();
        List<String> calls = new ArrayList<>();
        bus.on(TestEvent.class, GyBus.NORMAL, event -> {
            calls.add("first");
            bus.on(TestEvent.class, GyBus.NORMAL, event2 -> calls.add("late"));
        });
        bus.post(new TestEvent());
        checker.check("bus snapshot does not see new listener", calls.equals(List.of("first")));
    }

    private static void listenerCount(Checker checker) {
        GyBus bus = new GyBus();
        bus.on(TestEvent.class, GyBus.NORMAL, event -> {
        });
        bus.on(TestEvent.class, GyBus.NORMAL, event -> {
        });
        checker.check("bus listener count", bus.listenerCount(TestEvent.class) == 2);
    }

    private static final class TestEvent implements GyEvent {
    }

    private static final class CancelableEvent implements GyEvent, Cancelable {
        private boolean canceled;

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }
    }
}
