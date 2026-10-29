package com.gytrinket.gytrinket.core.shield;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 护盾自然恢复（特殊机制「再生护盾」）
 * <p>
 * 物品级定义（{@code item_definitions} 的 {@code values.shield_natural_recovery_items}）：
 * <ul>
 *   <li>{@code regen_per_second}：护盾再生速度（%/秒，按每个实例自身的最大护盾值计算）</li>
 *   <li>{@code shield_present_health_modifier}：有护盾值时的玩家生命再生效率</li>
 *   <li>{@code shield_present_shield_modifier}：有护盾值时的护盾再生效率</li>
 *   <li>{@code no_shield_health_modifier}：无护盾值时的玩家生命再生效率</li>
 * </ul>
 * 有护盾值 = 至少一个护盾实例池量 &gt; 0；全部实例池量为 0 视为无护盾值。
 * 所有数值走默认单/叠机制（stackable 求和、非叠加取最大），未声明时回退 {@code MechanicValueDefs} 默认值。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldNaturalRecoveryHandler {

    /** 机制集合名 */
    public static final String MECHANIC_SET = "shield_natural_recovery_items";

    /** 参数键：护盾再生速度（%/秒） */
    public static final String PARAM_REGEN_PER_SECOND = "regen_per_second";
    /** 参数键：有护盾值时的玩家生命再生效率 */
    public static final String PARAM_PRESENT_HEALTH = "shield_present_health_modifier";
    /** 参数键：有护盾值时的护盾再生效率 */
    public static final String PARAM_PRESENT_SHIELD = "shield_present_shield_modifier";
    /** 参数键：无护盾值时的玩家生命再生效率 */
    public static final String PARAM_NO_SHIELD_HEALTH = "no_shield_health_modifier";

    /** 参数回退值（与 MechanicValueDefs 中注册的默认值保持一致） */
    public static final double DEFAULT_REGEN_PER_SECOND = 0.02;
    public static final double DEFAULT_PRESENT_HEALTH = 0.5;
    public static final double DEFAULT_PRESENT_SHIELD = 0.75;
    public static final double DEFAULT_NO_SHIELD_HEALTH = 1.0;

    /** 玩家是否装备了再生护盾机制物品的缓存（属性重算时刷新，避免每刻扫描背包） */
    private static final Set<UUID> PLAYER_HAS_SHIELD_RECOVERY_ITEM = new HashSet<>();

    private ShieldNaturalRecoveryHandler() {}

    /** 玩家是否启用了护盾自然恢复（装备了声明该机制的物品且未被禁用） */
    public static boolean hasShieldNaturalRecovery(UUID playerUUID) {
        return PLAYER_HAS_SHIELD_RECOVERY_ITEM.contains(playerUUID);
    }

    /** 护盾再生速度（%/秒）：每个受损实例按其自身最大护盾值 × 该比例 / 秒恢复 */
    public static double getShieldRegenPerSecond(UUID playerUUID) {
        return resolve(playerUUID, PARAM_REGEN_PER_SECOND, DEFAULT_REGEN_PER_SECOND);
    }

    /** 有护盾值时的玩家生命再生效率 */
    public static double getShieldPresentHealthModifier(UUID playerUUID) {
        return resolve(playerUUID, PARAM_PRESENT_HEALTH, DEFAULT_PRESENT_HEALTH);
    }

    /** 有护盾值时的护盾再生效率 */
    public static double getShieldPresentShieldModifier(UUID playerUUID) {
        return resolve(playerUUID, PARAM_PRESENT_SHIELD, DEFAULT_PRESENT_SHIELD);
    }

    /** 无护盾值时的玩家生命再生效率 */
    public static double getNoShieldHealthModifier(UUID playerUUID) {
        return resolve(playerUUID, PARAM_NO_SHIELD_HEALTH, DEFAULT_NO_SHIELD_HEALTH);
    }

    /** 按玩家已装备物品解析机制参数（未装备声明物品时回退默认值） */
    private static double resolve(UUID playerUUID, String paramKey, double fallback) {
        return DefsManager.resolveMechanicValue(ServerLifecycleHooks.getCurrentServer(), playerUUID,
                MECHANIC_SET, paramKey, fallback);
    }

    /**
     * 监听属性计算完毕事件，刷新“是否装备再生护盾机制物品”的缓存
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        boolean hasRecoveryItem = false;
        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (!DisableSystem.isItemDisabled(playerUUID, stack) && Config.isShieldNaturalRecoveryItem(stack.getItem())) {
                hasRecoveryItem = true;
                break;
            }
        }

        if (hasRecoveryItem) {
            PLAYER_HAS_SHIELD_RECOVERY_ITEM.add(playerUUID);
        } else {
            PLAYER_HAS_SHIELD_RECOVERY_ITEM.remove(playerUUID);
        }
    }

    /**
     * 监听玩家退出事件，清理缓存
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_HAS_SHIELD_RECOVERY_ITEM.remove(event.getEntity().getUUID());
    }
}
