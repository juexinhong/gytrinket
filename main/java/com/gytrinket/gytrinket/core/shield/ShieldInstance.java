package com.gytrinket.gytrinket.core.shield;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.item.ItemStack;

/**
 * 护盾实例：每个提供护盾的物品实例独立维护池量、破盾状态与冷却进度。
 * <p>
 * 实例粒度 = 物品种类 × 护盾类型（相同物品 id 去重只生效一个实例）；
 * globalPool 标志与 {@link #GLOBAL_POOL_ITEM_ID} 仅保留用于旧存档快照兼容，
 * 现行逻辑不再创建全局池实例（无护盾类型物品的护盾值均摊共享给各实例上限）。
 */
public class ShieldInstance {

    /** 全局池实例的物品 id 占位 */
    public static final String GLOBAL_POOL_ITEM_ID = "<global>";

    private final String itemId;
    private final String shieldTypeName;
    private final ItemStack source;
    private final boolean globalPool;

    private double maxShield;
    private double currentShield;
    private boolean broken;
    private double cooldownProgress;
    private int maxCooldown;

    public ShieldInstance(String itemId, String shieldTypeName, ItemStack source, boolean globalPool,
                          double maxShield, int maxCooldown) {
        this.itemId = itemId;
        this.shieldTypeName = shieldTypeName;
        this.source = source == null ? ItemStack.EMPTY : source;
        this.globalPool = globalPool;
        this.maxShield = Math.max(0, maxShield);
        this.maxCooldown = Math.max(0, maxCooldown);
        if (this.maxCooldown > 0) {
            // 新实例零盾入场并立即进入冷却充能：装备护盾后需从 0 充能恢复至满值（与首个护盾的初始体验一致）
            this.currentShield = 0;
            this.broken = true;
            this.cooldownProgress = 0;
        } else {
            // 无冷却定义：无法通过冷却回满，保持满盾入场
            this.currentShield = this.maxShield;
        }
    }

    public String getItemId() {
        return itemId;
    }

    public String getShieldTypeName() {
        return shieldTypeName;
    }

    public ItemStack getSource() {
        return source;
    }

    public boolean isGlobalPool() {
        return globalPool;
    }

    public double getMaxShield() {
        return maxShield;
    }

    public double getCurrentShield() {
        return currentShield;
    }

    public boolean isBroken() {
        return broken;
    }

    public double getCooldownProgress() {
        return cooldownProgress;
    }

    public int getMaxCooldown() {
        return maxCooldown;
    }

    /** 实例身份键：itemId|shieldTypeName（重建时按此匹配保留状态） */
    public String identityKey() {
        return itemId + "|" + (shieldTypeName == null ? "" : shieldTypeName);
    }

    public void setMaxShield(double newMax) {
        this.maxShield = Math.max(0, newMax);
        if (this.currentShield > this.maxShield) {
            this.currentShield = this.maxShield;
        }
    }

    public void setCurrentShield(double value) {
        this.currentShield = Math.max(0, Math.min(value, this.maxShield));
    }

    /** 冷却充能中：护盾未满（受损或破盾）且冷却未完成 */
    public boolean isCoolingDown() {
        return maxCooldown > 0 && cooldownProgress < maxCooldown && currentShield < maxShield;
    }

    /** HUD 显示用整数进度 */
    public int getCurrentCooldown() {
        return (int) cooldownProgress;
    }

    public void setCooldownProgress(double progress) {
        this.cooldownProgress = Math.max(0, Math.min(progress, maxCooldown));
    }

    /** 破盾进入冷却：进度清零，重设冷却上限 */
    public void startCooldown(int maxCooldown) {
        this.broken = true;
        this.cooldownProgress = 0;
        this.maxCooldown = Math.max(0, maxCooldown);
    }

    /** 受损进入冷却充能：进度清零重新计时（不改变破盾状态，池量回满后自动退出） */
    public void restartCooldown(int maxCooldown) {
        if (maxCooldown > 0) {
            this.cooldownProgress = 0;
            this.maxCooldown = maxCooldown;
        }
    }

    /** 冷却推进：speedFactor 为每 tick 进度增量（攻击减速时 <1） */
    public void tickCooldown(double speedFactor) {
        if (cooldownProgress < maxCooldown) {
            cooldownProgress = Math.min(maxCooldown, cooldownProgress + speedFactor);
        }
    }

    /** 冷却完成：回满池量并退出破盾状态 */
    public void completeCooldown() {
        this.currentShield = this.maxShield;
        this.broken = false;
        this.cooldownProgress = 0;
    }

    /** 冷却上限变化：进度按新旧上限比例折算 */
    public void updateMaxCooldown(int newMaxCooldown) {
        newMaxCooldown = Math.max(0, newMaxCooldown);
        if (maxCooldown > 0 && newMaxCooldown > 0) {
            double ratio = cooldownProgress / maxCooldown;
            cooldownProgress = Math.min(newMaxCooldown, newMaxCooldown * ratio);
        } else {
            cooldownProgress = 0;
        }
        this.maxCooldown = newMaxCooldown;
    }

    /**
     * 重建时恢复旧实例状态：池量 clamp 到新上限；
     * 未破盾实例保持现状；破盾实例进度按新旧上限比例折算；
     * 旧实例冷却已完成或新实例无冷却定义时回满兜底。
     */
    public void restoreState(double oldCurrent, boolean oldBroken, double oldProgress, int oldMaxCooldown) {
        this.currentShield = Math.max(0, Math.min(oldCurrent, this.maxShield));
        if (!oldBroken) {
            this.broken = false;
            if (this.maxCooldown > 0 && oldMaxCooldown > 0 && this.currentShield < this.maxShield) {
                // 受损实例的充能进度按新旧上限比例折算保留：重建不重置充能（否则冷却进度反复清零）
                double ratio = oldProgress / oldMaxCooldown;
                this.cooldownProgress = Math.min(this.maxCooldown, this.maxCooldown * ratio);
            } else {
                this.cooldownProgress = 0;
            }
        } else if (oldMaxCooldown > 0 && oldProgress >= oldMaxCooldown) {
            completeCooldown();
        } else if (this.maxCooldown > 0) {
            this.broken = true;
            double ratio = oldMaxCooldown > 0 ? oldProgress / oldMaxCooldown : 0;
            this.cooldownProgress = Math.min(this.maxCooldown, this.maxCooldown * ratio);
        } else {
            completeCooldown();
        }
    }
}
