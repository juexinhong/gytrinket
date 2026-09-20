package com.gytrinket.gytrinket.core.shield;

import java.util.List;

/**
 * 护盾数据类
 * <p>
 * 用于存储玩家的护盾相关信息，包括当前护盾值和最大护盾值。
 * <p>
 * 主要功能：
 * <ul>
 *   <li>存储当前护盾值</li>
 *   <li>存储最大护盾值（可变）</li>
 *   <li>提供安全的护盾值设置方法（自动限制在0到最大值的范围内）</li>
 *   <li>支持独立更新最大护盾值，保留当前护盾值</li>
 *   <li>可选携带实例级快照（持久化多实例的池量/破盾/冷却状态，供登录重建时迁移）</li>
 * </ul>
 */
public class ShieldData {

    /**
     * 护盾实例快照：持久化单个实例（物品 × 护盾类型，或全局池）的状态。
     * <p>
     * 登录重建实例时按 identityKey（itemId|shieldTypeName）匹配迁移，
     * 使退出重进不丢失各实例的池量、破盾状态与冷却进度。
     */
    public record InstanceSnapshot(String itemId, String shieldTypeName, boolean globalPool,
                                   double current, boolean broken, double cooldownProgress, int maxCooldown) {
    }

    /** 当前护盾值 */
    private double currentShield;

    /** 最大护盾值 */
    private double maxShield;

    /** 实例快照列表（可空：聚合快照或旧存档无实例明细） */
    private List<InstanceSnapshot> instances;

    /**
     * 构造护盾数据
     *
     * @param maxShield 最大护盾值
     */
    public ShieldData(double maxShield) {
        this.currentShield = 0;
        this.maxShield = maxShield;
    }

    /**
     * 构造护盾数据（带当前值）
     *
     * @param currentShield 当前护盾值
     * @param maxShield 最大护盾值
     */
    public ShieldData(double currentShield, double maxShield) {
        this.currentShield = Math.max(0, Math.min(currentShield, maxShield));
        this.maxShield = maxShield;
    }

    /**
     * 构造护盾数据（带当前值与实例快照，持久化专用）
     *
     * @param currentShield 当前护盾值
     * @param maxShield 最大护盾值
     * @param instances 实例快照列表
     */
    public ShieldData(double currentShield, double maxShield, List<InstanceSnapshot> instances) {
        this(currentShield, maxShield);
        this.instances = instances;
    }

    /** 实例快照列表（可能为 null：旧存档或仅聚合语义的临时快照） */
    public List<InstanceSnapshot> getInstances() {
        return instances;
    }

    /**
     * 获取当前护盾值
     *
     * @return 当前护盾值
     */
    public double getCurrentShield() {
        return currentShield;
    }

    /**
     * 设置当前护盾值
     * <p>
     * 该方法会自动将值限制在有效范围内：
     * <ul>
     *   <li>最小值：0（护盾不能为负）</li>
     *   <li>最大值：maxShield（护盾不能超过最大值）</li>
     * </ul>
     *
     * @param currentShield 要设置的当前护盾值
     */
    public void setCurrentShield(double currentShield) {
        this.currentShield = Math.max(0, Math.min(currentShield, maxShield));
    }

    /**
     * 获取最大护盾值
     *
     * @return 最大护盾值
     */
    public double getMaxShield() {
        return maxShield;
    }

    /**
     * 更新最大护盾值，保留当前护盾值
     * <p>
     * 当最大护盾值变化时，当前护盾值会被限制在新的范围内。
     *
     * @param maxShield 新的最大护盾值
     */
    public void updateMaxShield(double maxShield) {
        this.maxShield = maxShield;
        this.currentShield = Math.max(0, Math.min(this.currentShield, maxShield));
    }
}