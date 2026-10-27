package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.core.damage.GyDamageType;

import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class DamageManager {

    private static boolean initialized = false;

    private DamageManager() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DamageHandlerChain.getInstance().registerHandler(new ArcBarrierHandler());
        DamageHandlerChain.getInstance().registerHandler(new ReshapingDamageHandler());
        DamageHandlerChain.getInstance().registerHandler(new BarrierHandler());
        DamageHandlerChain.getInstance().registerHandler(new BinaryProtocolHandler());
        DamageHandlerChain.getInstance().registerHandler(new AdaptiveArmorHandler());
        DamageHandlerChain.getInstance().registerHandler(new DamageNotificationHandler());
        DamageHandlerChain.getInstance().registerHandler(new ShieldDamageReductionHandler());
        DamageHandlerChain.getInstance().registerHandler(new ShieldSelfDamageReductionHandler());
        DamageHandlerChain.getInstance().registerHandler(new ReflectDamageHandler());
        DamageHandlerChain.getInstance().registerHandler(new ShieldParticleHandler());
        DamageHandlerChain.getInstance().registerHandler(new ShieldHandler());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LivingEntity attackedEntity = event.getEntity();
        DamageSource source = event.getSource();

        // 【仅服务端结算】客户端也会为摔落等本地预测伤害调用 hurt() 并 post 本事件；
        // 若在客户端跑护盾链，会改写客户端侧护盾池/无敌标记（单人同 JVM 时与服务端共享静态），
        // 客户端先加的“格挡无敌标记”还会把服务端随后真正的伤害事件在 HIGHEST 阶段取消，
        // 表现为“摔落伤害护盾掉一半、玩家生命不掉”。
        if (attackedEntity.getLevel().isClientSide) {
            return;
        }

        // 【免疫跳过】Forge 在 hurt() 最开头 post LivingAttackEvent（玩家由 Player.hurt 的
        // onPlayerAttack hook post，其余实体由 LivingEntity.hurt 的 onLivingAttack post），
        // 均早于原版全部免疫检查：玩家免疫该伤害时原版本会拒绝该伤害，但护盾在事件阶段
        // 介入会白白消耗护盾值。此处按原版 hurt() 顺序复现事件之后的全部免疫分支：
        //   isInvulnerableTo（实体类型火免/摔落/冰冻/凋零标签免疫、创造无敌）
        //   → 创造模式（Player.hurt: getAbilities().invulnerable，事件 hook 早于此检查）
        //   → isDeadOrDying → 火焰标签伤害 + 抗火药水（原版此条内联在 hurt() 中，无 API）
        // 免疫时整条伤害处理链不介入（不消耗护盾、不触发受击延长冷却/反射/音效）。
        if (attackedEntity.isInvulnerableTo(source)
            || (attackedEntity instanceof Player creativePlayer
                && creativePlayer.getAbilities().invulnerable
                && !source.isBypassInvul())
            || attackedEntity.isDeadOrDying()
            || (source.isFire() && attackedEntity.hasEffect(MobEffects.FIRE_RESISTANCE))) {
            return;
        }

        GyDamageType damageType = ModDamageTypes.typeOf(source);
        // FINAL_DAMAGE：本就绕过护盾链；EXECUTE_DAMAGE：斩杀重施加，
        // 不经护盾链削减，也防止护盾反射与斩杀之间互相往返递归
        if (damageType == ModDamageTypes.FINAL_DAMAGE || damageType == ModDamageTypes.EXECUTE_DAMAGE) {
            return;
        }

        if (event.getAmount() <= 0) {
            return;
        }

        boolean isShieldSelfDamage = damageType == ModDamageTypes.SHIELD_SELF_DAMAGE
                || damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE;

        Player shieldOwner = null;

        if (attackedEntity instanceof Player player) {
            shieldOwner = player;
        } else if (!isShieldSelfDamage) {
            UUID ownerUUID = ShieldTransferManager.getShieldOwnerUUID(attackedEntity);
            if (ownerUUID != null) {
                shieldOwner = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerUUID);
            }
        }

        if (shieldOwner == null) {
            return;
        }

        @Nullable LivingEntity attacker = source.getEntity() instanceof LivingEntity ? (LivingEntity) source.getEntity() : null;

        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof Projectile projectile && !isShieldSelfDamage) {
            ShieldTypeManager.recordProjectileForReflect(shieldOwner, projectile);
        }

        float originalDamage = event.getAmount();
        DamageContext context = new DamageContext(source, attacker, attackedEntity, shieldOwner, originalDamage);
        DamageHandlerChain.getInstance().process(context);

        if (context.isCanceled()) {
            event.setCanceled(true);
        } else if (context.getCurrentDamage() != originalDamage) {
            if (shouldPreventFinalDamageConversion(attackedEntity, source)) {
                return;
            }

            event.setCanceled(true);
            float finalDamage = context.getCurrentDamage();
            if (finalDamage > 0) {
                attackedEntity.hurt(ModDamageTypes.getFinalDamageSource(
                    attackedEntity.getLevel(),
                    directEntity,
                    attacker
                ), finalDamage);
            }
        }
    }

    private static boolean shouldPreventFinalDamageConversion(LivingEntity attackedEntity, DamageSource source) {
        var damageTypeKey = ModDamageTypes.typeOf(source);
        if (damageTypeKey == ModDamageTypes.PLAYER_SELF_DAMAGE ||
            damageTypeKey == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
            return true;
        }

        if (!(attackedEntity instanceof Player player)) {
            return false;
        }

        if (ShieldManager.getCurrentShield(player.getUUID()) <= 0) {
            return false;
        }

        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            return true;
        }

        return false;
    }
}
