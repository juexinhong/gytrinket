package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.core.attribute.AttributeType;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 构造体属性名解析器
 * <p>
 * 解析以 {@code construct_} 为前缀的属性名，提取匹配信息。
 * <p>
 * 属性名格式：{@code construct_{tokens...}_{effect}_{valueType}}
 * <p>
 * 解析规则（乱序，按 token 分类）：
 * <ul>
 *   <li>构造体类型：wingman, drone, swarm（无则匹配所有类型）</li>
 *   <li>效果：health, damage, attack_speed, count, build_speed</li>
 *   <li>值类型：base, percent, independent</li>
 *   <li>类别：basic, standard, advanced, weapon, shield, other</li>
 *   <li>类别排除：non_weapon, non_shield, non_other</li>
 *   <li>标签：assault, defense, commander, evolution, mothership 等任意其他 token</li>
 * </ul>
 * <p>
 * 示例：
 * <ul>
 *   <li>{@code construct_health_base} → 所有构造体，生命，BASE</li>
 *   <li>{@code construct_wingman_health_percent} → 僚机，生命，PERCENT</li>
 *   <li>{@code construct_drone_assault_attack_speed_percent} → 无人机+突击标签，攻速，PERCENT</li>
 *   <li>{@code construct_basic_non_weapon_count_percent} → 基础+非武器，数量，PERCENT</li>
 * </ul>
 */
public class ConstructAttributeNameParser {

    private static final String PREFIX = "construct_";

    /** 已知的效果 token（多词组合需特殊处理） */
    private static final Set<String> EFFECT_TOKENS = Set.of(
            "health", "damage", "count", "build_speed", "attack_speed", "weapon_attack_speed", "explosive_count"
    );

    /** 已知的值类型 token */
    private static final Set<String> VALUE_TYPE_TOKENS = Set.of(
            "base", "percent", "independent"
    );

    /** 已知的构造体类型 token */
    private static final Set<String> TYPE_TOKENS = Set.of(
            "wingman", "drone", "swarm"
    );

    /** 已知的类别 token */
    private static final Set<String> CATEGORY_TOKENS = Set.of(
            "basic", "standard", "advanced", "weapon", "shield", "other"
    );

    /** 已知的类别排除 token → 排除的类别 */
    private static final Map<String, ConstructCategory> EXCLUSION_TOKENS = Map.of(
            "non_weapon", ConstructCategory.WEAPON,
            "non_shield", ConstructCategory.SHIELD,
            "non_other", ConstructCategory.OTHER
    );

    /** 来源标识 token：仅表示属性来源，不参与匹配 */
    private static final Set<String> SOURCE_TOKENS = Set.of(
            "evolution", "mothership", "overflow"
    );

    private ConstructAttributeNameParser() {}

    /**
     * 判断属性名是否为构造体属性
     */
    public static boolean isConstructAttribute(String attributeName) {
        return attributeName.startsWith(PREFIX);
    }

    /**
     * 解析构造体属性名
     *
     * @param attributeName 属性名（必须以 construct_ 开头）
     * @return 解析结果，如果不是构造体属性返回 null
     */
    @Nullable
    public static ParsedAttribute parse(String attributeName) {
        if (!isConstructAttribute(attributeName)) return null;

        String body = attributeName.substring(PREFIX.length());
        List<String> tokens = tokenize(body);

        ParsedAttribute result = new ParsedAttribute();
        result.attributeName = attributeName;

        for (String token : tokens) {
            if (TYPE_TOKENS.contains(token)) {
                result.constructTypes.add(token);
            } else if (CATEGORY_TOKENS.contains(token)) {
                ConstructCategory cat = ConstructCategory.fromId(token);
                if (cat != null) result.categories.add(cat);
            } else if (EXCLUSION_TOKENS.containsKey(token)) {
                result.excludedCategories.add(EXCLUSION_TOKENS.get(token));
            } else if (SOURCE_TOKENS.contains(token)) {
                // 来源标识 token：仅记录，不参与匹配
                result.source = token;
            } else if (EFFECT_TOKENS.contains(token)) {
                result.effectType = parseEffectType(token);
            } else if (VALUE_TYPE_TOKENS.contains(token)) {
                result.valueType = parseValueType(token);
            } else if (!token.isEmpty()) {
                // 未知 token 视为标签
                result.tags.add(token);
            }
        }

        return result;
    }

    /**
     * 将属性名 body 部分拆分为 token，处理 attack_speed / build_speed 等多词组合
     */
    private static List<String> tokenize(String body) {
        String[] parts = body.split("_");
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (i + 1 < parts.length) {
                String combined = parts[i] + "_" + parts[i + 1];
                if (i + 2 < parts.length) {
                    String tripleCombined = combined + "_" + parts[i + 2];
                    if (tripleCombined.equals("weapon_attack_speed")) {
                        tokens.add(tripleCombined);
                        i += 2; // 跳过下两个 part
                        continue;
                    }
                }
                if (combined.equals("attack_speed") || combined.equals("build_speed") || combined.equals("explosive_count") || EXCLUSION_TOKENS.containsKey(combined)) {
                    tokens.add(combined);
                    i++; // 跳过下一个 part
                    continue;
                }
            }
            if (!parts[i].isEmpty()) {
                tokens.add(parts[i]);
            }
        }
        return tokens;
    }

    private static EffectType parseEffectType(String token) {
        return switch (token) {
            case "health" -> EffectType.HEALTH;
            case "damage" -> EffectType.DAMAGE;
            case "attack_speed" -> EffectType.ATTACK_SPEED;
            case "weapon_attack_speed" -> EffectType.WEAPON_ATTACK_SPEED;
            case "count" -> EffectType.MAX_COUNT;
            case "build_speed" -> EffectType.BUILD_SPEED;
            case "explosive_count" -> EffectType.EXPLOSIVE_COUNT;
            default -> null;
        };
    }

    private static AttributeType parseValueType(String token) {
        return switch (token) {
            case "base" -> AttributeType.BASE;
            case "percent" -> AttributeType.PERCENT;
            case "independent" -> AttributeType.INDEPENDENT_MULTIPLY;
            default -> null;
        };
    }

    /**
     * 解析后的构造体属性
     */
    public static class ParsedAttribute {
        private String attributeName;
        private final Set<String> constructTypes = new HashSet<>();
        private final Set<ConstructCategory> categories = new HashSet<>();
        private final Set<ConstructCategory> excludedCategories = new HashSet<>();
        private final Set<String> tags = new HashSet<>();
        private String source; // 来源标识（evolution, mothership 等），不参与匹配
        private EffectType effectType;
        private AttributeType valueType;

        public String getAttributeName() { return attributeName; }
        public Set<String> getConstructTypes() { return constructTypes; }
        public Set<ConstructCategory> getCategories() { return categories; }
        public Set<ConstructCategory> getExcludedCategories() { return excludedCategories; }
        public Set<String> getTags() { return tags; }
        public String getSource() { return source; }
        public EffectType getEffectType() { return effectType; }
        public AttributeType getValueType() { return valueType; }

        /**
         * 判断此属性是否匹配给定的构造体
         *
         * @param typeId       构造体类型 ID
         * @param type         构造体类型（用于类别匹配）
         * @param instanceTags 实例标签
         * @return 是否匹配
         */
        public boolean matches(String typeId, ConstructType type, Set<String> instanceTags) {
            // 类型匹配：无类型限制则匹配所有，否则需包含
            if (!constructTypes.isEmpty() && !constructTypes.contains(typeId)) {
                return false;
            }

            // 类别匹配
            if (!categories.isEmpty() && !type.matchesCategories(categories)) {
                return false;
            }

            // 类别排除
            if (!excludedCategories.isEmpty() && type.matchesCategories(excludedCategories)) {
                return false;
            }

            // 标签匹配：属性要求的标签必须在构造体的标签集合中
            if (!tags.isEmpty()) {
                Set<String> allTags = new HashSet<>(type.getTags());
                if (instanceTags != null) {
                    allTags.addAll(instanceTags);
                }
                if (!allTags.containsAll(tags)) {
                    return false;
                }
            }

            return true;
        }
    }

    /** 效果类型枚举（与旧 EffectType 对齐） */
    public enum EffectType {
        HEALTH,
        DAMAGE,
        ATTACK_SPEED,
        WEAPON_ATTACK_SPEED,
        BUILD_SPEED,
        MAX_COUNT,
        EXPLOSIVE_COUNT
    }
}
