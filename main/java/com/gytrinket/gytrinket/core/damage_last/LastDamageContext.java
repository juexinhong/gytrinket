package com.gytrinket.gytrinket.core.damage_last;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 最终伤害上下文
 * <p>
 * 封装 LivingDamageEvent，提供对伤害值的修改接口
 */
public class LastDamageContext {

    private final LivingDamageEvent.Pre event;
    private boolean canceled = false;

    public LastDamageContext(LivingDamageEvent.Pre event) {
        this.event = event;
    }

    /**
     * 获取受伤的实体
     */
    public LivingEntity getEntity() {
        return event.getEntity();
    }

    /**
     * 获取伤害来源
     */
    public DamageSource getSource() {
        return event.getSource();
    }

    /**
     * 获取当前伤害值
     */
    public float getCurrentDamage() {
        return event.getNewDamage();
    }

    /**
     * 设置伤害值
     */
    public void setCurrentDamage(float damage) {
        event.setNewDamage(damage);
    }

    /**
     * 判断是否已被取消
     */
    public boolean isCanceled() {
        return canceled;
    }

    /**
     * 设置取消状态
     */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
        if (canceled) {
            event.setNewDamage(0);
        }
    }

    /**
     * 获取原始事件
     */
    public LivingDamageEvent.Pre getEvent() {
        return event;
    }
}