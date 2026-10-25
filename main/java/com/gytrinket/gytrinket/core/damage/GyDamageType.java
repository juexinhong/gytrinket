package com.gytrinket.gytrinket.core.damage;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.19.2 没有 {@code damage_type} 注册表（1.19.4+ 才有），
 * 因此把原本写在 {@code data/gytrinket/damage_type/*.json} 与
 * {@code data/*}{@code /tags/damage_type/*.json} 里的声明，落成这个不可变的类型对象。
 * <p>
 * 字段语义与 1.20.1 的数据包定义一一对应：
 * <ul>
 *   <li>{@code exhaustion} ← JSON 的 {@code exhaustion}</li>
 *   <li>{@code bypassArmor/bypassEnchantments/bypassResistance} ← {@code bypasses_armor / bypasses_enchantments / bypasses_resistance} 标签</li>
 *   <li>{@code fire} ← {@code is_fire} 标签</li>
 *   <li>{@code noImpact / noHurtEffect / avoidsGuardianThorns} ← 同名标签（1.19.2 由代码判定）</li>
 *   <li>{@code bypassesShield / bypassesCooldown} ← 本模组语义标签</li>
 * </ul>
 * <p>
 * 身份语义沿用原代码：{@code ==} 比较即可（单例常量）。
 */
public final class GyDamageType {

    private final String path;
    private final ResourceLocation location;

    private float exhaustion = 0.1f;
    private boolean bypassArmor;
    private boolean bypassEnchantments;
    private boolean bypassResistance;
    private boolean fire;
    private boolean noImpact;
    private boolean noHurtEffect;
    private boolean avoidsGuardianThorns;
    private boolean bypassesShield;
    private boolean bypassesCooldown;

    GyDamageType(String path) {
        this.path = path;
        this.location = new ResourceLocation(ModDamageTypes.NAMESPACE, path);
    }

    // ==================== 定义期配置（只在静态初始化时用） ====================

    GyDamageType exhaustion(float value) {
        this.exhaustion = value;
        return this;
    }

    GyDamageType withBypassArmor() {
        this.bypassArmor = true;
        return this;
    }

    GyDamageType withBypassEnchantments() {
        this.bypassEnchantments = true;
        return this;
    }

    GyDamageType withBypassResistance() {
        this.bypassResistance = true;
        return this;
    }

    GyDamageType withFire() {
        this.fire = true;
        return this;
    }

    GyDamageType withNoImpact() {
        this.noImpact = true;
        return this;
    }

    GyDamageType withNoHurtEffect() {
        this.noHurtEffect = true;
        return this;
    }

    GyDamageType withAvoidsGuardianThorns() {
        this.avoidsGuardianThorns = true;
        return this;
    }

    GyDamageType withBypassesShield() {
        this.bypassesShield = true;
        return this;
    }

    GyDamageType withBypassesCooldown() {
        this.bypassesCooldown = true;
        return this;
    }

    // ==================== 查询 ====================

    public String path() {
        return path;
    }

    /** 原数据包 id：{@code gytrinket:xxx}。 */
    public ResourceLocation location() {
        return location;
    }

    /** 死亡消息键中段：{@code gytrinket.xxx}（与原 JSON 的 message_id 一致）。 */
    public String messageId() {
        return ModDamageTypes.NAMESPACE + "." + path;
    }

    public float exhaustion() {
        return exhaustion;
    }

    public boolean bypassArmor() {
        return bypassArmor;
    }

    public boolean bypassEnchantments() {
        return bypassEnchantments;
    }

    public boolean bypassResistance() {
        return bypassResistance;
    }

    public boolean fire() {
        return fire;
    }

    public boolean noImpact() {
        return noImpact;
    }

    public boolean noHurtEffect() {
        return noHurtEffect;
    }

    public boolean avoidsGuardianThorns() {
        return avoidsGuardianThorns;
    }

    public boolean bypassesShield() {
        return bypassesShield;
    }

    public boolean bypassesCooldown() {
        return bypassesCooldown;
    }

    @Override
    public String toString() {
        return location.toString();
    }
}
