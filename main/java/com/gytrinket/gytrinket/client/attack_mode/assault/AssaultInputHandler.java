package com.gytrinket.gytrinket.client.attack_mode.assault;

import com.gytrinket.gytrinket.client.attack_mode.AttackModeClientUtil;
import com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler;
import com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackInputHandler;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.network.packet.AssaultAttackPayload;
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
 * - 同时拥有充能攻击物品：强袭完全让位，左键输入全部由充能系统处理
 *   （充能期间的强袭效果由服务端协调器按攻速触发）
 * - 充能期间：不执行强袭自动攻击（防御性检查，正常已被上一条覆盖）
 * - 点射期间：不执行强袭自动攻击（点射自动攻击会触发强袭）
 * - 点射冷却期间：不执行强袭自动攻击
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class AssaultInputHandler {

    private static boolean isInAssaultMode = false;
    private static int attackCooldown = 0;
    // 进入强袭前的原版疾跑设置快照（退出强袭时恢复）
    private static boolean wasToggleSprint = false;
    private static boolean wasToggleActive = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (isInAssaultMode) {
                exitAssaultMode(minecraft);
            }
            return;
        }

        Player player = minecraft.player;

        boolean isLeftClickDown = minecraft.options.keyAttack.isDown();

        // 充能攻击兼容：充能期间强袭不执行客户端自动攻击
        if (ChargedAttackInputHandler.isCharging()) {
            if (isInAssaultMode) {
                exitAssaultMode(minecraft);
            }
            return;
        }

        // 点射兼容：点射期间和冷却期间，强袭不执行客户端自动攻击
        UUID playerUUID = player.getUUID();
        if (BurstFireClientHandler.isAssaultDisabled(playerUUID)) {
            if (isInAssaultMode) {
                exitAssaultMode(minecraft);
            }
            return;
        }

        // 充能攻击兼容：同时拥有充能攻击物品时，强袭完全让位。
        // 旧的"攻击强度 < 0.9 放行"门控存在时序漏洞：充能释放（或攻击强度 < 0.5 提前释放）后
        // 攻击强度归零，左键仍按着时强袭获得进入许可并持续接管——每次强袭攻击都会重置攻击强度，
        // 攻击强度恢复满也不会切回充能，导致充能期间强袭自动攻击误触发
        if (AttackModeClientUtil.hasChargedAttackItem()) {
            if (isInAssaultMode) {
                exitAssaultMode(minecraft);
            }
            return;
        }

        if (isLeftClickDown && AttackModeClientUtil.hasAssaultItem()) {
            if (!isInAssaultMode) {
                isInAssaultMode = true;
                disableAutoSprint(minecraft);
            }

            // 强袭期间禁止疾跑：原版 aiStep 的自动疾跑（切换疾跑/疾跑键）每 tick 会重新拉起疾跑，
            // 仅 setSprinting(false) 会被覆盖。进入强袭时已临时关闭"切换疾跑"设置切断自动疾跑源，
            // 此处再每 tick 压制疾跑状态，确保 MOVEMENT_SPEED 属性不再增删疾跑修改器，
            // 避免客户端 FOV 高频缩放
            player.setSprinting(false);

            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            float attackStrengthScale = player.getAttackStrengthScale(0.0f);
            if (attackStrengthScale < 1.0f) {
                return;
            }

            // 仅查找生物目标：忽略原版准星指向的非生物无效目标（展示框/画等），继续光束查找最近生物
            Entity target = AttackModeClientUtil.findTargetInCrosshair(player, true);
            if (target instanceof LivingEntity) {
                minecraft.gameMode.attack(player, target);

                PacketDistributor.sendToServer(new AssaultAttackPayload());

                AttackModeClientUtil.resetAttackStrengthTicker(player);

                double baseAttackSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
                attackCooldown = (int) Math.max(1, Math.ceil(20.0 / baseAttackSpeed) - 1);
            }
        } else {
            if (isInAssaultMode) {
                exitAssaultMode(minecraft);
            }
        }
    }

    public static boolean isAssaultMode() {
        return isInAssaultMode;
    }

    /**
     * 进入强袭模式：快照原版"切换疾跑"设置并临时关闭，切断 aiStep 的自动疾跑源。
     * <p>
     * 原版 keySprint 为 ToggleKeyMapping，其激活态受 toggleSprint 设置控制：
     * 设置关闭后 isDown() 走普通按键逻辑，自动疾跑判定不再成立。
     */
    private static void disableAutoSprint(Minecraft minecraft) {
        wasToggleSprint = minecraft.options.toggleSprint().get();
        wasToggleActive = minecraft.options.keySprint.isDown();
        if (wasToggleSprint) {
            minecraft.options.toggleSprint().set(false);
        }
    }

    /**
     * 退出强袭模式：恢复原版"切换疾跑"设置及切换激活状态
     */
    private static void restoreSprintOptions(Minecraft minecraft) {
        if (wasToggleSprint) {
            minecraft.options.toggleSprint().set(true);
            if (minecraft.options.keySprint.isDown() != wasToggleActive) {
                // ToggleKeyMapping 的 setDown(true) 为边沿翻转，用于恢复进入前的切换激活态
                minecraft.options.keySprint.setDown(true);
            }
        }
        wasToggleSprint = false;
        wasToggleActive = false;
    }

    /**
     * 退出强袭模式：重置攻击状态并恢复疾跑设置
     */
    private static void exitAssaultMode(Minecraft minecraft) {
        isInAssaultMode = false;
        attackCooldown = 0;
        restoreSprintOptions(minecraft);
    }
}
