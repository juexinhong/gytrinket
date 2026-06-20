package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class DamageContext {

    private final DamageSource source;

    @Nullable
    private final LivingEntity attacker;

    private final LivingEntity attackedEntity;

    private final Player shieldOwner;

    private final float originalDamage;

    private float currentDamage;

    private boolean canceled;

    public DamageContext(DamageSource source, @Nullable LivingEntity attacker, Player player, float originalDamage) {
        this.source = source;
        this.attacker = attacker;
        this.attackedEntity = player;
        this.shieldOwner = player;
        this.originalDamage = originalDamage;
        this.currentDamage = originalDamage;
        this.canceled = false;
    }

    public DamageContext(DamageSource source, @Nullable LivingEntity attacker, LivingEntity attackedEntity, Player shieldOwner, float originalDamage) {
        this.source = source;
        this.attacker = attacker;
        this.attackedEntity = attackedEntity;
        this.shieldOwner = shieldOwner;
        this.originalDamage = originalDamage;
        this.currentDamage = originalDamage;
        this.canceled = false;
    }

    public DamageSource getSource() {
        return source;
    }

    @Nullable
    public LivingEntity getAttacker() {
        return attacker;
    }

    public Player getPlayer() {
        return shieldOwner;
    }

    public LivingEntity getAttackedEntity() {
        return attackedEntity;
    }

    public Player getShieldOwner() {
        return shieldOwner;
    }

    public float getOriginalDamage() {
        return originalDamage;
    }

    public float getCurrentDamage() {
        return currentDamage;
    }

    public void setCurrentDamage(float currentDamage) {
        this.currentDamage = currentDamage;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isShieldTransferred() {
        return attackedEntity != shieldOwner;
    }

    // ===== 伤害类型辅助判断方法 =====

    /**
     * 判断当前伤害是否为指定伤害类型
     */
    private boolean isDamageType(ResourceKey<DamageType> typeKey) {
        return source.typeHolder().is(typeKey);
    }

    /**
     * 判断是否为玩家自伤类型（PLAYER_SELF_DAMAGE 或 PROTOCOL_PLAYER_SELF_DAMAGE）
     */
    public boolean isPlayerSelfDamage() {
        return isDamageType(ModDamageTypes.PLAYER_SELF_DAMAGE) || isDamageType(ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE);
    }

    /**
     * 判断是否为护盾自伤类型（SHIELD_SELF_DAMAGE 或 PROTOCOL_SHIELD_SELF_DAMAGE）
     */
    public boolean isShieldSelfDamage() {
        return isDamageType(ModDamageTypes.SHIELD_SELF_DAMAGE) || isDamageType(ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE);
    }

    /**
     * 判断是否为任意自伤类型（玩家自伤 + 护盾自伤）
     */
    public boolean isAnySelfDamage() {
        return isPlayerSelfDamage() || isShieldSelfDamage();
    }

    /**
     * 判断是否为协议自伤类型（PROTOCOL_PLAYER_SELF_DAMAGE 或 PROTOCOL_SHIELD_SELF_DAMAGE）
     */
    public boolean isProtocolSelfDamage() {
        return isDamageType(ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) || isDamageType(ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE);
    }
}