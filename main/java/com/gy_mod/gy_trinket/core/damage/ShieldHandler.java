package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield.type.IShieldType;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.core.sound.ModSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

public class ShieldHandler implements DamageHandler {

    private static final int PRIORITY = 20;

    /**
     * 设置护盾值，根据持有者类型选择合适的方法
     */
    private static void setCurrentShieldForEntity(Player shieldOwner, UUID shieldOwnerUUID, double value) {
        if (shieldOwner instanceof ServerPlayer serverPlayer) {
            ShieldManager.setCurrentShield(serverPlayer, value);
        } else {
            ShieldManager.setCurrentShield(shieldOwnerUUID, value);
        }
    }

    /**
     * 穿盾判定：遍历护盾持有者实际生效（active）的护盾类型实例，
     * 按各实例来源物品的护盾类型定义查询 pierce_through（默认不穿盾）；
     * 只要有一个定义不穿盾，则整体不穿盾；无任何生效实例时同样不穿盾。
     */
    private static boolean isPierceThroughForOwner(Player shieldOwner) {
        MinecraftServer server = shieldOwner.getServer();
        if (server == null) {
            return false;
        }
        List<IShieldType.ShieldTypeData> types = ShieldTypeManager.getPlayerShieldTypes(shieldOwner.getUUID());
        if (types.isEmpty()) {
            return false;
        }
        for (IShieldType.ShieldTypeData data : types) {
            if (!data.active()) {
                continue;
            }
            String itemId = ForgeRegistries.ITEMS.getKey(data.source().getItem()).toString();
            boolean pierce = DefsManager.resolveShieldTypeValueForItem(server, itemId,
                    data.type().getName(), "pierce_through", 0.0) >= 0.5;
            if (!pierce) {
                return false;
            }
        }
        return true;
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

        ShieldData shieldData = ShieldManager.getShieldData(shieldOwnerUUID);
        if (shieldData == null) {
            return;
        }

        double currentShield = shieldData.getCurrentShield();
        if (currentShield <= 0) {
            return;
        }

        float originalDamage = context.getCurrentDamage();

        if (originalDamage <= 0) {
            return;
        }

        if (currentShield >= originalDamage) {
            setCurrentShieldForEntity(shieldOwner, shieldOwnerUUID, currentShield - originalDamage);
            context.setCanceled(true);
            
            // 当承受护盾自伤或协议护盾自伤时，不施加无敌标记
            if (!isShieldSelfDamage) {
                InvincibilityMarkerManager.addMarker(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
            }
        } else {
            setCurrentShieldForEntity(shieldOwner, shieldOwnerUUID, 0);

            if (isPierceThroughForOwner(shieldOwner)) {
                // 穿盾（还原 856b1ce 之前的原始实现）：护盾吸收部分伤害后归零，
                // 剩余伤害重新作用于玩家（不取消事件并下调当前伤害，
                // 由 DamageManager 走 FINAL_DAMAGE 重施，不施加无敌标记）
                context.setCurrentDamage((float) (originalDamage - currentShield));
            } else {
                context.setCanceled(true);
                // 当承受护盾自伤或协议护盾自伤时，不施加无敌标记
                if (!isShieldSelfDamage) {
                    InvincibilityMarkerManager.addMarker(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
                }
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

