package com.gy_mod.gy_trinket.client.attack_mode.burst_fire;

import com.gy_mod.gy_trinket.client.attack_mode.AttackModeClientUtil;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class BurstFireClientHandler {

    private static final Map<UUID, Boolean> COMBO_COOLDOWN_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REMAINING_COOLDOWN_TICKS = new ConcurrentHashMap<>();

    /** 点射进行中状态（由服务端同步） */
    private static final Map<UUID, Boolean> BURST_FIRING_STATE = new ConcurrentHashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new BurstFireClientHandler());
    }

    public static void updateComboCooldownState(UUID playerUUID, boolean inCooldown, int remainingTicks) {
        if (inCooldown) {
            COMBO_COOLDOWN_STATE.put(playerUUID, true);
            REMAINING_COOLDOWN_TICKS.put(playerUUID, remainingTicks);
        } else {
            COMBO_COOLDOWN_STATE.remove(playerUUID);
            REMAINING_COOLDOWN_TICKS.remove(playerUUID);
        }
    }

    /**
     * 更新点射进行中状态（由服务端同步）
     */
    public static void updateBurstFiringState(UUID playerUUID, boolean isBurstFiring) {
        if (isBurstFiring) {
            BURST_FIRING_STATE.put(playerUUID, true);
        } else {
            BURST_FIRING_STATE.remove(playerUUID);
        }
    }

    @SubscribeEvent
    public void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        UUID playerUUID = player.getUUID();

        Boolean inCooldown = COMBO_COOLDOWN_STATE.get(playerUUID);
        if (inCooldown != null && inCooldown) {
            player.resetAttackStrengthTicker();
        }
    }

    /**
     * 使用反射设置玩家攻击强度为满
     */
    public static void reflectAttackStrengthToFull(Player player) {
        AttackModeClientUtil.reflectAttackStrengthToFull(player);
    }

    public static boolean isInComboCooldown(UUID playerUUID) {
        return COMBO_COOLDOWN_STATE.getOrDefault(playerUUID, false);
    }

    /**
     * 客户端是否处于点射进行中状态
     */
    public static boolean isBurstFiring(UUID playerUUID) {
        return BURST_FIRING_STATE.getOrDefault(playerUUID, false);
    }

    /**
     * 点射期间或冷却期间，强袭自动攻击应被禁用
     */
    public static boolean isAssaultDisabled(UUID playerUUID) {
        return isBurstFiring(playerUUID) || isInComboCooldown(playerUUID);
    }

    public static int getRemainingCooldown(UUID playerUUID) {
        return REMAINING_COOLDOWN_TICKS.getOrDefault(playerUUID, 0);
    }

    public static void handleSyncComboCooldownOnClient(boolean inCooldown, int remainingTicks) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            UUID playerUUID = mc.player.getUUID();
            updateComboCooldownState(playerUUID, inCooldown, remainingTicks);
        }
    }

    public static void handleSyncAttackStrengthOnClient(boolean reflectToFull) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && reflectToFull) {
            reflectAttackStrengthToFull(mc.player);
        }
    }

    /**
     * 处理服务端同步的点射进行中状态
     */
    public static void handleSyncBurstFiringOnClient(boolean isBurstFiring) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            UUID playerUUID = mc.player.getUUID();
            updateBurstFiringState(playerUUID, isBurstFiring);
        }
    }
}
