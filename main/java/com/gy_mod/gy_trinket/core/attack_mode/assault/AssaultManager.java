package com.gy_mod.gy_trinket.core.attack_mode.assault;

import com.gy_mod.gy_trinket.core.attack_mode.PlayerAttackLockManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 强袭管理器
 * <p>
 * 行为：按住左键维持叠层，松开立即取消。
 * 叠层的维持/取消由 AttackModeManager 统一管理。
 * triggerAssault 仅负责添加叠层、应用攻击速度属性和自伤。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AssaultManager {

    private static final Map<UUID, AssaultData> PLAYER_ASSAULT_DATA = new ConcurrentHashMap<>();

    private static final Set<UUID> PLAYER_HAS_ASSAULT = new java.util.concurrent.CopyOnWriteArraySet<>();

    private AssaultManager() {}

    public static boolean hasAssault(Player player) {
        return PLAYER_HAS_ASSAULT.contains(player.getUUID());
    }

    public static int getAssaultStacks(Player player) {
        AssaultData data = PLAYER_ASSAULT_DATA.get(player.getUUID());
        return data != null ? data.stacks : 0;
    }

    /**
     * 触发强袭：添加叠层、应用攻击速度属性、自伤。
     * 不再管理持续时间，叠层的维持由 AttackModeManager 按住逻辑控制。
     */
    public static void triggerAssault(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!hasAssault(player)) {
            return;
        }

        // 攻击锁定时禁用强袭
        if (PlayerAttackLockManager.isLocked(player)) {
            return;
        }

        UUID uuid = player.getUUID();
        AssaultData data = PLAYER_ASSAULT_DATA.computeIfAbsent(uuid, k -> new AssaultData());

        data.stacks++;

        double attackSpeedBonus = com.gy_mod.gy_trinket.Config.getAssaultAttackSpeedPerStack() * data.stacks;
        AttributeManager.setDynamicAttribute(uuid, "assault", "attack_speed_independent", attackSpeedBonus);

        float selfDamage = (float) (com.gy_mod.gy_trinket.Config.getAssaultSelfDamagePerStack() * data.stacks);
        if (selfDamage > 0) {
            KnockbackManager.markNoKnockback(uuid);
            player.hurt(ModDamageTypes.getPlayerSelfDamageSource(player.level()), selfDamage);
        }
    }

    /**
     * 立即清除强袭叠层和属性。
     * 由 AttackModeManager 在玩家松开左键时调用。
     */
    public static void clearAssault(UUID uuid) {
        AssaultData data = PLAYER_ASSAULT_DATA.get(uuid);
        if (data != null && data.stacks > 0) {
            AttributeManager.removeDynamicAttribute(uuid, "assault", "attack_speed_independent");
            PLAYER_ASSAULT_DATA.remove(uuid);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_ASSAULT_DATA.remove(uuid);
        PLAYER_HAS_ASSAULT.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        AttributeManager.removeDynamicAttribute(uuid, "assault", "attack_speed_independent");
        PLAYER_ASSAULT_DATA.remove(uuid);
    }

    public static void setHasAssault(UUID playerUUID, boolean has) {
        if (has) {
            PLAYER_HAS_ASSAULT.add(playerUUID);
        } else {
            PLAYER_HAS_ASSAULT.remove(playerUUID);
            AttributeManager.removeDynamicAttribute(playerUUID, "assault", "attack_speed_independent");
            PLAYER_ASSAULT_DATA.remove(playerUUID);
        }
    }

    public static void clearAllData() {
        for (UUID uuid : PLAYER_ASSAULT_DATA.keySet()) {
            AttributeManager.removeDynamicAttribute(uuid, "assault", "attack_speed_independent");
        }
        PLAYER_ASSAULT_DATA.clear();
        PLAYER_HAS_ASSAULT.clear();
    }

    private static class AssaultData {
        int stacks;

        AssaultData() {
            this.stacks = 0;
        }
    }
}
