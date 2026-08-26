package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.TickScheduler;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;

/**
 * 纳米再生模块管理器
 * <p>
 * 特殊机制：纳米再生
 * <ul>
 *   <li>需要玩家光点核心拥有纳米再生模块物品才能生效</li>
 *   <li>为玩家的僚机提供每秒恢复最大生命值2%的效果</li>
 * </ul>
 * <p>
 * 实现方式：每20 tick（1秒）检查所有在线玩家，
 * 若光点核心含纳米再生模块，则为该玩家的所有僚机恢复生命值。
 */
public class NanoRegenManager {

    private static boolean registered = false;

    private NanoRegenManager() {}

    /**
     * 注册每秒 tick 调度器
     */
    public static void init() {
        if (registered) return;
        registered = true;
        TickScheduler.register("nano_regen", 20, NanoRegenManager::tick);
    }

    /**
     * 每秒执行：检查所有在线玩家的纳米再生模块，为僚机恢复生命值
     */
    private static void tick(long currentTick) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive()) continue;

            UUID playerUUID = player.getUUID();
            if (!hasNanoRegenModule(playerUUID)) continue;

            double regenPercent = Config.getWingmanNanoRegenPercent();

            Map<UUID, Entity> wingmanEntities = ConstructManager.getInstance()
                    .getActiveConstructEntities(playerUUID, WingmanConstructTypes.WINGMAN);

            for (Entity entity : wingmanEntities.values()) {
                if (entity instanceof WingmanConstructEntity wingman && wingman.isAlive()) {
                    float maxHealth = wingman.getMaxHealth();
                    float healAmount = (float) (maxHealth * regenPercent);
                    if (healAmount > 0 && wingman.getHealth() < maxHealth) {
                        wingman.heal(healAmount);
                    }
                }
            }
        }
    }

    /**
     * 检查玩家已装备物品（光点核心存储 + Curios 饰品栏）是否拥有纳米再生模块
     */
    public static boolean hasNanoRegenModule(UUID playerUUID) {
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)) {
                if (Config.isNanoRegenModuleItem(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
