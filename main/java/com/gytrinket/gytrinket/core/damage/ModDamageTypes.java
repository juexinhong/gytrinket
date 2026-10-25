package com.gytrinket.gytrinket.core.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 模组自定义伤害类型表（1.19.2 版）。
 * <p>
 * 1.20.1 用数据包 {@code damage_type} 注册表 + 原版标签表达"护盾伤害 / 斩杀伤害 / 最终伤害…"，
 * 1.19.2 没有这个注册表（1.19.4+ 才有），因此：
 * <ul>
 *   <li>类型定义 → {@link GyDamageType} 常量（字段与 JSON / 标签一一对应）；</li>
 *   <li>伤害来源 → {@link GyDamageSource}（承载 message_id、exhaustion、穿甲/穿魔/穿抗性与实体归属）。</li>
 * </ul>
 * 对外的 {@code gytrinket:xxx} 语义、死亡消息键 {@code death.attack.gytrinket.xxx} 与 1.20.1 保持一致，
 * 语言文件无需改动。
 * <p>
 * 行为差异（1.19.2 固有限制）：{@code isBypassMagic()} 同时覆盖抗性药水与保护附魔，
 * 所以 {@code execute_damage} 的"穿抗性"会连带穿保护附魔 —— 与 1.21.1 的设计意图一致。
 */
public final class ModDamageTypes {

    /** 命名空间，用于资源定位 */
    public static final String NAMESPACE = "gytrinket";

    // ==================== 伤害类型 ====================

    /** 护盾自伤 */
    public static final GyDamageType SHIELD_SELF_DAMAGE = new GyDamageType("shield_self_damage")
            .exhaustion(0.1f).withBypassesShield().withBypassesCooldown().withNoImpact().withNoHurtEffect();

    /** 玩家自伤 */
    public static final GyDamageType PLAYER_SELF_DAMAGE = new GyDamageType("player_self_damage")
            .exhaustion(0.1f).withBypassArmor().withBypassResistance()
            .withBypassesShield().withBypassesCooldown().withNoImpact().withNoHurtEffect();

    /** 最终伤害（用于绕过护盾等特殊处理的伤害） */
    public static final GyDamageType FINAL_DAMAGE = new GyDamageType("final_damage")
            .exhaustion(0.1f).withBypassesShield().withBypassesCooldown().withNoImpact().withNoHurtEffect();

    /** 灼烧伤害 */
    public static final GyDamageType BURN_DAMAGE = new GyDamageType("burn_damage")
            .exhaustion(0.0f).withBypassArmor().withFire().withAvoidsGuardianThorns()
            .withBypassesShield().withBypassesCooldown().withNoImpact();

    /** 点燃伤害 */
    public static final GyDamageType ON_FIRE_DAMAGE = new GyDamageType("on_fire_damage")
            .exhaustion(0.0f).withBypassArmor().withFire().withAvoidsGuardianThorns()
            .withBypassesShield().withBypassesCooldown().withNoImpact();

    /** 瞬时伤害 */
    public static final GyDamageType INSTANT_DAMAGE = new GyDamageType("instant_damage")
            .exhaustion(0.0f);

    /** 协议护盾自伤 */
    public static final GyDamageType PROTOCOL_SHIELD_SELF_DAMAGE = new GyDamageType("protocol_shield_self_damage")
            .exhaustion(0.0f).withBypassesShield().withBypassesCooldown().withNoImpact().withNoHurtEffect();

    /** 协议玩家自伤 */
    public static final GyDamageType PROTOCOL_PLAYER_SELF_DAMAGE = new GyDamageType("protocol_player_self_damage")
            .exhaustion(0.0f).withBypassArmor().withBypassResistance()
            .withBypassesShield().withBypassesCooldown().withNoImpact().withNoHurtEffect();

    /** 无人机子弹伤害 */
    public static final GyDamageType DRONE_BULLET = new GyDamageType("drone_bullet")
            .exhaustion(0.0f).withBypassesCooldown();

    /** 虹吸伤害 */
    public static final GyDamageType SIPHON_DAMAGE = new GyDamageType("siphon_damage")
            .exhaustion(0.1f).withBypassArmor().withBypassResistance().withAvoidsGuardianThorns()
            .withBypassesCooldown().withNoImpact().withNoHurtEffect();

    /** 构造体自伤（炉心融解模块） */
    public static final GyDamageType CONSTRUCT_SELF_DAMAGE = new GyDamageType("construct_self_damage")
            .exhaustion(0.1f).withBypassesCooldown().withNoHurtEffect();

    /** 蜂群电弧伤害 */
    public static final GyDamageType SWARM_DAMAGE = new GyDamageType("swarm_damage")
            .exhaustion(0.0f);

    /** 斩杀伤害（致命一击预估，最终由归属玩家完成击杀；穿甲穿魔咒穿抗性） */
    public static final GyDamageType EXECUTE_DAMAGE = new GyDamageType("execute_damage")
            .exhaustion(0.1f).withBypassArmor().withBypassEnchantments().withBypassResistance();

    /** 陨石撞击伤害。 */
    public static final GyDamageType METEOR_IMPACT = new GyDamageType("meteor_impact")
            .exhaustion(0.1f);

    private ModDamageTypes() {
    }

    // ==================== 类型判定（替代 1.20.1 的 typeHolder().unwrapKey()） ====================

    /** 取自定义伤害类型；非本模组伤害返回 null。 */
    @Nullable
    public static GyDamageType typeOf(DamageSource source) {
        return source instanceof GyDamageSource gy ? gy.gyType() : null;
    }

    public static boolean is(DamageSource source, GyDamageType type) {
        return typeOf(source) == type;
    }

    /**
     * 原 {@code data/gytrinket/tags/damage_type/no_hurt_effect.json}：
     * 不播受伤音效、不触发镜头摇晃/行走动画抖动。
     */
    public static boolean hasNoHurtEffect(DamageSource source) {
        GyDamageType type = typeOf(source);
        return type != null && type.noHurtEffect();
    }

    /** 原 {@code minecraft:no_impact} 标签：不产生受击位移/动画。 */
    public static boolean hasNoImpact(DamageSource source) {
        GyDamageType type = typeOf(source);
        return type != null && type.noImpact();
    }

    /** 原 {@code minecraft:avoids_guardian_thorns} 标签。 */
    public static boolean avoidsGuardianThorns(DamageSource source) {
        GyDamageType type = typeOf(source);
        return type != null && type.avoidsGuardianThorns();
    }

    /** 原 {@code minecraft:bypasses_shield} 标签。 */
    public static boolean bypassesShield(DamageSource source) {
        GyDamageType type = typeOf(source);
        return type != null && type.bypassesShield();
    }

    /** 原 {@code minecraft:bypasses_cooldown} 标签。 */
    public static boolean bypassesCooldown(DamageSource source) {
        GyDamageType type = typeOf(source);
        return type != null && type.bypassesCooldown();
    }

    // ==================== 伤害来源创建方法 ====================

    public static DamageSource getShieldSelfDamageSource(Level level) {
        return new GyDamageSource(SHIELD_SELF_DAMAGE);
    }

    public static DamageSource getPlayerSelfDamageSource(Level level) {
        return new GyDamageSource(PLAYER_SELF_DAMAGE);
    }

    public static DamageSource getFinalDamageSource(Level level) {
        return new GyDamageSource(FINAL_DAMAGE);
    }

    public static DamageSource getFinalDamageSource(Level level, @Nullable Entity directEntity) {
        return new GyDamageSource(FINAL_DAMAGE, directEntity, null);
    }

    public static DamageSource getFinalDamageSource(Level level, @Nullable Entity directEntity,
                                                    @Nullable Entity causingEntity) {
        return new GyDamageSource(FINAL_DAMAGE, directEntity, causingEntity);
    }

    public static DamageSource getBurnDamageSource(Level level) {
        return new GyDamageSource(BURN_DAMAGE);
    }

    public static DamageSource getBurnDamageSource(Level level, @Nullable Entity directEntity) {
        return new GyDamageSource(BURN_DAMAGE, directEntity, null);
    }

    public static DamageSource getOnFireDamageSource(Level level) {
        return new GyDamageSource(ON_FIRE_DAMAGE);
    }

    public static DamageSource getOnFireDamageSource(Level level, @Nullable Entity directEntity) {
        return new GyDamageSource(ON_FIRE_DAMAGE, directEntity, null);
    }

    public static DamageSource getInstantDamageSource(Level level) {
        return new GyDamageSource(INSTANT_DAMAGE);
    }

    public static DamageSource getInstantDamageSource(Level level, @Nullable Entity directEntity) {
        return new GyDamageSource(INSTANT_DAMAGE, directEntity, null);
    }

    public static DamageSource getProtocolShieldSelfDamageSource(Level level) {
        return new GyDamageSource(PROTOCOL_SHIELD_SELF_DAMAGE);
    }

    public static DamageSource getProtocolShieldSelfDamageSource(Level level, @Nullable Entity directEntity) {
        return new GyDamageSource(PROTOCOL_SHIELD_SELF_DAMAGE, directEntity, null);
    }

    public static DamageSource getProtocolPlayerSelfDamageSource(Level level) {
        return new GyDamageSource(PROTOCOL_PLAYER_SELF_DAMAGE);
    }

    public static DamageSource getProtocolPlayerSelfDamageSource(Level level, @Nullable Entity directEntity) {
        return new GyDamageSource(PROTOCOL_PLAYER_SELF_DAMAGE, directEntity, null);
    }

    /** 陨石撞击伤害来源。 */
    public static DamageSource getMeteorImpactDamageSource(Level level, Entity meteor) {
        return new GyDamageSource(METEOR_IMPACT, meteor, meteor);
    }

    public static DamageSource getDroneBulletDamageSource(Level level, @Nullable Entity directEntity,
                                                          @Nullable Entity causingEntity) {
        return new GyDamageSource(DRONE_BULLET, directEntity, causingEntity);
    }

    public static DamageSource getSiphonDamageSource(Level level, @Nullable Entity causingEntity) {
        return new GyDamageSource(SIPHON_DAMAGE, causingEntity, causingEntity);
    }

    /** 构造体自伤伤害来源（炉心融解模块，无直接攻击者） */
    public static DamageSource getConstructSelfDamageSource(Level level) {
        return new GyDamageSource(CONSTRUCT_SELF_DAMAGE);
    }

    /** 蜂群电弧伤害来源（直接实体与攻击实体均为蜂群构造体） */
    public static DamageSource getSwarmDamageSource(Level level, Entity causingEntity) {
        return new GyDamageSource(SWARM_DAMAGE, causingEntity, causingEntity);
    }

    /**
     * 斩杀伤害来源（直接实体与攻击实体均为归属玩家，
     * 供其他模组按玩家归属判定击杀者；穿甲穿魔法保护穿抗性）
     */
    public static DamageSource getExecuteDamageSource(Level level, Player player) {
        return new GyDamageSource(EXECUTE_DAMAGE, player, player);
    }
}
