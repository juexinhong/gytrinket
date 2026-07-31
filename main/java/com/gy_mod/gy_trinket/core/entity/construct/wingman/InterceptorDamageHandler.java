package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拦截机伤害事件处理器
 * <p>
 * 负责在拦截机造成伤害时重置目标的无敌时间，
 * 确保拦截机的高频攻击不会被原版无敌帧机制吞掉伤害。
 * <p>
 * 覆盖的攻击类型：
 * <ul>
 *   <li>弓箭模式：箭矢由原版发射，通过 LivingHurtEvent 检测箭矢射击者是否为拦截机</li>
 *   <li>爆破弹：ExplosiveProjectile 中已直接处理</li>
 *   <li>近战：MeleeWeaponMode 中已直接处理</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class InterceptorDamageHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 仅在服务端处理
        if (event.getEntity().level().isClientSide) return;

        // 检查伤害来源是否来自拦截机
        if (isInterceptorDamage(event.getSource())) {
            LivingEntity target = event.getEntity();
            // 伤害前重置无敌时间
            target.invulnerableTime = 0;
        }
    }

    /**
     * 判断伤害来源是否来自拦截机
     */
    private static boolean isInterceptorDamage(net.minecraft.world.damagesource.DamageSource source) {
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
