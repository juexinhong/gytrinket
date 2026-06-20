package com.gytrinket.gytrinket.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 模组自定义伤害类型注册表
 * <p>
 * 该类负责定义和注册模组的所有自定义伤害类型。
 * 伤害类型用于区分不同的伤害来源，影响伤害的计算、处理和显示方式。
 * <p>
 * 已注册的伤害类型：
 * <ul>
 *   <li>{@link #SHIELD_SELF_DAMAGE} - 护盾自伤</li>
 *   <li>{@link #PLAYER_SELF_DAMAGE} - 玩家自伤</li>
 *   <li>{@link #FINAL_DAMAGE} - 最终伤害（用于绕过护盾的伤害）</li>
 *   <li>{@link #BURN_DAMAGE} - 灼烧伤害</li>
 *   <li>{@link #ON_FIRE_DAMAGE} - 点燃伤害</li>
 *   <li>{@link #INSTANT_DAMAGE} - 瞬时伤害</li>
 *   <li>{@link #PROTOCOL_SHIELD_SELF_DAMAGE} - 协议护盾自伤</li>
 *   <li>{@link #PROTOCOL_PLAYER_SELF_DAMAGE} - 协议玩家自伤</li>
 * </ul>
 * <p>
 * 每个伤害类型都需要对应的JSON数据文件，位于：
 * {@code resources/data/gytrinket/damage_type/}
 *
 * @see DamageSource
 */
public class ModDamageTypes {

    /** 命名空间，用于资源定位 */
    public static final String NAMESPACE = "gytrinket";

    // ==================== 伤害类型注册 ====================

    /** 护盾自伤 */
    public static final ResourceKey<DamageType> SHIELD_SELF_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "shield_self_damage")
    );

    /** 玩家自伤 */
    public static final ResourceKey<DamageType> PLAYER_SELF_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "player_self_damage")
    );

    /** 最终伤害（用于绕过护盾等特殊处理的伤害） */
    public static final ResourceKey<DamageType> FINAL_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "final_damage")
    );

    /** 灼烧伤害 */
    public static final ResourceKey<DamageType> BURN_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "burn_damage")
    );

    /** 点燃伤害 */
    public static final ResourceKey<DamageType> ON_FIRE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "on_fire_damage")
    );

    /** 瞬时伤害 */
    public static final ResourceKey<DamageType> INSTANT_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "instant_damage")
    );

    /** 协议护盾自伤 */
    public static final ResourceKey<DamageType> PROTOCOL_SHIELD_SELF_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "protocol_shield_self_damage")
    );

    /** 协议玩家自伤 */
    public static final ResourceKey<DamageType> PROTOCOL_PLAYER_SELF_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "protocol_player_self_damage")
    );

    /** 无人机子弹伤害 */
    public static final ResourceKey<DamageType> DRONE_BULLET = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "drone_bullet")
    );

    /** 虹吸伤害 */
    public static final ResourceKey<DamageType> SIPHON_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "siphon_damage")
    );

    // ==================== 伤害来源创建方法 ====================

    /**
     * 创建护盾自伤伤害来源
     *
     * @param level 世界
     * @return 护盾自伤伤害来源
     */
    public static DamageSource getShieldSelfDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SHIELD_SELF_DAMAGE)
        );
    }

    /**
     * 创建玩家自伤伤害来源
     *
     * @param level 世界
     * @return 玩家自伤伤害来源
     */
    public static DamageSource getPlayerSelfDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PLAYER_SELF_DAMAGE)
        );
    }

    /**
     * 创建最终伤害来源（无直接实体）
     *
     * @param level 世界
     * @return 最终伤害来源
     */
    public static DamageSource getFinalDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FINAL_DAMAGE)
        );
    }

    /**
     * 创建最终伤害来源（带直接实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体（如弹射物）
     * @return 最终伤害来源
     */
    public static DamageSource getFinalDamageSource(Level level, @Nullable Entity directEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FINAL_DAMAGE),
                directEntity
        );
    }

    /**
     * 创建最终伤害来源（带直接实体和攻击实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体（如弹射物）
     * @param causingEntity 攻击实体（如发射者）
     * @return 最终伤害来源
     */
    public static DamageSource getFinalDamageSource(Level level, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FINAL_DAMAGE),
                directEntity,
                causingEntity
        );
    }

    /**
     * 创建灼烧伤害来源
     *
     * @param level 世界
     * @return 灼烧伤害来源
     */
    public static DamageSource getBurnDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BURN_DAMAGE)
        );
    }

    /**
     * 创建灼烧伤害来源（带直接实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体
     * @return 灼烧伤害来源
     */
    public static DamageSource getBurnDamageSource(Level level, @Nullable Entity directEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BURN_DAMAGE),
                directEntity
        );
    }

    /**
     * 创建点燃伤害来源
     *
     * @param level 世界
     * @return 点燃伤害来源
     */
    public static DamageSource getOnFireDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ON_FIRE_DAMAGE)
        );
    }

    /**
     * 创建点燃伤害来源（带直接实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体
     * @return 点燃伤害来源
     */
    public static DamageSource getOnFireDamageSource(Level level, @Nullable Entity directEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ON_FIRE_DAMAGE),
                directEntity
        );
    }

    /**
     * 创建瞬时伤害来源
     *
     * @param level 世界
     * @return 瞬时伤害来源
     */
    public static DamageSource getInstantDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(INSTANT_DAMAGE)
        );
    }

    /**
     * 创建瞬时伤害来源（带直接实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体
     * @return 瞬时伤害来源
     */
    public static DamageSource getInstantDamageSource(Level level, @Nullable Entity directEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(INSTANT_DAMAGE),
                directEntity
        );
    }

    /**
     * 创建协议护盾自伤伤害来源
     *
     * @param level 世界
     * @return 协议护盾自伤伤害来源
     */
    public static DamageSource getProtocolShieldSelfDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PROTOCOL_SHIELD_SELF_DAMAGE)
        );
    }

    /**
     * 创建协议护盾自伤伤害来源（带直接实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体
     * @return 协议护盾自伤伤害来源
     */
    public static DamageSource getProtocolShieldSelfDamageSource(Level level, @Nullable Entity directEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PROTOCOL_SHIELD_SELF_DAMAGE),
                directEntity
        );
    }

    /**
     * 创建协议玩家自伤伤害来源
     *
     * @param level 世界
     * @return 协议玩家自伤伤害来源
     */
    public static DamageSource getProtocolPlayerSelfDamageSource(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PROTOCOL_PLAYER_SELF_DAMAGE)
        );
    }

    /**
     * 创建协议玩家自伤伤害来源（带直接实体）
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体
     * @return 协议玩家自伤伤害来源
     */
    public static DamageSource getProtocolPlayerSelfDamageSource(Level level, @Nullable Entity directEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PROTOCOL_PLAYER_SELF_DAMAGE),
                directEntity
        );
    }

    /**
     * 创建无人机子弹伤害来源
     *
     * @param level 世界
     * @param directEntity 直接造成伤害的实体（子弹）
     * @param causingEntity 攻击实体（无人机）
     * @return 无人机子弹伤害来源
     */
    public static DamageSource getDroneBulletDamageSource(Level level, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DRONE_BULLET),
                directEntity,
                causingEntity
        );
    }

    public static DamageSource getSiphonDamageSource(Level level, @Nullable Entity causingEntity) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SIPHON_DAMAGE),
                causingEntity,
                causingEntity
        );
    }
}
