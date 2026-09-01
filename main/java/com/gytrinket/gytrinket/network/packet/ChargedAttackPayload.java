package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.PlayerAttackLockManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackEvent;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackSweepHandler;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ChargedAttackPayload(int action) implements CustomPacketPayload {
    public static final Type<ChargedAttackPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "charged_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChargedAttackPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ChargedAttackPayload::action,
        ChargedAttackPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ChargedAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            UUID uuid = player.getUUID();

            if (!ChargedAttackManager.hasChargedAttack(player)) {
                return;
            }

            switch (payload.action) {
                case 0 -> {
                    // 客户端请求开始充能
                    if (PlayerAttackLockManager.isLocked(uuid)) {
                        // 攻击锁定时：即时结算 - 不充能，立即触发原版攻击
                        // 原版攻击会被 AttackModeManager 的锁定检查拦截（LivingEntity），
                        // 除非目标是无生命实体（船、矿车等），此时攻击正常通过
                        ChargedAttackManager.cancelCharging(uuid);
                        Entity target = ChargedAttackSweepHandler.findTargetInCrosshair(player, false);
                        if (target != null) {
                            player.attack(target);
                        }
                        // 同步0到客户端，重置充能状态（包括isCharging）
                        NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                    } else {
                        ChargedAttackManager.startCharging(uuid);
                    }
                }
                case 1 -> {
                    // 更新充能（服务端独立计算，此处仅确认状态）
                    ChargedAttackManager.updateCharging(uuid, player);
                }
                case 2 -> {
                    // 释放攻击（非剑类）
                    // 修复时序竞争：tick可能已先调用releaseCharge将充能值存入Tracker，
                    // 此处优先从Tracker获取；若Tracker无值则尝试releaseCharge（兼容开发环境时序）
                    double chargeValue = ChargedAttackDamageTracker.getChargeValue(uuid);
                    if (chargeValue <= 0) {
                        chargeValue = ChargedAttackManager.releaseCharge(uuid);
                    }
                    // 发布充能释放事件（供幽灵机身等系统使用）
                    if (chargeValue > 0) {
                        postReleasedEvent(player);
                        // 非剑类走客户端原版攻击，原版自带成功命中扣1点耐久，无需额外处理
                    }
                    // 对射线上非生命实体施加充能伤害（穿透非生命实体）
                    ChargedAttackSweepHandler.damageNonLivingTargetsAlongRaycast(player, chargeValue);
                    // 充能释放后的点射触发在 AttackModeManager.onPlayerAttack 中处理
                    // 同步0到客户端，清空HUD显示
                    NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                }
                case 3 -> {
                    // 取消充能（无目标释放，直接清空）
                    ChargedAttackManager.cancelCharging(uuid);
                    ChargedAttackDamageTracker.removePlayer(uuid);
                    // 同步0到客户端，清空HUD显示
                    NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                }
                case 4 -> {
                    // 充能横扫攻击释放（剑类物品，替代原版attack）
                    double chargeValue = ChargedAttackDamageTracker.getChargeValue(uuid);
                    if (chargeValue <= 0) {
                        chargeValue = ChargedAttackManager.releaseCharge(uuid);
                    }
                    if (chargeValue > 0) {
                        // 发布充能释放事件（供幽灵机身等系统使用）
                        postReleasedEvent(player);
                        ChargedAttackSweepHandler.executeChargedSweepAttack(player, chargeValue);
                        // 对射线上非生命实体施加充能伤害（穿透非生命实体）
                        ChargedAttackSweepHandler.damageNonLivingTargetsAlongRaycast(player, chargeValue);
                    }
                    NetworkHandler.sendChargedAttackSyncToPlayer(player, 0);
                }
            }
        });
    }

    /**
     * 发布充能释放事件到 NeoForge 事件总线
     */
    private static void postReleasedEvent(ServerPlayer player) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            new ChargedAttackEvent(ChargedAttackEvent.Type.RELEASED, player));
    }
}
