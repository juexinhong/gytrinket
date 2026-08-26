package com.gy_mod.gy_trinket.core.shield_transfer;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.core.TickScheduler;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent;
import com.gy_mod.gy_trinket.core.shield_transfer.event.ShieldTransferRebuiltEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldTransferManager {

    private static final Map<UUID, Set<ShieldTransferData>> PLAYER_TO_TRANSFERS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ENTITY_TO_PLAYER = new ConcurrentHashMap<>();
    private static final Set<UUID> PLAYER_HAS_SHIELD_TRANSFER_ITEM = new HashSet<>();
    /**
     * 神龛复活补标签队列：女仆主人UUID -> 标签owner。
     * 拦截神龛右键时若胶片内女仆NBT携带护盾移植标签，登记此队列；
     * 复活女仆加入世界时凭此队列补打标签（女仆模组的 filmToMaid 不会还原持久化数据）。
     */
    private static final Map<UUID, UUID> PENDING_SHRINE_RE_TAG = new ConcurrentHashMap<>();

    /** 女仆模组的胶片/魂符等存储物品数据键与神龛标识（仅按注册ID识别，不引用女仆模组类） */
    private static final String MAID_MOD_ID = "touhou_little_maid";
    private static final String MAID_INFO_COMPONENT = "maid_info";
    private static final String MAID_SHRINE_BLOCK = "shrine";
    private static final String MAID_SHRINE_STORAGE_KEY = "StorageItem";

    private static final String NBT_TRANSFER_LIST = "gytrinket.shield_transfers";
    private static final String DYNAMIC_ATTR_NAMESPACE = "shield_transfer";

    /** 实体持久化NBT上的护盾移植标签键（随实体保存/加载/复制而保留，作为保护身份的依据） */
    private static final String ENTITY_TAG_KEY = "gytrinket.shield_transfer";
    private static final String ENTITY_TAG_OWNER = "owner";

    private ShieldTransferManager() {}

    // ===== 实体标签读写 =====

    /**
     * 读取女仆模组存储物品（胶片/魂符等）内 maid_info NBT 中的护盾移植标签owner；
     * 无标签返回 null。仅按NBT键读取，不引用女仆模组类。
     */
    private static UUID readMaidInfoTagOwner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag itemTag = stack.getTag();
        if (itemTag == null || !itemTag.contains(MAID_INFO_COMPONENT, 10)) {
            return null;
        }
        CompoundTag maidNbt = itemTag.getCompound(MAID_INFO_COMPONENT);
        if (!maidNbt.contains("ForgeData", 10)) {
            return null;
        }
        CompoundTag forgeData = maidNbt.getCompound("ForgeData");
        if (!forgeData.contains(ENTITY_TAG_KEY, 10)) {
            return null;
        }
        CompoundTag tag = forgeData.getCompound(ENTITY_TAG_KEY);
        if (tag.contains(ENTITY_TAG_OWNER)) {
            return tag.getUUID(ENTITY_TAG_OWNER);
        }
        return null;
    }

    /**
     * 拦截女仆模组神龛右键（不引用女仆模组类，仅按方块注册ID识别）。
     * 若神龛内的胶片携带护盾移植标签，登记补标签队列，
     * 待神龛复活的女仆加入世界时补打标签（filmToMaid 不会还原持久化数据）。
     */
    @SubscribeEvent
    public static void onShrineRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
        if (!MAID_MOD_ID.equals(blockId.getNamespace()) || !MAID_SHRINE_BLOCK.equals(blockId.getPath())) {
            return;
        }
        // 从神龛方块实体持久化数据读取胶片（TileEntityShrine 将 StorageItem 存在 getPersistentData()）
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be == null) {
            return;
        }
        ItemStack film = readShrineFilm(be);
        if (film == null || film.isEmpty()) {
            return;
        }
        UUID tagOwner = readMaidInfoTagOwner(film);
        if (tagOwner == null) {
            return;
        }
        // 以复活后女仆的主人（胶片内 Owner）为键登记；读不到则用右键玩家
        UUID maidOwner = readMaidInfoOwner(film);
        UUID pendingKey = maidOwner != null ? maidOwner : player.getUUID();
        PENDING_SHRINE_RE_TAG.put(pendingKey, tagOwner);
        gytrinket.LOGGER.debug("[ShieldTransfer] 神龛胶片含护盾标签: 女仆主人={} 标签owner={} → 登记补标签",
                pendingKey, tagOwner);
    }

    /**
     * 从神龛方块实体的持久化数据中读取胶片（ItemStackHandler 序列化格式，取 slot 0）。
     */
    private static ItemStack readShrineFilm(BlockEntity be) {
        CompoundTag handlerTag = be.getPersistentData().getCompound(MAID_SHRINE_STORAGE_KEY);
        if (handlerTag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ListTag items = handlerTag.getList("Items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            if (itemTag.getByte("Slot") == 0) {
                return ItemStack.of(itemTag);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 读取胶片 maid_info 中女仆的主人（TamableAnimal 的 Owner UUID）。
     */
    private static UUID readMaidInfoOwner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag itemTag = stack.getTag();
        if (itemTag == null || !itemTag.contains(MAID_INFO_COMPONENT, 10)) {
            return null;
        }
        CompoundTag maidNbt = itemTag.getCompound(MAID_INFO_COMPONENT);
        if (maidNbt.contains("Owner", 11)) {
            return maidNbt.getUUID("Owner");
        }
        return null;
    }

    /**
     * 为实体打上护盾移植标签（记录所属玩家）。
     * 标签写入实体持久化NBT，实体被复制/重建（UUID变化）时仍随NBT保留，
     * 从而在实体加入世界时重新登记保护，避免因UUID变化丢失保护。
     */
    private static void setEntityTransferTag(LivingEntity entity, UUID ownerUUID) {
        if (entity == null) {
            return;
        }
        CompoundTag tag = entity.getPersistentData().getCompound(ENTITY_TAG_KEY);
        tag.putUUID(ENTITY_TAG_OWNER, ownerUUID);
        entity.getPersistentData().put(ENTITY_TAG_KEY, tag);
    }

    private static void removeEntityTransferTag(LivingEntity entity) {
        if (entity != null) {
            entity.getPersistentData().remove(ENTITY_TAG_KEY);
        }
    }

    /**
     * 读取实体上的护盾移植标签所属玩家；无标签返回 null。
     */
    public static UUID getEntityTransferTagOwner(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        CompoundTag tag = entity.getPersistentData().getCompound(ENTITY_TAG_KEY);
        if (tag.contains(ENTITY_TAG_OWNER)) {
            return tag.getUUID(ENTITY_TAG_OWNER);
        }
        return null;
    }

    /**
     * 在所有维度中按UUID查找实体（跨维度保护判定用）。
     */
    private static LivingEntity findEntityAnywhere(UUID entityUUID) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (serverLevel.getEntity(entityUUID) instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    /**
     * 判定构造体是否应被护盾移植保护。
     * 排除基础类构造体；蜂群按内部等阶判定（仅标准/高阶受保护）。
     */
    private static boolean shouldProtectConstruct(LivingEntity entity) {
        if (entity instanceof SwarmConstructEntity swarm) {
            // 蜂群：仅内部等阶为标准/高阶时受保护
            return swarm.getTier() >= SwarmConstructTypes.TIER_STANDARD;
        }
        // 其他构造体（无人机/僚机等均为标准阶或以上）：受保护
        return true;
    }

    /**
     * 登记一条护盾移植记录（写入内存映射 + 实体标签）。
     */
    private static void registerTransfer(UUID ownerUUID, LivingEntity target) {
        ShieldTransferData transferData = new ShieldTransferData(ownerUUID, target);
        PLAYER_TO_TRANSFERS.computeIfAbsent(ownerUUID, k -> new HashSet<>()).add(transferData);
        ENTITY_TO_PLAYER.put(target.getUUID(), ownerUUID);
        setEntityTransferTag(target, ownerUUID);

        updateShieldTransferPenalty(ownerUUID);
    }

    public static void transferShieldToEntity(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            gytrinket.LOGGER.warn("尝试将护盾转移给无效实体");
            return;
        }

        if (!shouldProtectConstruct(target)) {
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
            if (existingOwner != null && existingOwner.equals(ownerUUID)) {
                return;
            }
            // 被其他玩家保护，或仅残留标签（如实体重建后标签未登记）
            clearTransferForEntity(targetUUID);
        }

        registerTransfer(ownerUUID, target);

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
        // 玩家主动用护盾接收器取消保护：同时移除实体标签，使取消永久生效
        removeEntityTransferTag(findEntityAnywhere(entityUUID));
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
                // 清空：同时移除实体标签（实体不在已加载区块时无法找到则保留，待其回归时凭标签恢复）
                removeEntityTransferTag(findEntityAnywhere(data.getProtectedEntityUUID()));
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
        // 实体标签优先（实体复制/重建后标签随NBT保留），内存映射兜底
        if (getEntityTransferTagOwner(entity) != null) {
            return true;
        }
        return ENTITY_TO_PLAYER.containsKey(entity.getUUID());
    }

    public static UUID getShieldOwnerUUID(LivingEntity entity) {
        UUID tagOwner = getEntityTransferTagOwner(entity);
        if (tagOwner != null) {
            return tagOwner;
        }
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

    public static int[] getProtectedEntityIds(UUID playerUUID, ServerLevel level) {
        List<LivingEntity> entities = getProtectedEntities(playerUUID, level);
        int[] ids = new int[entities.size()];
        for (int i = 0; i < entities.size(); i++) {
            ids[i] = entities.get(i).getId();
        }
        return ids;
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

        LivingEntity entity = event.getEntity();
        UUID entityUUID = entity.getUUID();
        if (ENTITY_TO_PLAYER.containsKey(entityUUID) || getEntityTransferTagOwner(entity) != null) {
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

        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        UUID entityUUID = livingEntity.getUUID();
        UUID ownerUUID = ENTITY_TO_PLAYER.get(entityUUID);
        UUID tagOwner = getEntityTransferTagOwner(livingEntity);
        String typeId = ForgeRegistries.ENTITY_TYPES.getKey(livingEntity.getType()).toString();

        if (ownerUUID != null) {
            if (!livingEntity.isAlive()) {
                clearTransferForEntity(entityUUID);
                gytrinket.LOGGER.debug("实体重新加入世界但已死亡，清除护盾转移: UUID {}", entityUUID);
            }
            return;
        }

        if (tagOwner != null) {
            if (livingEntity.isAlive()) {
                // 实体携带护盾移植标签但未登记：
                // 实体被复制/重建（UUID变化）或跨维度加载后，凭标签恢复保护
                replaceStaleTransfer(tagOwner, livingEntity);
            }
            return;
        }

        // 无记录无标签：女仆系实体检查神龛复活补标签
        if (typeId.startsWith("touhou_little_maid")) {
            if (!PENDING_SHRINE_RE_TAG.isEmpty() && livingEntity.isAlive()) {
                UUID maidOwner = livingEntity instanceof TamableAnimal tamable ? tamable.getOwnerUUID() : null;
                if (maidOwner != null) {
                    UUID pendingTagOwner = PENDING_SHRINE_RE_TAG.remove(maidOwner);
                    if (pendingTagOwner != null) {
                        // 神龛复活：女仆模组 filmToMaid 未还原持久化数据，此处补打标签并恢复保护
                        gytrinket.LOGGER.debug("[ShieldTransfer] 神龛复活女仆补标签: 实体UUID={} 标签owner={}",
                                entityUUID, pendingTagOwner);
                        registerTransfer(pendingTagOwner, livingEntity);
                    }
                }
            }
        }
    }

    /**
     * 用携带标签的实体重新登记护盾移植。
     * 若玩家名下存在无法解析的旧记录（实体重建导致UUID变化），替换旧记录，避免重复计数与重复惩罚。
     */
    private static void replaceStaleTransfer(UUID ownerUUID, LivingEntity entity) {
        UUID newUUID = entity.getUUID();
        Set<ShieldTransferData> transfers = PLAYER_TO_TRANSFERS.get(ownerUUID);

        if (transfers != null) {
            if (transfers.stream().anyMatch(data -> data.getProtectedEntityUUID().equals(newUUID))) {
                return; // 已登记
            }
            Iterator<ShieldTransferData> iterator = transfers.iterator();
            while (iterator.hasNext()) {
                ShieldTransferData data = iterator.next();
                if (data.getProtectedEntityAnywhere() == null) {
                    iterator.remove();
                    ENTITY_TO_PLAYER.remove(data.getProtectedEntityUUID());
                    gytrinket.LOGGER.debug("护盾移植: 实体重建导致UUID变化, 旧UUID {} → 新UUID {}", data.getProtectedEntityUUID(), newUUID);
                    registerTransfer(ownerUUID, entity);
                    return;
                }
            }
        }

        registerTransfer(ownerUUID, entity);
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

        boolean hasShieldTransferItem = false;

        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (!DisableSystem.isItemDisabled(playerUUID, stack) && Config.isShieldTransferItem(stack.getItem())) {
                hasShieldTransferItem = true;
                break;
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
                    // 跨维度查找，避免实体在其他维度时被误判为已消失
                    LivingEntity protectedEntity = data.getProtectedEntityAnywhere();
                    if (protectedEntity == null || !protectedEntity.isAlive()) {
                        // 仅移除记录档案；实体标签保留，实体回归/复活时凭标签自动恢复保护
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

            // 神龛补标签队列为瞬时状态，定期整体清空，防止残留
            if (!PENDING_SHRINE_RE_TAG.isEmpty()) {
                PENDING_SHRINE_RE_TAG.clear();
            }
        });
    }
}
