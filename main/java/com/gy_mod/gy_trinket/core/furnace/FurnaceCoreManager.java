package com.gy_mod.gy_trinket.core.furnace;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;

/**
 * 炉心融解模块特殊效果管理器。
 * <p>
 * 仅在玩家光点核心中持有炉心融解模块时生效：
 * - 构造体每10刻受到 0.8 点构造体自伤（伤害前后重置无敌时间）
 * - 构造体根据剩余生命值提高攻击速度（血量越低越高，最高 +50% 独立乘区，生命值30%时达到最高）
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class FurnaceCoreManager {

    /** 自伤间隔（tick） */
    private static final int SELF_DAMAGE_INTERVAL = 10;
    /** 每次自伤伤害 */
    private static final float SELF_DAMAGE_AMOUNT = 0.8f;
    /** 低血量攻速最高加成（+50% 独立乘区） */
    private static final double MAX_LOW_HP_BONUS = 0.5;
    /** 生命值达到最高加成的比例（30%） */
    private static final double FULL_BONUS_AT_RATIO = 0.3;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long gameTime = server.overworld().getGameTime();
        boolean selfDamageTick = gameTime % SELF_DAMAGE_INTERVAL == 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!PlayerStoreUtils.hasActiveItem(player, Config::isFurnaceCoreItem)) {
                continue;
            }
            UUID ownerUUID = player.getUUID();
            processType(ownerUUID, DroneConstructTypes.DRONE, selfDamageTick);
            processType(ownerUUID, WingmanConstructTypes.WINGMAN, selfDamageTick);
            processType(ownerUUID, SwarmConstructTypes.SWARM, selfDamageTick);
        }
    }

    private static void processType(UUID ownerUUID, String typeId, boolean selfDamageTick) {
        Map<UUID, Entity> entities = ConstructManager.getInstance().getActiveConstructEntities(ownerUUID, typeId);
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (Entity entity : entities.values()) {
            if (!(entity instanceof AbstractConstructEntity construct) || !construct.isAlive()) {
                continue;
            }

            // 低血量攻速独立乘区：血量越低越高，生命值30%时达到 +50%
            float ratio = construct.getMaxHealth() > 0 ? construct.getHealth() / construct.getMaxHealth() : 1.0f;
            double factor = Math.max(0.0, Math.min(1.0, (1.0 - ratio) / (1.0 - FULL_BONUS_AT_RATIO)));
            double bonus = MAX_LOW_HP_BONUS * factor;
            construct.setLowHpAttackSpeedMultiplier(1.0 + bonus);

            // 每10刻构造体自伤
            if (selfDamageTick) {
                construct.invulnerableTime = 0;
                construct.hurt(ModDamageTypes.getConstructSelfDamageSource(construct.level()), SELF_DAMAGE_AMOUNT);
                construct.invulnerableTime = 0;
            }
        }
    }
}
