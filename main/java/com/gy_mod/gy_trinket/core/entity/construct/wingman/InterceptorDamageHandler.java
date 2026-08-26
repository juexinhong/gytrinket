package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拦截机伤害事件处理器
 * <p>
 * 负责在拦截机造成伤害时重置目标的无敌时间，
 * 确保拦截机的高频攻击不会被原版无敌帧机制吞掉伤害。
 * <p>
 * 使用 {@link LivingAttackEvent}（对应 1.21.1 的 LivingIncomingDamageEvent），
 * 因为该事件在 {@code LivingEntity.hurt()} 的无敌帧检查之前触发，
 * 可以在无敌帧拦截伤害之前将 {@code invulnerableTime} 重置为0。
 * <p>
 * 覆盖的攻击类型：
 * <ul>
 *   <li>近战：通过 doHurtTarget → hurt → LivingAttackEvent</li>
 *   <li>弓箭模式：箭矢命中 → hurt → LivingAttackEvent</li>
 *   <li>爆破弹：ExplosiveProjectile 中已直接处理</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class InterceptorDamageHandler {

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
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
