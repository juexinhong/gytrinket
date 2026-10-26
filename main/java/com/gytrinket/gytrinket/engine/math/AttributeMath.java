package com.gytrinket.gytrinket.engine.math;

import java.util.Map;

/**
 * 属性合并数学（纯函数，无 MC 依赖）。
 * <p>
 * 四类属性的语义：
 * <ul>
 *   <li>BASE：静态底数累加；</li>
 *   <li>PERCENT：静态侧为 {@code 1 + Σ值}，动态侧为 {@code Σ值}，最终 {@code 静态 + 动态}；</li>
 *   <li>INDEPENDENT_MULTIPLY：两侧各自 {@code Π(1 + 值)}，最终 {@code 静态 × 动态}；</li>
 *   <li>POST_ADD：两侧各自求和，静态 + 动态（不受乘区缩放）。</li>
 * </ul>
 */
public final class AttributeMath {

    private AttributeMath() {
    }

    /** 静态侧空集默认值。 */
    public static double defaultValue(AttributeType type) {
        return switch (type) {
            case BASE, POST_ADD -> 0.0D;
            case PERCENT, INDEPENDENT_MULTIPLY -> 1.0D;
        };
    }

    /** 动态侧空集默认值（百分比侧不加 1）。 */
    public static double dynamicDefaultValue(AttributeType type) {
        return switch (type) {
            case BASE, PERCENT, POST_ADD -> 0.0D;
            case INDEPENDENT_MULTIPLY -> 1.0D;
        };
    }

    /** 合并静态侧与动态侧。 */
    public static double combine(double staticValue, double dynamicValue, AttributeType type) {
        return switch (type) {
            case BASE, PERCENT, POST_ADD -> staticValue + dynamicValue;
            case INDEPENDENT_MULTIPLY -> staticValue * dynamicValue;
        };
    }

    /** 静态侧合计（PERCENT 含 {@code 1 +}）。 */
    public static double staticTotal(AttributeType type, Map<String, Double> values) {
        return switch (type) {
            case BASE, POST_ADD -> sum(values);
            case PERCENT -> 1.0D + sum(values);
            case INDEPENDENT_MULTIPLY -> product(values);
        };
    }

    /** 动态侧合计（PERCENT 不含 {@code 1 +}）。 */
    public static double dynamicTotal(AttributeType type, Map<String, Double> values) {
        return switch (type) {
            case BASE, PERCENT, POST_ADD -> sum(values);
            case INDEPENDENT_MULTIPLY -> product(values);
        };
    }

    /** 动态侧合计，排除 key 以指定前缀开头的提供者。 */
    public static double dynamicTotalExcluding(AttributeType type, Map<String, Double> values, String excludePrefix) {
        return switch (type) {
            case BASE, PERCENT, POST_ADD -> sumExcluding(values, excludePrefix);
            case INDEPENDENT_MULTIPLY -> productExcluding(values, excludePrefix);
        };
    }

    private static double sum(Map<String, Double> values) {
        double total = 0.0D;
        for (double value : values.values()) {
            total += value;
        }
        return total;
    }

    private static double product(Map<String, Double> values) {
        double total = 1.0D;
        for (double value : values.values()) {
            total *= 1.0D + value;
        }
        return total;
    }

    private static double sumExcluding(Map<String, Double> values, String excludePrefix) {
        double total = 0.0D;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (!entry.getKey().startsWith(excludePrefix)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static double productExcluding(Map<String, Double> values, String excludePrefix) {
        double total = 1.0D;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (!entry.getKey().startsWith(excludePrefix)) {
                total *= 1.0D + entry.getValue();
            }
        }
        return total;
    }
}
