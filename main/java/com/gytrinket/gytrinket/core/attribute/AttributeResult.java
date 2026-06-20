package com.gytrinket.gytrinket.core.attribute;

import java.util.HashMap;
import java.util.Map;

/**
 * 属性计算结果类
 * 存储计算后的属性值
 */
public class AttributeResult {
    // 存储最终计算结果的映射
    private final Map<String, Double> finalValues = new HashMap<>();
    // 存储底数值的映射
    private final Map<String, Double> baseValues = new HashMap<>();
    // 存储百分比值的映射
    private final Map<String, Double> percentValues = new HashMap<>();
    // 存储独立乘区值的映射（独立相乘）
    private final Map<String, Double> independentValues = new HashMap<>();
    // 记录属性的计算方法类型
    private final Map<String, AttributeType> attributeTypes = new HashMap<>();

    /**
     * 注册属性类型
     * @param attributeName 属性名
     * @param type 属性类型
     */
    public void registerAttributeType(String attributeName, AttributeType type) {
        attributeTypes.put(attributeName, type);
    }

    /**
     * 添加底数值
     * @param attributeName 属性名
     * @param type 属性类型
     * @param value 底数值
     */
    public void addBaseValue(String attributeName, AttributeType type, double value) {
        attributeTypes.putIfAbsent(attributeName, type);
        baseValues.merge(attributeName, value, Double::sum);
    }

    /**
     * 添加百分比值
     * @param attributeName 属性名
     * @param type 属性类型
     * @param value 百分比值（如 0.1 表示 10%）
     */
    public void addPercentValue(String attributeName, AttributeType type, double value) {
        attributeTypes.putIfAbsent(attributeName, type);
        percentValues.merge(attributeName, value, Double::sum);
    }

    /**
     * 添加独立乘区值（独立相乘）
     * @param attributeName 属性名
     * @param type 属性类型
     * @param value 独立乘区值（如 0.1 表示 10%）
     */
    public void addIndependentValue(String attributeName, AttributeType type, double value) {
        attributeTypes.putIfAbsent(attributeName, type);
        double current = independentValues.getOrDefault(attributeName, 1.0);
        independentValues.put(attributeName, current * (1.0 + value));
    }

    /**
     * 计算最终结果
     * 根据属性类型应用不同的计算公式
     */
    public void calculate() {
        // 处理所有属性
        for (Map.Entry<String, AttributeType> entry : attributeTypes.entrySet()) {
            String attrName = entry.getKey();
            AttributeType type = entry.getValue();
            double result;

            switch (type) {
                case BASE:
                    // 底数方法：直接使用底数值相加
                    result = baseValues.getOrDefault(attrName, 0.0);
                    break;
                case PERCENT:
                    // 百分比方法：1 + 所有百分比值之和
                    result = 1.0 + percentValues.getOrDefault(attrName, 0.0);
                    break;
                case INDEPENDENT_MULTIPLY:
                    // 独立乘区方法：所有值独立相乘
                    result = independentValues.getOrDefault(attrName, 1.0);
                    break;
                default:
                    result = 0.0;
            }

            finalValues.put(attrName, result);
        }
    }

    /**
     * 获取最终计算结果
     * @param attributeName 属性名
     * @return 计算后的值，如果没有则返回 0.0
     */
    public double getFinalValue(String attributeName) {
        return finalValues.getOrDefault(attributeName, 0.0);
    }

    /**
     * 检查是否有该属性
     * @param attributeName 属性名
     * @return 是否存在
     */
    public boolean hasAttribute(String attributeName) {
        return finalValues.containsKey(attributeName);
    }

    /**
     * 获取所有最终值
     * @return 属性名到最终值的映射
     */
    public Map<String, Double> getAllFinalValues() {
        return new HashMap<>(finalValues);
    }

    /**
     * 直接设置属性值
     * @param attributeName 属性名
     * @param value 属性值
     */
    public void setAttribute(String attributeName, double value) {
        finalValues.put(attributeName, value);
    }

    /**
     * 清空所有值
     */
    public void clear() {
        finalValues.clear();
        baseValues.clear();
        percentValues.clear();
        independentValues.clear();
        attributeTypes.clear();
    }
}
