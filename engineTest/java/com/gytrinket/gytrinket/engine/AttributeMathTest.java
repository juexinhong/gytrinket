package com.gytrinket.gytrinket.engine;

import com.gytrinket.gytrinket.engine.math.AttributeMath;
import com.gytrinket.gytrinket.engine.math.AttributeType;

import java.util.LinkedHashMap;
import java.util.Map;

final class AttributeMathTest {

    private static final double EPSILON = 1.0E-9D;

    private AttributeMathTest() {
    }

    static void run(Checker checker) {
        defaults(checker);
        staticTotals(checker);
        dynamicTotals(checker);
        combine(checker);
        excluding(checker);
    }

    private static void defaults(Checker checker) {
        checker.checkEquals("math default BASE", 0.0D, AttributeMath.defaultValue(AttributeType.BASE), EPSILON);
        checker.checkEquals("math default PERCENT", 1.0D, AttributeMath.defaultValue(AttributeType.PERCENT), EPSILON);
        checker.checkEquals("math default INDEP", 1.0D, AttributeMath.defaultValue(AttributeType.INDEPENDENT_MULTIPLY), EPSILON);
        checker.checkEquals("math default POST", 0.0D, AttributeMath.defaultValue(AttributeType.POST_ADD), EPSILON);
        checker.checkEquals("math dyn default BASE", 0.0D, AttributeMath.dynamicDefaultValue(AttributeType.BASE), EPSILON);
        checker.checkEquals("math dyn default PERCENT", 0.0D, AttributeMath.dynamicDefaultValue(AttributeType.PERCENT), EPSILON);
        checker.checkEquals("math dyn default INDEP", 1.0D, AttributeMath.dynamicDefaultValue(AttributeType.INDEPENDENT_MULTIPLY), EPSILON);
        checker.checkEquals("math dyn default POST", 0.0D, AttributeMath.dynamicDefaultValue(AttributeType.POST_ADD), EPSILON);
    }

    private static void staticTotals(Checker checker) {
        checker.checkEquals("math static BASE sum", 3.0D, AttributeMath.staticTotal(AttributeType.BASE, map(1.0D, 2.0D)), EPSILON);
        checker.checkEquals("math static PERCENT 1+sum", 1.3D, AttributeMath.staticTotal(AttributeType.PERCENT, map(0.1D, 0.2D)), EPSILON);
        checker.checkEquals("math static INDEP product", 3.0D, AttributeMath.staticTotal(AttributeType.INDEPENDENT_MULTIPLY, map(0.5D, 1.0D)), EPSILON);
        checker.checkEquals("math static POST sum", 3.0D, AttributeMath.staticTotal(AttributeType.POST_ADD, map(1.0D, 2.0D)), EPSILON);
        checker.checkEquals("math static empty PERCENT", 1.0D, AttributeMath.staticTotal(AttributeType.PERCENT, Map.of()), EPSILON);
        checker.checkEquals("math static empty INDEP", 1.0D, AttributeMath.staticTotal(AttributeType.INDEPENDENT_MULTIPLY, Map.of()), EPSILON);
    }

    private static void dynamicTotals(Checker checker) {
        checker.checkEquals("math dyn BASE sum", 3.0D, AttributeMath.dynamicTotal(AttributeType.BASE, map(1.0D, 2.0D)), EPSILON);
        checker.checkEquals("math dyn PERCENT sum", 0.3D, AttributeMath.dynamicTotal(AttributeType.PERCENT, map(0.1D, 0.2D)), EPSILON);
        checker.checkEquals("math dyn INDEP product", 3.0D, AttributeMath.dynamicTotal(AttributeType.INDEPENDENT_MULTIPLY, map(0.5D, 1.0D)), EPSILON);
        checker.checkEquals("math dyn POST sum", 3.0D, AttributeMath.dynamicTotal(AttributeType.POST_ADD, map(1.0D, 2.0D)), EPSILON);
        checker.checkEquals("math dyn empty PERCENT", 0.0D, AttributeMath.dynamicTotal(AttributeType.PERCENT, Map.of()), EPSILON);
    }

    private static void combine(Checker checker) {
        checker.checkEquals("math combine BASE", 7.0D, AttributeMath.combine(3.0D, 4.0D, AttributeType.BASE), EPSILON);
        checker.checkEquals("math combine PERCENT", 1.6D, AttributeMath.combine(1.3D, 0.3D, AttributeType.PERCENT), EPSILON);
        checker.checkEquals("math combine INDEP", 6.0D, AttributeMath.combine(3.0D, 2.0D, AttributeType.INDEPENDENT_MULTIPLY), EPSILON);
        checker.checkEquals("math combine POST", 7.0D, AttributeMath.combine(3.0D, 4.0D, AttributeType.POST_ADD), EPSILON);
    }

    private static void excluding(Checker checker) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("ns_a", 1.0D);
        values.put("ns_b", 2.0D);
        values.put("other_c", 3.0D);
        checker.checkEquals("math exclude sum", 3.0D, AttributeMath.dynamicTotalExcluding(AttributeType.BASE, values, "ns_"), EPSILON);
        checker.checkEquals("math exclude product", 4.0D, AttributeMath.dynamicTotalExcluding(AttributeType.INDEPENDENT_MULTIPLY, values, "ns_"), EPSILON);
    }

    private static Map<String, Double> map(double first, double second) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("a", first);
        values.put("b", second);
        return values;
    }
}
