package com.gytrinket.gytrinket.engine;

/**
 * 零依赖断言器：只用于 engineTest harness。
 */
final class Checker {

    private int checks;
    private int failures;

    void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    void checkEquals(String name, double expected, double actual, double epsilon) {
        check(name + " (expected=" + expected + ", actual=" + actual + ")", Math.abs(expected - actual) <= epsilon);
    }

    int checks() {
        return checks;
    }

    int failures() {
        return failures;
    }
}
