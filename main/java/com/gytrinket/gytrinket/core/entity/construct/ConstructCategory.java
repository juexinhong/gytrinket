package com.gytrinket.gytrinket.core.entity.construct;

import java.util.*;

/**
 * 构造体类别系统
 * <p>
 * 类别用于标签式匹配，允许功能效果精准地作用于特定构造体组合。
 * <p>
 * 类别层级：
 * <ul>
 *   <li>基础类别：CONSTRUCT（所有构造体都具有）</li>
 *   <li>类型类别：WEAPON、SHIELD、OTHER（三选一）</li>
 *   <li>等级类别：ADVANCED、STANDARD、BASIC（三选一）</li>
 * </ul>
 * <p>
 * 匹配规则：
 * 目标类别必须全部包含于构造体类别中。例如：
 * <ul>
 *   <li>目标类别 [ADVANCED, WEAPON, CONSTRUCT] → 高阶武器构造体生效</li>
 *   <li>目标类别 [BASIC, CONSTRUCT] → 所有基础构造体生效</li>
 *   <li>目标类别 [CONSTRUCT] → 所有构造体生效</li>
 * </ul>
 */
public enum ConstructCategory {
    /** 所有构造体都具有的基础类别 */
    CONSTRUCT("construct"),

    /** 类型类别：武器 */
    WEAPON("weapon"),

    /** 类型类别：护盾 */
    SHIELD("shield"),

    /** 类型类别：其他 */
    OTHER("other"),

    /** 等级类别：高级 */
    ADVANCED("advanced"),

    /** 等级类别：标准 */
    STANDARD("standard"),

    /** 等级类别：基础 */
    BASIC("basic");

    /** 类别的唯一标识符 */
    private final String id;

    ConstructCategory(String id) {
        this.id = id;
    }

    /**
     * 获取类别的唯一标识符
     */
    public String getId() {
        return id;
    }

    /**
     * 根据ID查找对应的类别
     *
     * @param id 类别ID
     * @return 对应的类别，如果不存在返回null
     */
    public static ConstructCategory fromId(String id) {
        for (ConstructCategory category : values()) {
            if (category.id.equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 从逗号分隔的ID字符串解析出类别集合
     * <p>
     * 例如："advanced,weapon,construct" → {ADVANCED, WEAPON, CONSTRUCT}
     *
     * @param categoryIds 逗号分隔的类别ID字符串
     * @return 解析后的类别集合
     */
    public static Set<ConstructCategory> parseCategories(String categoryIds) {
        Set<ConstructCategory> categories = new HashSet<>();
        if (categoryIds == null || categoryIds.isEmpty()) {
            return categories;
        }
        String[] ids = categoryIds.split(",");
        for (String id : ids) {
            ConstructCategory category = fromId(id.trim());
            if (category != null) {
                categories.add(category);
            }
        }
        return categories;
    }

    /**
     * 检查目标类别是否与构造体类别匹配
     * <p>
     * 匹配规则：构造体类别必须包含所有目标类别中的元素
     *
     * @param targetCategories  目标类别集合（需要满足的条件）
     * @param constructCategories 构造体类别集合（实际拥有的类别）
     * @return 如果匹配返回true，否则返回false
     */
    public static boolean matches(Set<ConstructCategory> targetCategories, Set<ConstructCategory> constructCategories) {
        if (targetCategories == null || targetCategories.isEmpty()) {
            return true;
        }
        if (constructCategories == null || constructCategories.isEmpty()) {
            return false;
        }
        return constructCategories.containsAll(targetCategories);
    }

    /**
     * 创建武器类构造体的类别集合
     *
     * @param tier 等级
     * @return 包含CONSTRUCT、WEAPON和指定等级的类别集合
     */
    public static Set<ConstructCategory> createWeaponCategories(Tier tier) {
        Set<ConstructCategory> categories = new HashSet<>();
        categories.add(CONSTRUCT);
        categories.add(WEAPON);
        categories.add(tier.getCategory());
        return categories;
    }

    /**
     * 创建护盾类构造体的类别集合
     *
     * @param tier 等级
     * @return 包含CONSTRUCT、SHIELD和指定等级的类别集合
     */
    public static Set<ConstructCategory> createShieldCategories(Tier tier) {
        Set<ConstructCategory> categories = new HashSet<>();
        categories.add(CONSTRUCT);
        categories.add(SHIELD);
        categories.add(tier.getCategory());
        return categories;
    }

    /**
     * 创建其他类构造体的类别集合
     *
     * @param tier 等级
     * @return 包含CONSTRUCT、OTHER和指定等级的类别集合
     */
    public static Set<ConstructCategory> createOtherCategories(Tier tier) {
        Set<ConstructCategory> categories = new HashSet<>();
        categories.add(CONSTRUCT);
        categories.add(OTHER);
        categories.add(tier.getCategory());
        return categories;
    }

    /**
     * 等级枚举
     * <p>
     * 用于表示构造体的高阶、标准、基础三个等级
     */
    public enum Tier {
        /** 高级 */
        ADVANCED(ConstructCategory.ADVANCED),

        /** 标准 */
        STANDARD(ConstructCategory.STANDARD),

        /** 基础 */
        BASIC(ConstructCategory.BASIC);

        private final ConstructCategory category;

        Tier(ConstructCategory category) {
            this.category = category;
        }

        public ConstructCategory getCategory() {
            return category;
        }
    }
}