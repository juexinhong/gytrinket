package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@EventBusSubscriber(modid = gytrinket.MODID)
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
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LivingEntity attackedEntity = event.getEntity();
        DamageSource source = event.getSource();

        // 【免疫跳过】护盾事件链的介入点早于（或可能早于）原版全部免疫检查：
        // 玩家免疫该伤害时原版本会拒绝该伤害，但护盾在事件阶段介入会白白消耗护盾值。
        // 此处按原版 hurt() 顺序复现事件之后的全部免疫分支：
        //   isInvulnerableTo（实体类型火免/摔落/冰冻/凋零标签免疫、创造无敌）
        //   → 创造模式（Player.hurt: getAbilities().invulnerable）
        //   → isDeadOrDying → 火焰标签伤害 + 抗火药水（原版此条内联在 hurt() 中，无 API）
        // 免疫时整条伤害处理链不介入（不消耗护盾、不触发受击延长冷却/反射/音效）。
        if (attackedEntity.isInvulnerableTo(source)
            || (attackedEntity instanceof Player creativePlayer
                && creativePlayer.getAbilities().invulnerable
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            || attackedEntity.isDeadOrDying()
            || (source.is(DamageTypeTags.IS_FIRE) && attackedEntity.hasEffect(MobEffects.FIRE_RESISTANCE))) {
            return;
        }

        var damageTypeKey = source.typeHolder().unwrapKey();
        if (damageTypeKey.orElse(null) == ModDamageTypes.FINAL_DAMAGE) {
            return;
        }

        if (event.getAmount() <= 0) {
            return;
        }

        boolean isShieldSelfDamage = damageTypeKey.map(type ->
            type == ModDamageTypes.SHIELD_SELF_DAMAGE || type == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE
        ).orElse(false);

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
                    attackedEntity.level(),
                    directEntity,
                    attacker
                ), finalDamage);
            }
        }
    }

    private static boolean shouldPreventFinalDamageConversion(LivingEntity attackedEntity, DamageSource source) {
        var damageTypeKey = source.typeHolder().unwrapKey().orElse(null);
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
