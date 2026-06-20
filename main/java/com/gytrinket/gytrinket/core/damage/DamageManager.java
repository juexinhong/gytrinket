package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.damagesource.DamageSource;
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
