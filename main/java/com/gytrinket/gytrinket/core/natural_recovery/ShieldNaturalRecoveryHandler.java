package com.gytrinket.gytrinket.core.natural_recovery;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.event.ShieldCooldownCompleteEvent;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 护盾自然恢复处理器
 * <p>
 * 当玩家光点核心中存在指定物品时启用：
 * - 将护盾自然恢复值设为配置值（默认0.1%/刻）
 * - 提供额外的恢复修正值
 * <p>
 * 恢复修正值：
 * - 玩家生命恢复修正值（默认1）
 * - 护盾自然恢复修正值（默认1）
 * <p>
 * 当护盾冷却完成后：
 * - 玩家生命恢复修正值 = 配置值（默认0.5）
 * - 护盾自然恢复修正值 = 配置值（默认0.75）
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class ShieldNaturalRecoveryHandler {

    /** 玩家是否启用护盾自然恢复的缓存 */
    private static final Set<UUID> PLAYER_HAS_SHIELD_RECOVERY_ITEM = new HashSet<>();

    /** 玩家生命恢复修正值：玩家UUID -> 修正值 */
    private static final Map<UUID, Double> PLAYER_HEALTH_RECOVERY_MODIFIER = new HashMap<>();

    /** 护盾自然恢复修正值：玩家UUID -> 修正值 */
    private static final Map<UUID, Double> SHIELD_RECOVERY_MODIFIER = new HashMap<>();

    /** 默认玩家生命恢复修正值 */
    private static final double DEFAULT_HEALTH_RECOVERY_MODIFIER = 1.0;

    /** 默认护盾自然恢复修正值 */
    private static final double DEFAULT_SHIELD_RECOVERY_MODIFIER = 1.0;

    /**
     * 检查玩家是否启用了护盾自然恢复
     */
    public static boolean hasShieldNaturalRecovery(UUID playerUUID) {
        return PLAYER_HAS_SHIELD_RECOVERY_ITEM.contains(playerUUID);
    }

    /**
     * 获取玩家生命恢复修正值
     */
    public static double getPlayerHealthRecoveryModifier(UUID playerUUID) {
        return PLAYER_HEALTH_RECOVERY_MODIFIER.getOrDefault(playerUUID, DEFAULT_HEALTH_RECOVERY_MODIFIER);
    }

    /**
     * 获取护盾自然恢复修正值
     */
    public static double getShieldRecoveryModifier(UUID playerUUID) {
        return SHIELD_RECOVERY_MODIFIER.getOrDefault(playerUUID, DEFAULT_SHIELD_RECOVERY_MODIFIER);
    }

    /**
     * 获取护盾自然恢复基础值（从配置读取）
     */
    public static double getShieldRecoveryBase() {
        return Config.getNaturalRecoveryShieldRecoveryPerTick();
    }

    /**
     * 设置恢复修正值
     */
    private static void setRecoveryModifiers(UUID playerUUID, double healthModifier, double shieldModifier) {
        PLAYER_HEALTH_RECOVERY_MODIFIER.put(playerUUID, healthModifier);
        SHIELD_RECOVERY_MODIFIER.put(playerUUID, shieldModifier);
    }

    /**
     * 设置为默认恢复修正值
     */
    private static void setDefaultModifiers(UUID playerUUID) {
        setRecoveryModifiers(playerUUID, DEFAULT_HEALTH_RECOVERY_MODIFIER, DEFAULT_SHIELD_RECOVERY_MODIFIER);
    }

    /**
     * 设置为护盾存在时的恢复修正值（从配置读取）
     */
    private static void setShieldPresentModifiers(UUID playerUUID) {
        double healthModifier = Config.getNaturalRecoveryShieldPresentHealthModifier();
        double shieldModifier = Config.getNaturalRecoveryShieldPresentShieldModifier();
        setRecoveryModifiers(playerUUID, healthModifier, shieldModifier);
    }

    /**
     * 监听属性计算完毕事件，检测玩家是否有护盾自然恢复物品
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            PLAYER_HAS_SHIELD_RECOVERY_ITEM.remove(playerUUID);
            return;
        }

        boolean hasRecoveryItem = false;
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack) && Config.isShieldNaturalRecoveryItem(stack.getItem())) {
                hasRecoveryItem = true;
                break;
            }
        }

        if (hasRecoveryItem) {
            PLAYER_HAS_SHIELD_RECOVERY_ITEM.add(playerUUID);
        } else {
            PLAYER_HAS_SHIELD_RECOVERY_ITEM.remove(playerUUID);
            PLAYER_HEALTH_RECOVERY_MODIFIER.remove(playerUUID);
            SHIELD_RECOVERY_MODIFIER.remove(playerUUID);
        }
    }

    /**
     * 监听护盾破裂事件
     * 设置玩家生命恢复修正值为1，护盾自然恢复修正值为1
     */
    @SubscribeEvent
    public static void onShieldBreak(ShieldBreakEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        if (PLAYER_HAS_SHIELD_RECOVERY_ITEM.contains(playerUUID)) {
            setDefaultModifiers(playerUUID);
        }
    }

    /**
     * 监听护盾冷却完成事件
     * 设置玩家生命恢复修正值为配置值，护盾自然恢复修正值为配置值
     */
    @SubscribeEvent
    public static void onShieldCooldownComplete(ShieldCooldownCompleteEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        if (PLAYER_HAS_SHIELD_RECOVERY_ITEM.contains(playerUUID)) {
            setShieldPresentModifiers(playerUUID);
        }
    }

    /**
     * 监听玩家登录事件
     * 检测玩家的当前护盾值是否为0
     * - 为0：设置玩家生命恢复修正值为1，护盾自然恢复修正值为1
     * - 不为0：设置玩家生命恢复修正值为配置值，护盾自然恢复修正值为配置值
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUUID();

        if (!PLAYER_HAS_SHIELD_RECOVERY_ITEM.contains(playerUUID)) {
            return;
        }

        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield <= 0) {
            setDefaultModifiers(playerUUID);
        } else {
            setShieldPresentModifiers(playerUUID);
        }
    }

    /**
     * 监听玩家退出事件，清理数据
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        PLAYER_HEALTH_RECOVERY_MODIFIER.remove(playerUUID);
        SHIELD_RECOVERY_MODIFIER.remove(playerUUID);
    }
}