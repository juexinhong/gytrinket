package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 拦截机伤害事件处理器
 * <p>
 * 负责在拦截机造成伤害时重置目标的无敌时间，
 * 确保拦截机的高频攻击不会被原版无敌帧机制吞掉伤害。
 * <p>
 * 使用 {@link LivingIncomingDamageEvent}（而非 LivingDamageEvent.Pre），
 * 因为该事件在 {@code LivingEntity.hurt()} 的无敌帧检查之前触发，
 * 可以在无敌帧拦截伤害之前将 {@code invulnerableTime} 重置为0。
 * <p>
 * 覆盖的攻击类型：
 * <ul>
 *   <li>近战：通过 doHurtTarget → hurt → LivingIncomingDamageEvent</li>
 *   <li>弓箭模式：箭矢命中 → hurt → LivingIncomingDamageEvent</li>
 *   <li>爆破弹：ExplosiveProjectile 中已直接处理</li>
 * </ul>
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class InterceptorDamageHandler {

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // 仅在服务端处理
        if (event.getEntity().level().isClientSide) return;

        // 检查伤害来源是否来自拦截机
        if (isInterceptorDamage(event.getSource())) {
            LivingEntity target = event.getEntity();
            // 伤害前重置无敌时间（在原版无敌帧检查之前执行）
            target.invulnerableTime = 0;
        }
    }

    /**
     * 判断伤害来源是否来自拦截机
     */
    private static boolean isInterceptorDamage(DamageSource source) {
        if (source.getEntity() instanceof WingmanConstructEntity) {
            return true;
        }

        if (source.getDirectEntity() instanceof Projectile projectile) {
            if (projectile.getOwner() instanceof WingmanConstructEntity) {
                return true;
            }
        }

        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            if (arrow.getOwner() instanceof WingmanConstructEntity) {
                return true;
            }
        }

        return false;
    }
}
