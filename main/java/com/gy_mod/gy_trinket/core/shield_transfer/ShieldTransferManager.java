package com.gy_mod.gy_trinket.core.shield_transfer;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.core.TickScheduler;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent;
import com.gy_mod.gy_trinket.core.shield_transfer.event.ShieldTransferRebuiltEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldTransferManager {

    private static final Map<UUID, Set<ShieldTransferData>> PLAYER_TO_TRANSFERS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ENTITY_TO_PLAYER = new ConcurrentHashMap<>();
    private static final Set<UUID> PLAYER_HAS_SHIELD_TRANSFER_ITEM = new HashSet<>();

    private static final String NBT_TRANSFER_LIST = "gy_trinket.shield_transfers";
    private static final String DYNAMIC_ATTR_NAMESPACE = "shield_transfer";

    private ShieldTransferManager() {}

    public static void transferShieldToEntity(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            gytrinket.LOGGER.warn("尝试将护盾转移给无效实体");
            return;
        }

        if (!hasShieldTransferItem(owner.getUUID())) {
            gytrinket.LOGGER.warn("玩家 {} 没有护盾移植物品，无法转移护盾", owner.getUUID());
            return;
        }

        UUID ownerUUID = owner.getUUID();
        UUID targetUUID = target.getUUID();

        if (isEntityProtectedByShield(target)) {
            UUID existingOwner = ENTITY_TO_PLAYER.get(targetUUID);
            if (!existingOwner.equals(ownerUUID)) {
                clearTransferForEntity(targetUUID);
            } else {
                return;
            }
        }

        ShieldTransferData transferData = new ShieldTransferData(ownerUUID, target);
        PLAYER_TO_TRANSFERS.computeIfAbsent(ownerUUID, k -> new HashSet<>()).add(transferData);
        ENTITY_TO_PLAYER.put(targetUUID, ownerUUID);

        updateShieldTransferPenalty(ownerUUID);

        gytrinket.LOGGER.debug("玩家 {} 将护盾转移给实体 {} (UUID: {})", ownerUUID, target.getName().getString(), targetUUID);
    }

    public static void transferShieldToEntity(ServerPlayer owner, LivingEntity target) {
        transferShieldToEntity((Player) owner, target);
    }

    public static void removeProtectionForEntity(UUID playerUUID, UUID entityUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        if (transfers != null) {
            transfers.removeIf(data -> data.getProtectedEntityUUID().equals(entityUUID));
            ENTITY_TO_PLAYER.remove(entityUUID);
            if (transfers.isEmpty()) {
                PLAYER_TO_TRANSFERS.remove(playerUUID);
            }
            updateShieldTransferPenalty(playerUUID);
        }
    }

    public static boolean isEntityProtected(UUID playerUUID, UUID entityUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        if (transfers == null) {
            return false;
        }
        return transfers.stream().anyMatch(data -> data.getProtectedEntityUUID().equals(entityUUID));
    }

    public static void transferShieldToConstructs(ServerPlayer owner) {
        if (!hasShieldTransferItem(owner.getUUID())) {
            return;
        }

        UUID ownerUUID = owner.getUUID();
        Collection<Entity> constructs = getPlayerConstructEntities(owner);

        for (Entity construct : constructs) {
            if (construct instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                transferShieldToEntity(owner, livingEntity);
            }
        }
    }

    private static Collection<Entity> getPlayerConstructEntities(Player player) {
        List<Entity> entities = new ArrayList<>();
        
        for (String constructId : ConstructManager.getInstance().getAllConstructTypeIds()) {
            Map<UUID, Entity> typeEntities = ConstructManager.getInstance().getActiveConstructEntities(player.getUUID(), constructId);
            entities.addAll(typeEntities.values());
        }
        
        return entities.stream()
                .filter(Entity::isAlive)
                .collect(Collectors.toList());
    }

    public static void clearTransferForPlayer(UUID playerUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.remove(playerUUID);
        if (transfers != null) {
            for (ShieldTransferData data : transfers) {
                ENTITY_TO_PLAYER.remove(data.getProtectedEntityUUID());
            }
            updateShieldTransferPenalty(playerUUID);
            gytrinket.LOGGER.debug("清除玩家 {} 的所有护盾转移", playerUUID);
        }
    }

    public static void clearTransferForEntity(UUID entityUUID) {
        UUID ownerUUID = ENTITY_TO_PLAYER.remove(entityUUID);
        if (ownerUUID != null) {
            Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(ownerUUID);
            if (transfers != null) {
                transfers.removeIf(data -> data.getProtectedEntityUUID().equals(entityUUID));
                if (transfers.isEmpty()) {
                    PLAYER_TO_TRANSFERS.remove(ownerUUID);
                }
            }
            updateShieldTransferPenalty(ownerUUID);
            gytrinket.LOGGER.debug("清除实体 UUID {} 的护盾转移", entityUUID);
        }
    }

    public static void removeProtectedEntity(UUID playerUUID, LivingEntity entity) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        if (transfers != null) {
            transfers.removeIf(data -> data.getProtectedEntityUUID().equals(entity.getUUID()));
            ENTITY_TO_PLAYER.remove(entity.getUUID());
            if (transfers.isEmpty()) {
                PLAYER_TO_TRANSFERS.remove(playerUUID);
            }
            updateShieldTransferPenalty(playerUUID);
        }
    }

    public static boolean hasTransferredShield(UUID playerUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        return transfers != null && !transfers.isEmpty();
    }

    public static boolean isEntityProtectedByShield(LivingEntity entity) {
        return ENTITY_TO_PLAYER.containsKey(entity.getUUID());
    }

    public static UUID getShieldOwnerUUID(LivingEntity entity) {
        return ENTITY_TO_PLAYER.get(entity.getUUID());
    }

    public static UUID getShieldOwnerUUID(UUID entityUUID) {
        return ENTITY_TO_PLAYER.get(entityUUID);
    }

    public static Set<ShieldTransferData> getTransferData(UUID playerUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        return transfers != null ? new HashSet<>(transfers) : new HashSet<>();
    }

    public static List<LivingEntity> getProtectedEntities(UUID playerUUID, Level level) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        if (transfers == null) {
            return Collections.emptyList();
        }

        return transfers.stream()
                .map(data -> data.getProtectedEntity(level))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static LivingEntity getProtectedEntity(UUID playerUUID, Level level) {
        List<LivingEntity> entities = getProtectedEntities(playerUUID, level);
        return entities.isEmpty() ? null : entities.get(0);
    }

    public static boolean isShieldActiveForEntity(LivingEntity entity) {
        UUID entityUUID = entity.getUUID();
        UUID ownerUUID = ENTITY_TO_PLAYER.get(entityUUID);
        if (ownerUUID == null) {
            return false;
        }

        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(ownerUUID);
        if (transfers == null) {
            return false;
        }

        return transfers.stream()
                .anyMatch(data -> data.getProtectedEntityUUID().equals(entityUUID) &&
                        data.isEntityValid(entity.level()) &&
                        com.gy_mod.gy_trinket.core.shield.ShieldManager.getCurrentShield(ownerUUID) > 0);
    }

    public static boolean hasShieldTransferItem(UUID playerUUID) {
        return PLAYER_HAS_SHIELD_TRANSFER_ITEM.contains(playerUUID);
    }

    public static boolean isShieldTransferEnabled(UUID playerUUID) {
        return PLAYER_HAS_SHIELD_TRANSFER_ITEM.contains(playerUUID);
    }

    public static boolean shouldProtectPlayer(Player player) {
        return !hasShieldTransferItem(player.getUUID());
    }

    public static void updateTransferForPlayer(UUID playerUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        if (transfers != null) {
            transfers.forEach(ShieldTransferData::updateTransferTime);
        }
    }

    public static void updateProtectedConstructs(ServerPlayer player) {
        if (!hasShieldTransferItem(player.getUUID())) {
            return;
        }

        UUID playerUUID = player.getUUID();
        Collection<Entity> constructs = getPlayerConstructEntities(player);

        Set<UUID> currentProtectedUUIDs = new HashSet<>();
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        if (transfers != null) {
            for (ShieldTransferData data : transfers) {
                currentProtectedUUIDs.add(data.getProtectedEntityUUID());
            }
        }

        for (Entity construct : constructs) {
            if (construct instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                UUID entityUUID = construct.getUUID();
                if (!currentProtectedUUIDs.contains(entityUUID)) {
                    transferShieldToEntity(player, livingEntity);
                }
            }
        }

        if (transfers != null) {
            Iterator<ShieldTransferData> iterator = transfers.iterator();
            while (iterator.hasNext()) {
                ShieldTransferData data = iterator.next();
                UUID entityUUID = data.getProtectedEntityUUID();
                boolean stillExists = constructs.stream()
                        .anyMatch(e -> e.getUUID().equals(entityUUID));
                if (!stillExists) {
                    iterator.remove();
                    ENTITY_TO_PLAYER.remove(entityUUID);
                }
            }
            if (transfers.isEmpty()) {
                PLAYER_TO_TRANSFERS.remove(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            return;
        }

        UUID entityUUID = event.getEntity().getUUID();
        if (ENTITY_TO_PLAYER.containsKey(entityUUID)) {
            clearTransferForEntity(entityUUID);
            gytrinket.LOGGER.debug("被保护的实体死亡，清除护盾转移: UUID {}", entityUUID);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel().getServer() != null)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (hasShieldTransferItem(player.getUUID())) {
                transferShieldToConstructs(player);
            }
            return;
        }

        UUID entityUUID = event.getEntity().getUUID();
        UUID ownerUUID = ENTITY_TO_PLAYER.get(entityUUID);
        if (ownerUUID != null) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            if (!entity.isAlive()) {
                clearTransferForEntity(entityUUID);
                gytrinket.LOGGER.debug("实体重新加入世界但已死亡，清除护盾转移: UUID {}", entityUUID);
            }
        }
    }

    @SubscribeEvent
    public static void onConstructListChanged(PlayerConstructListChangedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        
        if (!hasShieldTransferItem(playerUUID)) {
            return;
        }

        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
        if (player == null) {
            return;
        }

        Entity construct = event.getConstruct();
        if (!(construct instanceof LivingEntity livingEntity)) {
            return;
        }

        switch (event.getChangeType()) {
            case ADDED:
                if (livingEntity.isAlive()) {
                    transferShieldToEntity(player, livingEntity);
                    gytrinket.LOGGER.debug("构造体加入，自动添加护盾保护: 玩家 {}, 实体 UUID {}", playerUUID, construct.getUUID());
                }
                break;
            case REMOVED:
                removeProtectedEntity(playerUUID, livingEntity);
                gytrinket.LOGGER.debug("构造体移除，自动清除护盾保护: 玩家 {}, 实体 UUID {}", playerUUID, construct.getUUID());
                break;
            case CLEARED:
                clearTransferForPlayer(playerUUID);
                gytrinket.LOGGER.debug("构造体列表清空，清除所有护盾保护: 玩家 {}", playerUUID);
                break;
        }
    }

    @SubscribeEvent
    public static void onShieldTransferRebuilt(ShieldTransferRebuiltEvent event) {
        updateShieldTransferPenalty(event.getPlayerUUID());
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            PLAYER_HAS_SHIELD_TRANSFER_ITEM.remove(playerUUID);
            clearTransferForPlayer(playerUUID);
            return;
        }

        boolean hasShieldTransferItem = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!DisableSystem.isItemDisabled(playerUUID, stack) && Config.isShieldTransferItem(stack.getItem())) {
                    hasShieldTransferItem = true;
                    break;
                }
            }
        }

        boolean hadItem = PLAYER_HAS_SHIELD_TRANSFER_ITEM.contains(playerUUID);

        if (hasShieldTransferItem) {
            PLAYER_HAS_SHIELD_TRANSFER_ITEM.add(playerUUID);
            if (!hadItem) {
                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    transferShieldToConstructs(player);
                }
            }
        } else {
            PLAYER_HAS_SHIELD_TRANSFER_ITEM.remove(playerUUID);
            clearTransferForPlayer(playerUUID);
        }

        updateShieldTransferPenalty(playerUUID);
    }

    public static void saveTransfersToNBT(CompoundTag playerData) {
        ListTag list = new ListTag();
        int count = 0;
        for (Map.Entry<UUID, Set<ShieldTransferData>> entry : PLAYER_TO_TRANSFERS.entrySet()) {
            for (ShieldTransferData data : entry.getValue()) {
                CompoundTag tag = data.save();
                tag.putUUID("playerUUID", entry.getKey());
                list.add(tag);
                count++;
            }
        }
        playerData.put(NBT_TRANSFER_LIST, list);
        gytrinket.LOGGER.info("[ShieldTransfer] 保存护盾移植数据: {} 条记录", count);
        gytrinket.LOGGER.debug("[ShieldTransfer] PLAYER_TO_TRANSFERS 大小: {}", PLAYER_TO_TRANSFERS.size());
    }

    public static void loadTransfersFromNBT(CompoundTag playerData) {
        if (!playerData.contains(NBT_TRANSFER_LIST)) {
            gytrinket.LOGGER.info("[ShieldTransfer] 加载护盾移植数据: NBT中不存在转移列表");
            return;
        }

        ListTag list = playerData.getList(NBT_TRANSFER_LIST, 10);
        int count = 0;
        gytrinket.LOGGER.info("[ShieldTransfer] 加载护盾移植数据: NBT中存在 {} 条记录", list.size());
        
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            UUID playerUUID = tag.getUUID("playerUUID");
            ShieldTransferData data = ShieldTransferData.load(tag);

            if (data != null) {
                PLAYER_TO_TRANSFERS.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(data);
                ENTITY_TO_PLAYER.put(data.getProtectedEntityUUID(), playerUUID);
                count++;
                gytrinket.LOGGER.debug("[ShieldTransfer] 加载记录: 玩家={}, 保护实体UUID={}", playerUUID, data.getProtectedEntityUUID());
            }
        }
        gytrinket.LOGGER.info("[ShieldTransfer] 成功加载 {} 条护盾移植记录", count);
        gytrinket.LOGGER.debug("[ShieldTransfer] 加载后 PLAYER_TO_TRANSFERS 大小: {}", PLAYER_TO_TRANSFERS.size());
    }

    public static void saveTransfersToNBT(Player player, CompoundTag playerData) {
        UUID playerUUID = player.getUUID();
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        
        ListTag list = new ListTag();
        int count = 0;
        
        if (transfers != null && !transfers.isEmpty()) {
            for (ShieldTransferData data : transfers) {
                CompoundTag tag = data.save();
                list.add(tag);
                count++;
            }
        }
        
        playerData.put(NBT_TRANSFER_LIST, list);
        gytrinket.LOGGER.info("[ShieldTransfer] 保存玩家 {} 的护盾移植数据: {} 条记录", playerUUID, count);
    }

    public static void loadTransfersFromNBT(Player player, CompoundTag playerData) {
        UUID playerUUID = player.getUUID();
        
        if (!playerData.contains(NBT_TRANSFER_LIST)) {
            gytrinket.LOGGER.info("[ShieldTransfer] 加载玩家 {} 的护盾移植数据: NBT中不存在转移列表", playerUUID);
            return;
        }

        ListTag list = playerData.getList(NBT_TRANSFER_LIST, 10);
        int count = 0;
        gytrinket.LOGGER.info("[ShieldTransfer] 加载玩家 {} 的护盾移植数据: NBT中存在 {} 条记录", playerUUID, list.size());
        
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ShieldTransferData data = ShieldTransferData.load(tag);

            if (data != null) {
                PLAYER_TO_TRANSFERS.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(data);
                ENTITY_TO_PLAYER.put(data.getProtectedEntityUUID(), playerUUID);
                count++;
                gytrinket.LOGGER.debug("[ShieldTransfer] 加载记录: 保护实体UUID={}", data.getProtectedEntityUUID());
            }
        }
        gytrinket.LOGGER.info("[ShieldTransfer] 成功加载玩家 {} 的 {} 条护盾移植记录", playerUUID, count);
    }

    public static void clearAllTransfers() {
        PLAYER_TO_TRANSFERS.clear();
        ENTITY_TO_PLAYER.clear();
    }

    public static void updateShieldTransferPenalty(UUID playerUUID) {
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(playerUUID);
        int entityCount = (transfers != null) ? transfers.size() : 0;

        if (entityCount == 0 || !hasShieldTransferItem(playerUUID)) {
            AttributeManager.removeDynamicAttribute(playerUUID, DYNAMIC_ATTR_NAMESPACE, "shield_effect_independent");
            AttributeManager.removeDynamicAttribute(playerUUID, DYNAMIC_ATTR_NAMESPACE, "shield_effect_radius");
            return;
        }

        double penaltyPerEntity = Config.SHIELD_TRANSFER_EFFECT_PENALTY_PER_ENTITY.get();
        double multiplier = 1.0;
        for (int i = 0; i < entityCount; i++) {
            multiplier *= (1.0 - penaltyPerEntity);
        }
        double penalty = multiplier - 1.0;

        AttributeManager.setDynamicAttribute(playerUUID, DYNAMIC_ATTR_NAMESPACE, "shield_effect_independent", penalty);
        AttributeManager.setDynamicAttribute(playerUUID, DYNAMIC_ATTR_NAMESPACE, "shield_effect_radius", penalty);
    }

    static {
        TickScheduler.register("shield_transfer_cleanup", 100, tick -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }

            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, Set<ShieldTransferData>> entry : PLAYER_TO_TRANSFERS.entrySet()) {
                UUID playerUUID = entry.getKey();
                Set<ShieldTransferData> transfers = entry.getValue();

                ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                if (player == null) {
                    toRemove.add(playerUUID);
                    continue;
                }

                Iterator<ShieldTransferData> iterator = transfers.iterator();
                while (iterator.hasNext()) {
                    ShieldTransferData data = iterator.next();
                    LivingEntity protectedEntity = data.getProtectedEntity(player.level());
                    if (protectedEntity == null || !protectedEntity.isAlive()) {
                        iterator.remove();
                        ENTITY_TO_PLAYER.remove(data.getProtectedEntityUUID());
                    }
                }

                if (transfers.isEmpty()) {
                    toRemove.add(playerUUID);
                }
            }

            for (UUID uuid : toRemove) {
                PLAYER_TO_TRANSFERS.remove(uuid);
                gytrinket.LOGGER.debug("清理无效的护盾转移: 玩家 {}", uuid);
            }
        });
    }
}
