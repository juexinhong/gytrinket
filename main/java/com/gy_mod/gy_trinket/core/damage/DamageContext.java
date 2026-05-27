package com.gy_mod.gy_trinket.core.damage;

import net.minecraft.world.damagesource.DamageSource;
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
}