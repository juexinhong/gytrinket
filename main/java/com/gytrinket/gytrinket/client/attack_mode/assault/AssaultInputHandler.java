package com.gytrinket.gytrinket.client.attack_mode.assault;

import com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil;
import com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler;
import com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackInputHandler;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * 强袭客户端输入处理
 * <p>
 * 按住左键时按攻击速度频率自动攻击准星对准的目标。
 * <p>
 * 兼容逻辑（由 AttackModeManager 统一管理）：
 * - 充能期间：不执行强袭自动攻击（充能期间的强袭由服务端协调器处理）
 * - 点射期间：不执行强袭自动攻击（点射自动攻击会触发强袭）
 * - 点射冷却期间：不执行强袭自动攻击
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class AssaultInputHandler {

    private static boolean isInAssaultMode = false;
    private static int attackCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Player player = minecraft.player;

        boolean isLeftClickDown = minecraft.options.keyAttack.isDown();

        // 充能攻击兼容：充能期间强袭不执行客户端自动攻击
        if (ChargedAttackInputHandler.isCharging()) {
            if (isInAssaultMode) {
                isInAssaultMode = false;
                attackCooldown = 0;
            }
            return;
        }

        // 点射兼容：点射期间和冷却期间，强袭不执行客户端自动攻击
        UUID playerUUID = player.getUUID();
        if (BurstFireClientHandler.isAssaultDisabled(playerUUID)) {
            if (isInAssaultMode) {
                isInAssaultMode = false;
                attackCooldown = 0;
            }
            return;
        }

        if (isLeftClickDown && AttackModeClientUtil.hasAssaultItem()) {
            // 充能攻击兼容：如果玩家同时有充能攻击物品，第一次按下左键时不执行强袭自动攻击
            boolean hasChargedAttackItem = AttackModeClientUtil.hasChargedAttackItem();
            if (!isInAssaultMode && hasChargedAttackItem) {
                float attackStrengthScale = player.getAttackStrengthScale(0.0f);
                if (attackStrengthScale >= 0.9f) {
                    // 不进入强袭模式，让充能系统处理
                    return;
                }
            }

            if (!isInAssaultMode) {
                isInAssaultMode = true;
            }

            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            float attackStrengthScale = player.getAttackStrengthScale(0.0f);
            if (attackStrengthScale < 1.0f) {
                return;
            }

            Entity target = AttackModeClientUtil.findTargetInCrosshair(player);
            if (target instanceof LivingEntity) {
                minecraft.gameMode.attack(player, target);

                PacketDistributor.sendToServer(new NetworkHandler.AssaultAttackPayload());

                AttackModeClientUtil.resetAttackStrengthTicker(player);

                double baseAttackSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
                attackCooldown = (int) Math.max(1, Math.ceil(20.0 / baseAttackSpeed) - 1);
            }
        } else {
            if (isInAssaultMode) {
                isInAssaultMode = false;
                attackCooldown = 0;
            }
        }
    }

    public static boolean isAssaultMode() {
        return isInAssaultMode;
    }
}
