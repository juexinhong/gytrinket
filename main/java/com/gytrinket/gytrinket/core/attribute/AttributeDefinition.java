package com.gytrinket.gytrinket.core.attribute;

/**
 * 属性定义类
 * 用于在 Config 中注册单个属性的配置
 */
public class AttributeDefinition {
    // 属性名称
    private final String name;
    // 计算方法类型
    private final AttributeType type;
    // 属性组名称（用于分组计算）
    private final String group;
    // 该属性是否启用
    private boolean enabled;
    // 默认值
    private double defaultValue;

    /**
     * 构造函数
     * @param name 属性名称
     * @param type 计算方法类型
     */
    public AttributeDefinition(String name, AttributeType type) {
        this(name, type, null);
    }

    /**
     * 构造函数（带属性组）
     * @param name 属性名称
     * @param type 计算方法类型
     * @param group 属性组名称
     */
    public AttributeDefinition(String name, AttributeType type, String group) {
        this.name = name;
        this.type = type;
        this.group = group;
        this.enabled = true;
        this.defaultValue = getDefaultValueForType(type);
    }

    /**
     * 根据类型获取默认值
     */
    private double getDefaultValueForType(AttributeType type) {
        switch (type) {
            case PERCENT:
            case INDEPENDENT_MULTIPLY:
                return 1.0;
            default:
                return 0.0;
        }
    }

    /**
     * 获取属性名称
     * @return 属性名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取计算方法类型
     * @return 计算方法类型
     */
    public AttributeType getType() {
        return type;
    }

    /**
     * 获取属性组名称
     * @return 属性组名称，如果没有分组返回 null
     */
    public String getGroup() {
        return group;
    }

    /**
     * 检查是否属于指定属性组
     * @param groupName 属性组名称
     * @return 是否属于该属性组
     */
    public boolean belongsToGroup(String groupName) {
        return group != null && group.equals(groupName);
    }

    /**
     * 检查属性是否启用
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置属性是否启用
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认值
     * @return 默认值
     */
    public double getDefaultValue() {
        return defaultValue;
    }

    /**
     * 设置默认值
     * @param defaultValue 默认值
     */
    public void setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
    }
}
