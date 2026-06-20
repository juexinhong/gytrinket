package com.gytrinket.gytrinket.core.attribute;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品属性配置类
 * 用于定义物品提供的属性
 */
public class ItemAttributeConfig {
    // 物品注册名
    private final String itemId;
    // 该物品提供的属性映射：属性名 -> 属性值
    private final Map<String, Double> attributes = new HashMap<>();

    /**
     * 构造函数
     * @param itemId 物品注册名
     */
    public ItemAttributeConfig(String itemId) {
        this.itemId = itemId;
    }

    /**
     * 获取物品注册名
     * @return 物品注册名
     */
    public String getItemId() {
        return itemId;
    }

    /**
     * 添加属性值
     * @param attributeName 属性名（可以是带后缀的完整名，如 "speed_percent"）
     * @param value 属性值
     * @return this，用于链式调用
     */
    public ItemAttributeConfig addAttribute(String attributeName, double value) {
        attributes.put(attributeName, value);
        return this;
    }

    /**
     * 获取所有属性
     * @return 属性映射
     */
    public Map<String, Double> getAttributes() {
        return new HashMap<>(attributes);
    }

    /**
     * 获取指定属性值
     * @param attributeName 属性名
     * @return 属性值，如果不存在返回 0.0
     */
    public double getAttribute(String attributeName) {
        return attributes.getOrDefault(attributeName, 0.0);
    }

    /**
     * 检查是否有指定属性
     * @param attributeName 属性名
     * @return 是否存在
     */
    public boolean hasAttribute(String attributeName) {
        return attributes.containsKey(attributeName);
    }

    public void removeAttribute(String attributeName) {
        attributes.remove(attributeName);
    }

    public static ItemAttributeConfig of(String itemId) {
        return new ItemAttributeConfig(itemId);
    }
}
