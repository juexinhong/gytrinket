package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * 进化模块管理器
 * <p>
 * 特殊机制：进化
 * <ul>
 *   <li>需要玩家光点核心拥有进化模块物品才能生效</li>
 *   <li>玩家光点等级每级提高僚机0.625%生命值和攻击速度</li>
 * </ul>
 * <p>
 * 实现方式：通过动态属性系统注册 wingman_evolution_health_percent 和
 * wingman_evolution_attack_speed_percent（PERCENT 类型），
 * 监听 {@link PlayerAttributesCalculatedEvent} 及光点等级变化刷新动态值。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class EvolutionManager {

    private static final String NAMESPACE = "wingman_evolution";
    private static final String ATTR_HEALTH_PERCENT = "construct_wingman_evolution_health_percent";
    private static final String ATTR_ATTACK_SPEED_PERCENT = "construct_wingman_evolution_attack_speed_percent";

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            applyEvolutionBonus(player);
        }
    }

    /**
     * 应用进化模块加成：根据光点等级设置僚机生命值和攻击速度百分比。
     * <p>
     * 玩家拥有进化模块物品时，每级光点等级提供 {@link Config#getWingmanEvolutionBonusPerLevel()} 的加成。
     * 无物品时移除动态属性。
     *
     * @param player 服务端玩家
     */
    public static void applyEvolutionBonus(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (!hasEvolutionModuleInStore(playerUUID)) {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_HEALTH_PERCENT);
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, ATTR_ATTACK_SPEED_PERCENT);
            return;
        }

        int modLevel = Math.max(0, ModLevelManager.getModLevel(playerUUID));
        double bonusPerLevel = Config.getWingmanEvolutionBonusPerLevel();
        double bonus = modLevel * bonusPerLevel;

        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, ATTR_HEALTH_PERCENT, bonus);
        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, ATTR_ATTACK_SPEED_PERCENT, bonus);
    }

    /**
     * 检查玩家已装备物品（光点核心存储 + Curios 饰品栏）是否拥有进化模块
     */
    private static boolean hasEvolutionModuleInStore(UUID playerUUID) {
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isEvolutionModuleItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
