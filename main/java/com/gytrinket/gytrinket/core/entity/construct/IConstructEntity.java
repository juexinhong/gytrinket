package com.gytrinket.gytrinket.core.entity.construct;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
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

    /** 获取归属玩家 UUID */
    @Nullable
    UUID getOwnerUUID();

    /** 获取基础最大生命值（不含属性修饰器加成） */
    double getBaseMaxHealth();

    /** 获取基础攻击伤害（不含属性修饰器加成） */
    double getBaseAttackDamage();

    /** 设置攻速倍率（来自 construct_attack_speed 等属性） */
    void setAttackSpeedMultiplier(double multiplier);

    /** 获取当前攻速倍率 */
    double getAttackSpeedMultiplier();

    /** 刷新构造体属性（重算并应用 construct_* 属性） */
    void refreshConstructAttributes();
}
