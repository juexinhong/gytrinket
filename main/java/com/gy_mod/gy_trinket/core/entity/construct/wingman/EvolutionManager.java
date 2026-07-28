package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.core.level.ModLevelManager;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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
     * 检查玩家光点核心是否拥有进化模块
     */
    private static boolean hasEvolutionModuleInStore(UUID playerUUID) {
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            return false;
        }

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isEvolutionModuleItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
