package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 母舰机制管理器
 * <p>
 * 特殊机制：母舰
 * <ul>
 *   <li>需要玩家光点核心拥有母舰机身物品（蜂群构建物品）生效</li>
 *   <li>玩家光点等级每有4级，增加1蜂群构造体数量上限</li>
 *   <li>蜂群数量极限值：当最终数量超过极限值时，不再增加数量，而是提升每只蜂群的基础属性和易伤值</li>
 * </ul>
 * <p>
 * 实现方式：通过动态属性系统注册 swarm_count_mothership（BASE 类型），
 * 监听 {@link PlayerAttributesCalculatedEvent} 及光点等级变化（经 PlayerLevelDebouncer）刷新动态值。
 * <p>
 * 数值：动态值 = modLevel / 4.0，作为底数直接加到蜂群数量上限。
 * 最终在 getEffectiveMaxCount 中向下取整，小数部分累积到整数时才生效。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class MothershipManager {

    private static final String NAMESPACE = "mothership";
    private static final String ATTR_SWARM_COUNT = "swarm_count_mothership";

    /** 溢出倍率存储：玩家UUID -> 溢出倍率（当蜂群数量超过极限值时，属性和易伤值的放大倍率） */
    private static final Map<UUID, Double> OVERFLOW_MULTIPLIERS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            applyMothershipBonus(player);
        }
    }

    /**
     * 应用母舰机制：根据玩家光点等级计算蜂群数量上限加成。
     * <p>
     * 玩家拥有母舰机身物品时，每4级光点等级+1蜂群上限（通过 BASE 动态属性实现）。
     * 无物品或等级不足时移除动态属性。
     *
     * @param player 服务端玩家
     */
    public static void applyMothershipBonus(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (!hasMothershipItem(playerUUID)) {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_SWARM_COUNT);
            return;
        }

        int modLevel = Math.max(0, ModLevelManager.getModLevel(playerUUID));
        double bonusCount = modLevel / 4.0; // 每4级+1，允许小数部分平滑过渡

        if (bonusCount <= 0.0) {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_SWARM_COUNT);
            return;
        }

        // BASE 类型：动态值即为加成的数量上限，直接加到 baseBonus
        // 最终在 getEffectiveMaxCount 中向下取整，小数部分累积到整数时才生效
        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, ATTR_SWARM_COUNT, bonusCount);
    }

    /**
     * 检查玩家光点核心是否拥有母舰机身物品（蜂群构建物品）
     */
    private static boolean hasMothershipItem(UUID playerUUID) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isSwarmModuleItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取蜂群溢出倍率（当蜂群数量超过极限值时，属性和易伤值的放大倍率）。
     * <p>
     * 由 {@link com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeApplier#getEffectiveMaxCount}
     * 在计算蜂群最终数量时设置。
     *
     * @param playerUUID 玩家 UUID
     * @return 溢出倍率，1.0 表示无溢出
     */
    public static double getOverflowMultiplier(UUID playerUUID) {
        return OVERFLOW_MULTIPLIERS.getOrDefault(playerUUID, 1.0);
    }

    /**
     * 设置蜂群溢出倍率。倍率 <= 1.0 时移除存储。
     *
     * @param playerUUID 玩家 UUID
     * @param multiplier 溢出倍率
     */
    public static void setOverflowMultiplier(UUID playerUUID, double multiplier) {
        if (multiplier <= 1.0) {
            OVERFLOW_MULTIPLIERS.remove(playerUUID);
        } else {
            OVERFLOW_MULTIPLIERS.put(playerUUID, multiplier);
        }
    }
}
