package com.gy_mod.gy_trinket.core.level;

import net.minecraft.nbt.CompoundTag;

/**
 * 光点等级数据模型
 * 存储玩家的光点经验、光点等级和升级点
 */
public class ModLevelData {

    private static final String TAG_UPGRADE_EXP = "upgrade_exp";
    private static final String TAG_MOD_LEVEL = "mod_level";
    private static final String TAG_UPGRADE_POINTS = "upgrade_points";
    private static final String TAG_RANDOM_POINTS = "random_points";
    private static final String TAG_UPGRADE_POINTS_EARNED = "upgrade_points_earned";

    /** 初始/重置时发放的刷新点数量 */
    public static final int INITIAL_RANDOM_POINTS = 5;
    /** 每累计获得多少升级点（光点经验升级多少次）额外给予 1 个刷新点 */
    public static final int UPGRADE_POINTS_PER_RANDOM_POINT = 3;

    private int upgradeExp;      // 当前光点经验（进度值，升级后重置）
    private int modLevel;        // 光点等级
    private int upgradePoints;   // 升级点
    private int randomPoints;    // 刷新点（用于刷新随机构建池）
    private int upgradePointsEarned; // 累计获得升级点计数（每满3个额外给1刷新点）

    public ModLevelData() {
        this.upgradeExp = 0;
        this.modLevel = 0;
        this.upgradePoints = 0;
        this.randomPoints = INITIAL_RANDOM_POINTS;
        this.upgradePointsEarned = 0;
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
     * 随机构建系统启用时，所需经验乘以配置的倍率；
     * 代币机制启用时取消该经验惩罚（随机池消耗背包代币而非升级点）
     */
    public static int getXpNeededForNextLevel(int currentLevel) {
        int base = getTotalXpForLevel(currentLevel + 1) - getTotalXpForLevel(currentLevel);
        if (com.gy_mod.gy_trinket.config.Config.isRandomBuildEnabled()
                && !com.gy_mod.gy_trinket.config.Config.isRandomBuildTokenEnabled()) {
            return base * com.gy_mod.gy_trinket.config.Config.getRandomBuildXpMultiplier();
        }
        return base;
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
            // 每累计获得 3 个升级点（即光点经验升级 3 次）额外给予 1 个刷新点
            upgradePointsEarned++;
            if (upgradePointsEarned >= UPGRADE_POINTS_PER_RANDOM_POINT) {
                upgradePointsEarned -= UPGRADE_POINTS_PER_RANDOM_POINT;
                randomPoints++;
            }
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
     * （刷新点由调用方在重置后发放，见 ModLevelManager.resetData）
     */
    public void reset() {
        upgradeExp = 0;
        modLevel = 0;
        upgradePoints = 0;
        randomPoints = 0;
        upgradePointsEarned = 0;
    }

    /** 增加刷新点 */
    public void addRandomPoints(int amount) {
        if (amount <= 0) return;
        randomPoints += amount;
    }

    /** 消耗刷新点 */
    public boolean consumeRandomPoints(int amount) {
        if (amount <= 0 || randomPoints < amount) {
            return false;
        }
        randomPoints -= amount;
        return true;
    }

    public int getRandomPoints() {
        return randomPoints;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_UPGRADE_EXP, upgradeExp);
        tag.putInt(TAG_MOD_LEVEL, modLevel);
        tag.putInt(TAG_UPGRADE_POINTS, upgradePoints);
        tag.putInt(TAG_RANDOM_POINTS, randomPoints);
        tag.putInt(TAG_UPGRADE_POINTS_EARNED, upgradePointsEarned);
        return tag;
    }

    public static ModLevelData load(CompoundTag tag) {
        ModLevelData data = new ModLevelData();
        data.upgradeExp = tag.getInt(TAG_UPGRADE_EXP);
        data.modLevel = tag.getInt(TAG_MOD_LEVEL);
        data.upgradePoints = tag.getInt(TAG_UPGRADE_POINTS);
        // 旧存档没有刷新点字段时，视为首次获得初始刷新点
        data.randomPoints = tag.contains(TAG_RANDOM_POINTS)
                ? tag.getInt(TAG_RANDOM_POINTS) : INITIAL_RANDOM_POINTS;
        data.upgradePointsEarned = tag.getInt(TAG_UPGRADE_POINTS_EARNED);
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

