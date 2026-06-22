package com.gytrinket.gytrinket.core.level;

import net.minecraft.nbt.CompoundTag;

/**
 * 光点等级数据模型
 * 存储玩家的光点经验、光点等级和升级点
 */
public class ModLevelData {

    private static final String TAG_UPGRADE_EXP = "upgrade_exp";
    private static final String TAG_MOD_LEVEL = "mod_level";
    private static final String TAG_UPGRADE_POINTS = "upgrade_points";

    private int upgradeExp;      // 当前光点经验（进度值，升级后重置）
    private int modLevel;        // 光点等级
    private int upgradePoints;   // 升级点（预留）

    public ModLevelData() {
        this.upgradeExp = 0;
        this.modLevel = 0;
        this.upgradePoints = 0;
    }

    /**
     * 获取从0级升到指定等级所需的总经验值
     * 套用原版经验等级公式
     */
    public static int getTotalXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }

    /**
     * 获取从当前等级升到下一级所需的经验值
     */
    public static int getXpNeededForNextLevel(int currentLevel) {
        return getTotalXpForLevel(currentLevel + 1) - getTotalXpForLevel(currentLevel);
    }

    /**
     * 添加光点经验，自动处理升级逻辑
     * @param amount 经验量
     * @return 等级变动量（正数表示升级）
     */
    public int addUpgradeExp(int amount) {
        if (amount <= 0) return 0;

        int oldLevel = modLevel;
        upgradeExp += amount;

        while (upgradeExp >= getXpNeededForNextLevel(modLevel)) {
            upgradeExp -= getXpNeededForNextLevel(modLevel);
            modLevel++;
            upgradePoints++;
        }

        return modLevel - oldLevel;
    }

    /**
     * 消耗升级点
     * @param amount 消耗数量
     * @return 是否消耗成功
     */
    public boolean consumeUpgradePoints(int amount) {
        if (amount <= 0 || upgradePoints < amount) {
            return false;
        }
        upgradePoints -= amount;
        return true;
    }

    /**
     * 重置光点经验、光点等级和升级点为0
     */
    public void reset() {
        upgradeExp = 0;
        modLevel = 0;
        upgradePoints = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_UPGRADE_EXP, upgradeExp);
        tag.putInt(TAG_MOD_LEVEL, modLevel);
        tag.putInt(TAG_UPGRADE_POINTS, upgradePoints);
        return tag;
    }

    public static ModLevelData load(CompoundTag tag) {
        ModLevelData data = new ModLevelData();
        data.upgradeExp = tag.getInt(TAG_UPGRADE_EXP);
        data.modLevel = tag.getInt(TAG_MOD_LEVEL);
        data.upgradePoints = tag.getInt(TAG_UPGRADE_POINTS);
        return data;
    }

    public int getUpgradeExp() {
        return upgradeExp;
    }

    public int getModLevel() {
        return modLevel;
    }

    public int getUpgradePoints() {
        return upgradePoints;
    }

    /**
     * 获取当前升级进度比例（0.0 ~ 1.0），用于HUD显示
     */
    public float getExpProgress() {
        int needed = getXpNeededForNextLevel(modLevel);
        if (needed <= 0) return 0.0f;
        return (float) upgradeExp / needed;
    }
}
