package com.gytrinket.gytrinket.core.shield.cooldown;

import java.util.UUID;

public class CooldownContext {

    private final UUID playerUUID;
    private final double currentShield;
    private final double maxShield;
    private float lastDamage;
    private float originalDamage;
    private float currentDamage;
    
    private float attackStrength = 1.0f;
    private boolean isInAttackCooldown = false;

    public CooldownContext(UUID playerUUID, double currentShield, double maxShield) {
        this.playerUUID = playerUUID;
        this.currentShield = currentShield;
        this.maxShield = maxShield;
        this.lastDamage = 0;
        this.originalDamage = 0;
        this.currentDamage = 0;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public double getCurrentShield() {
        return currentShield;
    }

    public double getMaxShield() {
        return maxShield;
    }

    public float getLastDamage() {
        return lastDamage;
    }

    public void setLastDamage(float damage) {
        this.lastDamage = damage;
    }

    public float getOriginalDamage() {
        return originalDamage;
    }

    public void setOriginalDamage(float originalDamage) {
        this.originalDamage = originalDamage;
    }

    public float getCurrentDamage() {
        return currentDamage;
    }

    public void setCurrentDamage(float currentDamage) {
        this.currentDamage = currentDamage;
    }

    public float getAttackStrength() {
        return attackStrength;
    }

    public void setAttackStrength(float attackStrength) {
        this.attackStrength = attackStrength;
    }

    public boolean isInAttackCooldown() {
        return isInAttackCooldown;
    }

    public void setInAttackCooldown(boolean inAttackCooldown) {
        this.isInAttackCooldown = inAttackCooldown;
    }

    public double getShieldPercentage() {
        if (maxShield <= 0) {
            return 0.0;
        }
        return currentShield / maxShield;
    }
}