package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * 构造体实体接口
 * <p>
 * 统一三个构造体实体（无人机、僚机、蜂群）的属性应用契约，
 * 使 {@link ConstructAttributeApplier} 等管理器可以基于接口统一处理，
 * 避免针对每个实体类型重复实现属性应用逻辑。
 * <p>
 * 实现类应继承 {@link LivingEntity}，从而获得 {@code getAttribute}、
 * {@code getMaxHealth}、{@code getHealth}、{@code setHealth} 等方法。
 */
public interface IConstructEntity {

    /**
     * 获取归属玩家 UUID。
     * <p>
     * 注意：发布 jar（SRG 重映射）下，本方法名 getOwnerUUID 与 Minecraft 接口
     * {@link net.minecraft.world.entity.OwnableEntity#getOwnerUUID()} 同名，重映射后两者被拆分为
     * 不同方法名（OwnableEntity 的变为 m_21805_ 等 SRG 名），导致实现类只实现了 SRG 版而本接口
     * 方法无实现，运行时报 AbstractMethodError。故改为 default 方法并委托给 OwnableEntity，
     * 保证两种映射环境下都能取到真实归属。
     */
    @Nullable
    default UUID getOwnerUUID() {
        if (this instanceof net.minecraft.world.entity.OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        }
        return null;
    }

    /** 获取基础最大生命值（不含属性修饰器加成） */
    double getBaseMaxHealth();

    /** 获取基础攻击伤害（不含属性修饰器加成） */
    double getBaseAttackDamage();

    /** 设置攻速倍率（来自 construct_attack_speed 等属性） */
    void setAttackSpeedMultiplier(double multiplier);

    /** 获取当前攻速倍率 */
    double getAttackSpeedMultiplier();

    /** 设置武器攻速倍率（仅影响武器攻击，不影响爆破弹攻速） */
    void setWeaponAttackSpeedMultiplier(double multiplier);

    /** 获取当前武器攻速倍率 */
    double getWeaponAttackSpeedMultiplier();

    /** 设置移动速度倍率（来自 construct_move_speed 等属性） */
    void setMoveSpeedMultiplier(double multiplier);

    /** 获取当前移动速度倍率 */
    double getMoveSpeedMultiplier();

    /** 设置环绕/阵列转速倍率（来自 construct_orbit_speed 等属性） */
    void setOrbitSpeedMultiplier(double multiplier);

    /** 获取当前环绕/阵列转速倍率 */
    double getOrbitSpeedMultiplier();

    /** 设置自转/朝向旋转速度倍率（来自 construct_rotation_speed 等属性） */
    void setRotationSpeedMultiplier(double multiplier);

    /** 获取当前自转/朝向旋转速度倍率 */
    double getRotationSpeedMultiplier();

    /** 设置低血量攻速独立乘区倍率（炉心融解模块） */
    void setLowHpAttackSpeedMultiplier(double multiplier);

    /** 获取当前低血量攻速独立乘区倍率（炉心融解模块） */
    double getLowHpAttackSpeedMultiplier();

    /** 刷新构造体属性（重算并应用 construct_* 属性） */
    void refreshConstructAttributes();

    /** 获取构造体类型 ID（用于属性目标匹配） */
    String getConstructTypeId();

    /** 获取实例标签（用于属性目标匹配，如突击/防御/指挥官等） */
    Set<String> getInstanceTags();
}

