package com.gy_mod.gy_trinket.client.burst_fire;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
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
     * 使用反射设置玩家攻击强度为10（100%攻击强度）
     */
    public static void reflectAttackStrengthToFull(Player player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 10);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            gytrinket.LOGGER.warn("Failed to reflect attack strength to full via reflection", e);
        }
    }

    public static boolean isInComboCooldown(UUID playerUUID) {
        return COMBO_COOLDOWN_STATE.getOrDefault(playerUUID, false);
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
}