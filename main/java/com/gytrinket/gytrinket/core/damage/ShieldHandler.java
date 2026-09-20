package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.ShieldInstance;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.sound.ModSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

public class ShieldHandler implements DamageHandler {

    private static final int PRIORITY = 20;

    /**
     * 单实例过穿判定：按实例来源物品 + 护盾类型定义查询 pierce_through（默认不穿盾）；
     * 全局池实例无类型定义，不穿透。
     */
    private static boolean isPierceInstance(MinecraftServer server, ShieldInstance instance) {
        String typeName = instance.getShieldTypeName();
        if (typeName == null) {
            return false;
        }
        return DefsManager.resolveShieldTypeValueForItem(server, instance.getItemId(),
                typeName, "pierce_through", 0.0) >= 0.5;
    }

    @Override
    public void handle(DamageContext context) {
        Player shieldOwner = context.getShieldOwner();
        UUID shieldOwnerUUID = shieldOwner.getUUID();
        LivingEntity attackedEntity = context.getAttackedEntity();

        if (InvincibilityMarkerManager.hasMarker(attackedEntity)) {
            context.setCanceled(true);
            return;
        }

        if (context.isPlayerSelfDamage()) {
            return;
        }

        boolean isShieldSelfDamage = context.isShieldSelfDamage();

        if (!isShieldSelfDamage && attackedEntity instanceof Player) {
            if (!ShieldTransferManager.shouldProtectPlayer((Player) attackedEntity)) {
                return;
            }
        }

        List<ShieldInstance> instances = ShieldManager.getInstances(shieldOwnerUUID);
        if (instances.isEmpty()) {
            return;
        }

        double oldTotal = ShieldManager.getCurrentShield(shieldOwnerUUID);
        if (oldTotal <= 0) {
            return;
        }

        float originalDamage = context.getCurrentDamage();

        if (originalDamage <= 0) {
            return;
        }

        // 逐实例吸收：实例列表序 = 装备扫描序（Curios 优先，全局池末位）
        double remaining = originalDamage;
        boolean allHitPierce = true;
        for (ShieldInstance instance : instances) {
            if (remaining <= 0) {
                break;
            }
            if (instance.isBroken() || instance.getCurrentShield() <= 0) {
                continue;
            }
            double pool = instance.getCurrentShield();
            if (pool >= remaining) {
                instance.setCurrentShield(pool - remaining);
                // 受损（未破盾）即进入冷却充能：进度清零重新计时
                if (instance.getCurrentShield() < instance.getMaxShield()) {
                    instance.restartCooldown(ShieldManager.computeMaxCooldown(shieldOwnerUUID, instance.getItemId(),
                            instance.getShieldTypeName()));
                }
                remaining = 0;
            } else {
                remaining -= pool;
                instance.setCurrentShield(0);
                instance.startCooldown(ShieldManager.computeMaxCooldown(shieldOwnerUUID, instance.getItemId(),
                        instance.getShieldTypeName()));
                // 逐实例过穿判定：本次被打破的实例全部声明穿盾，剩余伤害才放行
                boolean pierce = isPierceInstance(shieldOwner.getServer(), instance);
                allHitPierce &= pierce;
                // 默认不过穿：不允许过穿的实例被打破后，剩余伤害不再传递给后续护盾实例（完全阻挡）
                if (!pierce) {
                    break;
                }
            }
        }

        ShieldManager.afterInstanceMutation(shieldOwnerUUID, oldTotal);

        if (remaining <= 0) {
            context.setCanceled(true);
            // 当承受护盾自伤或协议护盾自伤时，不施加无敌标记
            if (!isShieldSelfDamage) {
                InvincibilityMarkerManager.addMarker(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
            }
        } else if (allHitPierce) {
            // 穿盾（还原 856b1ce 之前的原始实现）：护盾吸收部分伤害后归零，
            // 剩余伤害重新作用于玩家（不取消事件并下调当前伤害，
            // 由 DamageManager 走 FINAL_DAMAGE 重施，不施加无敌标记）
            context.setCurrentDamage((float) remaining);
        } else {
            context.setCanceled(true);
            if (!isShieldSelfDamage) {
                InvincibilityMarkerManager.addMarker(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
            }
        }

        if (!isShieldSelfDamage) {
            ShieldTypeManager.processReflectAfterShieldDamage(shieldOwner, attackedEntity);
        }

        // 播放护盾受击音效
        if (!isShieldSelfDamage) {
            playShieldHitSound(attackedEntity);
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    private static void playShieldHitSound(LivingEntity entity) {
        String soundType = Config.getShieldHitSound();
        if ("none".equals(soundType)) {
            return;
        }
        if ("vanilla_hurt".equals(soundType)) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 1.0F);
        } else if ("shield_hit".equals(soundType)) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    ModSounds.SHIELD_HIT.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }
}
