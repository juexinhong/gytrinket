package com.gytrinket.gytrinket.core.entity.construct;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ConstructType {
    private final String id;
    private final String name;
    private final Set<ConstructCategory> categories;
    private final Set<String> tags;
    private final int buildTime;
    private final double maxHealth;
    private final int maxCount;
    private final Class<? extends IConstruct> constructClass;

    private ConstructType(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.categories = builder.categories;
        this.tags = builder.tags != null ? Collections.unmodifiableSet(new HashSet<>(builder.tags)) : Collections.emptySet();
        this.buildTime = builder.buildTime;
        this.maxHealth = builder.maxHealth;
        this.maxCount = builder.maxCount;
        this.constructClass = builder.constructClass;
    }

    /** 获取唯一标识符 */
    public String getId() {
        return id;
    }

    /** 获取显示名称 */
    public String getName() {
        return name;
    }

    public Set<ConstructCategory> getCategories() {
        return categories;
    }

    public Set<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public boolean hasAllTags(Set<String> targetTags) {
        return tags.containsAll(targetTags);
    }

    public int getBuildTime() {
        return buildTime;
    }

    /** 获取最大生命值 */
    public double getMaxHealth() {
        return maxHealth;
    }

    /** 获取玩家持有数量上限 */
    public int getMaxCount() {
        return maxCount;
    }

    /** 获取对应的构造体实现类 */
    public Class<? extends IConstruct> getConstructClass() {
        return constructClass;
    }

    /**
     * 检查此构造体类型是否匹配目标类别
     *
     * @param targetCategories 目标类别集合
     * @return 如果匹配返回true
     */
    public boolean matchesCategories(Set<ConstructCategory> targetCategories) {
        return ConstructCategory.matches(targetCategories, this.categories);
    }

    /**
     * 创建类型构建器
     *
     * @param id 唯一标识符
     * @return 新的构建器实例
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String name;
        private Set<ConstructCategory> categories;
        private Set<String> tags;
        private int buildTime = 20;
        private double maxHealth = 20.0;
        private int maxCount = 3;
        private Class<? extends IConstruct> constructClass;

        public Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder categories(Set<ConstructCategory> categories) {
            this.categories = categories;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder tag(String tag) {
            if (this.tags == null) {
                this.tags = new HashSet<>();
            }
            this.tags.add(tag);
            return this;
        }

        public Builder buildTime(int buildTime) {
            this.buildTime = buildTime;
            return this;
        }

        public Builder maxHealth(double maxHealth) {
            this.maxHealth = maxHealth;
            return this;
        }

        public Builder maxCount(int maxCount) {
            this.maxCount = maxCount;
            return this;
        }

        public Builder constructClass(Class<? extends IConstruct> constructClass) {
            this.constructClass = constructClass;
            return this;
        }

        public ConstructType build() {
            return new ConstructType(this);
        }
    }
}