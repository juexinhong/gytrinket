package com.gy_mod.gy_trinket.core.assault;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    public static void triggerAssault(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!hasAssault(player)) {
            return;
        }

        UUID uuid = player.getUUID();
        AssaultData data = PLAYER_ASSAULT_DATA.computeIfAbsent(uuid, k -> new AssaultData());

        data.stacks++;
        data.remainingTicks = com.gy_mod.gy_trinket.Config.getAssaultDurationTicks();

        double attackSpeedBonus = com.gy_mod.gy_trinket.Config.getAssaultAttackSpeedPerStack() * data.stacks;
        AttributeManager.setDynamicAttribute(uuid, "assault", "attack_speed_independent", attackSpeedBonus);

        float selfDamage = (float) (com.gy_mod.gy_trinket.Config.getAssaultSelfDamagePerStack() * data.stacks);
        if (selfDamage > 0) {
            KnockbackManager.markNoKnockback(uuid);
            player.hurt(ModDamageTypes.getPlayerSelfDamageSource(player.level()), selfDamage);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }

        UUID uuid = player.getUUID();
        AssaultData data = PLAYER_ASSAULT_DATA.get(uuid);
        if (data == null) {
            return;
        }

        data.remainingTicks--;
        if (data.remainingTicks <= 0) {
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
        int remainingTicks;

        AssaultData() {
            this.stacks = 0;
            this.remainingTicks = 0;
        }
    }
}
