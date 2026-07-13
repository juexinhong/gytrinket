package com.gy_mod.gy_trinket.core.grudge;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 积怨管理器 - 服务端核心逻辑
 * <p>
 * 积怨模块是充能攻击的附属模块，需要依赖充能攻击模块。
 * 当玩家处于充能状态时，每点损失的生命或护盾值转化为临时的充能速率。
 * <p>
 * 机制：
 * 1. 玩家充能期间，损失生命或护盾值时，按比率累加到临时充能速率
 * 2. 临时充能速率始终快速消退：每tick消退 fadeBase + 当前值 * fadePercent
 * 3. 退出充能状态时，立即清除临时充能速率
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class GrudgeManager {

    // 拥有积怨能力的玩家集合
    private static final Set<UUID> PLAYER_HAS_GRUDGE = new java.util.concurrent.CopyOnWriteArraySet<>();

    // 玩家积怨数据
    private static final Map<UUID, GrudgeData> PLAYER_GRUDGE_DATA = new ConcurrentHashMap<>();

    private GrudgeManager() {}

    /**
     * 判断玩家是否拥有积怨能力
     */
    public static boolean hasGrudge(Player player) {
        return PLAYER_HAS_GRUDGE.contains(player.getUUID());
    }

    /**
     * 设置玩家是否拥有积怨能力
     */
    public static void setHasGrudge(UUID playerUUID, boolean has) {
        if (has) {
            PLAYER_HAS_GRUDGE.add(playerUUID);
        } else {
            PLAYER_HAS_GRUDGE.remove(playerUUID);
            PLAYER_GRUDGE_DATA.remove(playerUUID);
        }
    }

    /**
     * 获取玩家当前的积怨充能速率
     */
    public static double getTotalGrudgeChargeRate(UUID playerUUID) {
        GrudgeData data = PLAYER_GRUDGE_DATA.get(playerUUID);
        return data != null ? data.rateValue : 0;
    }

    /**
     * 清除玩家的所有积怨数据
     */
    public static void clearGrudge(UUID playerUUID) {
        PLAYER_GRUDGE_DATA.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!hasGrudge(player)) {
            return;
        }

        if (ChargedAttackManager.isCharging(player)) {
            GrudgeData data = PLAYER_GRUDGE_DATA.computeIfAbsent(uuid, k -> new GrudgeData());

            // 首次进入充能状态，初始化生命和护盾值
            if (!data.wasCharging) {
                data.prevHealth = player.getHealth();
                data.prevShield = ShieldManager.getCurrentShield(uuid);
                data.wasCharging = true;
            }

            // 检测生命和护盾损失
            float currentHealth = player.getHealth();
            double currentShield = ShieldManager.getCurrentShield(uuid);

            double healthLost = Math.max(0, data.prevHealth - currentHealth);
            double shieldLost = Math.max(0, data.prevShield - currentShield);
            double totalLost = healthLost + shieldLost;

            if (totalLost > 0) {
                double conversionRatio = Config.getGrudgeConversionRatio();
                data.rateValue += totalLost * conversionRatio;
            }

            data.prevHealth = currentHealth;
            data.prevShield = currentShield;

            // 快速消退：每tick消退 fadeBase + 当前值 * fadePercent
            if (data.rateValue > 0) {
                double fadeBase = Config.getGrudgeFadeBase();
                double fadePercent = Config.getGrudgeFadePercent();
                double decay = fadeBase + data.rateValue * fadePercent;
                data.rateValue -= decay;
                if (data.rateValue <= 0) {
                    data.rateValue = 0;
                    PLAYER_GRUDGE_DATA.remove(uuid);
                }
            }
        } else {
            // 不在充能状态，立即清除所有积怨数据
            GrudgeData data = PLAYER_GRUDGE_DATA.get(uuid);
            if (data != null && data.wasCharging) {
                PLAYER_GRUDGE_DATA.remove(uuid);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_HAS_GRUDGE.remove(uuid);
        PLAYER_GRUDGE_DATA.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_GRUDGE_DATA.remove(uuid);
    }

    public static void clearAllData() {
        PLAYER_HAS_GRUDGE.clear();
        PLAYER_GRUDGE_DATA.clear();
    }

    /**
     * 积怨数据 - 每个玩家的积怨状态
     */
    private static class GrudgeData {
        double rateValue;
        float prevHealth;
        double prevShield;
        boolean wasCharging;

        GrudgeData() {
            this.rateValue = 0;
            this.prevHealth = 0;
            this.prevShield = 0;
            this.wasCharging = false;
        }
    }
}
