package com.gytrinket.gytrinket.core.special_effect.explosive_shield;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.core.explosion.SimulatedExplosion;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ExplosiveShieldEffect {

    @SubscribeEvent
    public static void onShieldBreak(ShieldBreakEvent event) {
        Player player = event.getPlayer();

        if (!hasExplosiveShieldItem(player)) {
            return;
        }

        List<LivingEntity> effectCenters;
        if (ShieldTransferManager.isShieldTransferEnabled(player.getUUID())) {
            // 护盾移植模式：在被保护实体位置触发（无受保护实体时玩家自身不触发）
            effectCenters = ShieldTransferManager.getProtectedEntities(player.getUUID(), player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        double baseRadius = Config.EXPLOSIVE_SHIELD_RADIUS.get();
        double radius = baseRadius * shieldEffectRadius;

        double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
        double baseDamage = Config.EXPLOSIVE_SHIELD_DAMAGE.get();
        float damage = (float)(baseDamage * shieldEffect);

        for (LivingEntity effectCenter : effectCenters) {
            if (effectCenter == null || !effectCenter.isAlive()) {
                continue;
            }

            if (effectCenter.level() instanceof ServerLevel serverLevel) {
                NetworkHandler.sendExplosiveShieldFlashToAll(serverLevel, effectCenter);
            }

            DamageSource damageSource = effectCenter.damageSources().explosion(effectCenter, player);

            SimulatedExplosion.execute(
                    effectCenter.level(),
                    effectCenter.position(),
                    radius,
                    damage,
                    damageSource,
                    entity -> entity instanceof Mob mob && !mob.isDeadOrDying()
                            && HostileTargetManager.shouldAttackPlayer(mob, player),
                    false,
                    player
            );
        }
    }

    private static boolean hasExplosiveShieldItem(Player player) {
        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getAllEquippedStacks(player)) {
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(player.getUUID(), stack) && Config.isExplosiveShieldItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}