package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.world.entity.player.Player;

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
    private final IConstructFactory constructFactory;
    private final IEntityRestorer entityRestorer;

    private ConstructType(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.categories = builder.categories;
        this.tags = builder.tags != null ? Collections.unmodifiableSet(new HashSet<>(builder.tags)) : Collections.emptySet();
        this.buildTime = builder.buildTime;
        this.maxHealth = builder.maxHealth;
        this.maxCount = builder.maxCount;
        this.constructFactory = builder.constructFactory;
        this.entityRestorer = builder.entityRestorer;
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

    /** 获取构造体工厂 */
    public IConstructFactory getConstructFactory() {
        return constructFactory;
    }

    /** 获取实体恢复器 */
    public IEntityRestorer getEntityRestorer() {
        return entityRestorer;
    }

    /** 使用工厂创建构造体逻辑实例 */
    public IConstruct createConstruct(Player player) {
        if (constructFactory != null) {
            return constructFactory.create(player, this);
        }
        return null;
    }

    /** 是否注册了实体恢复器 */
    public boolean hasEntityRestorer() {
        return entityRestorer != null;
    }

    /**
     * 检查此构造体类型是否匹配目标类别
     */
    public boolean matchesCategories(Set<ConstructCategory> targetCategories) {
        return ConstructCategory.matches(targetCategories, this.categories);
    }

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
        private IConstructFactory constructFactory;
        private IEntityRestorer entityRestorer;

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

        public Builder constructFactory(IConstructFactory constructFactory) {
            this.constructFactory = constructFactory;
            return this;
        }

        public Builder entityRestorer(IEntityRestorer entityRestorer) {
            this.entityRestorer = entityRestorer;
            return this;
        }

        /** 兼容旧接口：从 Class 创建工厂（反射） */
        public Builder constructClass(Class<? extends IConstruct> constructClass) {
            this.constructFactory = (player, type) -> {
                try {
                    return constructClass.getConstructor(String.class, net.minecraft.world.entity.LivingEntity.class, double.class)
                            .newInstance(type.getId(), player, type.getMaxHealth());
                } catch (Exception e) {
                    throw new RuntimeException("无法创建构造体实例: " + constructClass.getName(), e);
                }
            };
            return this;
        }

        public ConstructType build() {
            return new ConstructType(this);
        }
    }
}