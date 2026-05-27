package com.gy_mod.gy_trinket.core.damage_last;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * 最终伤害上下文
 * <p>
 * 封装 LivingDamageEvent，提供对伤害值的修改接口
 */
public class LastDamageContext {

    private final LivingDamageEvent event;
    private boolean canceled = false;

    public LastDamageContext(LivingDamageEvent event) {
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
        return event.getAmount();
    }

    /**
     * 设置伤害值
     */
    public void setCurrentDamage(float damage) {
        event.setAmount(damage);
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
            event.setCanceled(true);
        }
    }

    /**
     * 获取原始事件
     */
    public LivingDamageEvent getEvent() {
        return event;
    }
}