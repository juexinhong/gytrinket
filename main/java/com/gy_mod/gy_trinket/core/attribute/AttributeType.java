package com.gy_mod.gy_trinket.core.attribute;

/**
 * 属性计算方法枚举
 * 定义了三种属性计算方式
 */
public enum AttributeType {
    /**
     * 底数方法
     * 所有属性值只做底数相加
     */
    BASE,

    /**
     * 百分比方法
     * 所有属性值做百分比相加，计算公式：1 + 所有值之和
     */
    PERCENT,

    /**
     * 独立乘区方法
     * 所有属性值做独立相乘，计算公式：(1 + 值1) * (1 + 值2) * ...
     */
    INDEPENDENT_MULTIPLY;

    /**
     * 判断该类型是否支持底数属性
     * @return 是否支持底数
     */
    public boolean supportsBase() {
        return this == BASE;
    }

    /**
     * 判断该类型是否支持百分比属性
     * @return 是否支持百分比
     */
    public boolean supportsPercent() {
        return this == PERCENT;
    }

    /**
     * 判断该类型是否支持独立乘区属性
     * @return 是否支持独立乘区
     */
    public boolean supportsIndependent() {
        return this == INDEPENDENT_MULTIPLY;
    }
}