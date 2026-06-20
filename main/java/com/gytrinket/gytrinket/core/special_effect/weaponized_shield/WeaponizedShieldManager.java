package com.gytrinket.gytrinket.core.special_effect.weaponized_shield;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.vulnerability.VulnerabilityApplyEvent;
import com.gytrinket.gytrinket.core.vulnerability.VulnerabilityManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class WeaponizedShieldManager {

    private static final Set<UUID> PLAYER_HAS_WEAPONIZED_SHIELD = new java.util.concurrent.CopyOnWriteArraySet<>();
    
    private static final Map<UUID, Set<UUID>> PLAYER_VULNERABILITY_TARGETS = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final Map<UUID, Integer> TICK_COUNTER = new java.util.concurrent.ConcurrentHashMap<>();
    
    private static final int CHECK_INTERVAL = 5;
    private static final String VULNERABILITY_NAME = "weaponized_shield";

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {

        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        UUID playerUUID = player.getUUID();

        if (!PLAYER_HAS_WEAPONIZED_SHIELD.contains(playerUUID)) {
            clearAllVulnerabilities(playerUUID);
            return;
        }

        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield <= 0) {
            clearAllVulnerabilities(playerUUID);
            return;
        }

        int tickCounter = TICK_COUNTER.getOrDefault(playerUUID, 0);
        tickCounter++;
        TICK_COUNTER.put(playerUUID, tickCounter);

        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        TICK_COUNTER.put(playerUUID, 0);

        processWeaponizedShield(player);
    }

    private static void processWeaponizedShield(Player player) {
        UUID playerUUID = player.getUUID();

        List<LivingEntity> effectCenters;
        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            effectCenters = ShieldTransferManager.getProtectedEntities(playerUUID, player.level());
        } else {
            effectCenters = List.of(player);
        }

        double shieldEffectRadius = AttributeManager.getGroupAttribute(playerUUID, "shield_effect_radius");
        double baseRadius = Config.WEAPONIZED_SHIELD_RADIUS.get();
        double radius = baseRadius * shieldEffectRadius;

        double shieldEffect = AttributeManager.getGroupAttribute(playerUUID, "shield_effect");
        float baseVulnerability = Config.WEAPONIZED_SHIELD_VULNERABILITY.get().floatValue();
        float vulnerabilityValue = (float)(baseVulnerability * shieldEffect);

        Set<UUID> currentTargets = new HashSet<>();

        for (LivingEntity center : effectCenters) {
            if (center == null || !center.isAlive()) {
                continue;
            }

            AABB boundingBox = center.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, boundingBox,
                entity -> isValidTarget(entity, player)
            );

            for (LivingEntity target : entities) {
                UUID targetUUID = target.getUUID();
                currentTargets.add(targetUUID);

                applyVulnerability(target, vulnerabilityValue);
            }
        }

        Set<UUID> previousTargets = PLAYER_VULNERABILITY_TARGETS.getOrDefault(playerUUID, Collections.emptySet());

        for (UUID targetUUID : previousTargets) {
            if (!currentTargets.contains(targetUUID)) {
                removeVulnerabilityForTarget(playerUUID, targetUUID);
            }
        }

        PLAYER_VULNERABILITY_TARGETS.put(playerUUID, currentTargets);
    }

    private static boolean isValidTarget(LivingEntity entity, Player player) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) {
            return false;
        }

        return HostileTargetManager.shouldAttackPlayer(entity, player);
    }

    private static void applyVulnerability(LivingEntity target, float vulnerabilityValue) {
        VulnerabilityApplyEvent event = new VulnerabilityApplyEvent(
            VULNERABILITY_NAME,
            vulnerabilityValue,
            target,
            false
        );
        NeoForge.EVENT_BUS.post(event);
    }

    private static void removeVulnerabilityForTarget(UUID playerUUID, UUID targetUUID) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        server.getPlayerList().getPlayers().forEach(player -> {
            if (player.getUUID().equals(targetUUID)) {
                VulnerabilityManager.removeVulnerability(player, VULNERABILITY_NAME);
            }
        });

        for (var level : server.getAllLevels()) {
            level.getEntitiesOfClass(LivingEntity.class, new AABB(0, 0, 0, 0, 0, 0).inflate(1000),
                entity -> entity.getUUID().equals(targetUUID)
            ).forEach(entity -> {
                VulnerabilityManager.removeVulnerability(entity, VULNERABILITY_NAME);
            });
        }
    }

    private static void clearAllVulnerabilities(UUID playerUUID) {
        Set<UUID> targets = PLAYER_VULNERABILITY_TARGETS.remove(playerUUID);
        if (targets != null) {
            for (UUID targetUUID : targets) {
                removeVulnerabilityForTarget(playerUUID, targetUUID);
            }
        }
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            PLAYER_HAS_WEAPONIZED_SHIELD.remove(playerUUID);
            clearAllVulnerabilities(playerUUID);
            return;
        }

        boolean hasWeaponizedShield = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(playerUUID, stack) && Config.isWeaponizedShieldItem(stack.getItem())) {
                    hasWeaponizedShield = true;
                    break;
                }
            }
        }

        if (hasWeaponizedShield) {
            PLAYER_HAS_WEAPONIZED_SHIELD.add(playerUUID);
        } else {
            PLAYER_HAS_WEAPONIZED_SHIELD.remove(playerUUID);
            clearAllVulnerabilities(playerUUID);
        }
    }

    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_HAS_WEAPONIZED_SHIELD.remove(playerUUID);
        clearAllVulnerabilities(playerUUID);
        TICK_COUNTER.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_HAS_WEAPONIZED_SHIELD.clear();
        PLAYER_VULNERABILITY_TARGETS.forEach((playerUUID, targets) -> {
            for (UUID targetUUID : targets) {
                removeVulnerabilityForTarget(playerUUID, targetUUID);
            }
        });
        PLAYER_VULNERABILITY_TARGETS.clear();
        TICK_COUNTER.clear();
    }
}