package com.gytrinket.gytrinket.storage.datacenter;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.damage.InvincibilityMarkerManager;
import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.*;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructData;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructData;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gytrinket.gytrinket.core.shield.ShieldData;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import com.gytrinket.gytrinket.core.level.ModLevelDataSlot;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.core.upgrade.UpgradeDataSlot;
import com.gytrinket.gytrinket.storage.datacenter.slot.HealthDataSlot;
import com.gytrinket.gytrinket.storage.datacenter.slot.LightPointStoreSlot;
import com.gytrinket.gytrinket.storage.datacenter.slot.ShieldDataSlot;
import com.gytrinket.gytrinket.storage.datacenter.slot.ShieldTypeSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = gytrinket.MODID)
public class DataCenterLifecycleHandler {

    private static final double MAX_SAVE_DISTANCE = 40.0;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        PlayerDataCenter.registerSlot(new LightPointStoreSlot());
        PlayerDataCenter.registerSlot(new UpgradeDataSlot());
        PlayerDataCenter.registerSlot(new ModLevelDataSlot());
        PlayerDataCenter.registerSlot(new ShieldTypeSlot());
        PlayerDataCenter.registerSlot(new ShieldDataSlot());
        PlayerDataCenter.registerSlot(new HealthDataSlot());

        gytrinket.LOGGER.info("PlayerDataCenter 数据槽注册完成");
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!initialized) {
            init();
        }

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Phase 1: 加载基础数据（从Attachment自动加载，初始化默认值）
        PlayerDataCenter.onLogin(player);

        // Phase 2: 触发光点核心内容变化 → 属性重算
        NeoForge.EVENT_BUS.post(
            new com.gytrinket.gytrinket.event.PlayerLightPointStoreChangedEvent(player.getUUID()));

        // Phase 3: 恢复护盾值（此时maxShield已正确计算）
        ShieldData savedShield = PlayerDataCenter.getData(player.getUUID(), "shield");
        if (savedShield != null && savedShield.getCurrentShield() > 0) {
            double currentMax = ShieldManager.getMaxShield(player.getUUID());
            if (currentMax > 0) {
                double restoredCurrent = Math.min(savedShield.getCurrentShield(), currentMax);
                ShieldManager.setCurrentShield(player.getUUID(), restoredCurrent);
            }
        }

        // Phase 3.5: 恢复当前血量
        Double savedHealth = PlayerDataCenter.getData(player.getUUID(), "health");
        if (savedHealth != null && savedHealth > 0) {
            float maxHealth = serverPlayer.getMaxHealth();
            float restoredHealth = (float) Math.min(savedHealth, maxHealth);
            serverPlayer.setHealth(restoredHealth);
        }

        String activeType = ShieldTypeSlot.determineActiveType(player.getUUID());
        PlayerDataCenter.setData(player.getUUID(), "active_shield_type", activeType);

        com.gytrinket.gytrinket.core.disable.DisableSystem.updateDisabledItems(player.getUUID());

        // Phase 4: 加载构造体/无人机数据（从Attachment的extraNbt）
        loadConstructAndDroneData(serverPlayer);

        // Phase 5: 加载护盾移植数据（从Attachment的extraNbt）
        loadShieldTransferData(serverPlayer);

        com.gytrinket.gytrinket.event.LightPointStoreSyncHandler.sendDataSnapshotToClient(serverPlayer);

        gytrinket.LOGGER.debug("玩家 {} 登录，数据中心初始化完成", player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (player.isInvulnerable()) {
            player.setInvulnerable(false);
        }
        InvincibilityMarkerManager.removeMarker(player);

        // Phase 1: 保存当前血量
        PlayerDataCenter.setData(player.getUUID(), "health", (double) serverPlayer.getHealth());

        // Phase 2: 保存构造体/无人机/护盾移植数据到Attachment
        saveConstructAndDroneData(serverPlayer);
        saveShieldTransferData(serverPlayer);

        // Phase 3: 触发回调（数据自动随玩家实体保存）
        PlayerDataCenter.onLogout(player);

        ConstructManager.getInstance().clearPlayerData(player);
        DroneArrayManager.getInstance().removePlayerData(player);

        gytrinket.LOGGER.debug("玩家 {} 退出，数据中心清理完成", player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerDataCenter.onRespawn(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        PlayerDataCenter.onClone(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!Config.isHardcoreModeEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        if (store == null) {
            return;
        }

        ItemStackHandler handler = store.getItemHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }

        gytrinket.LOGGER.info("困难模式：玩家 {} 死亡，光点核心物品已清空", player.getUUID());

        ModLevelManager.resetData(player.getUUID());
        gytrinket.LOGGER.info("困难模式：玩家 {} 死亡，光点等级已重置", player.getUUID());
    }

    // ===== 构造体/无人机数据保存/加载（使用Attachment替代SavedData） =====

    private static void saveConstructAndDroneData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        PlayerDataAttachment attachment = player.getData(ModAttachments.PLAYER_DATA);

        // 保存构造体数据
        ConstructManager cm = ConstructManager.getInstance();
        DroneArrayManager dam = DroneArrayManager.getInstance();
        boolean isStandby = dam.isInStandby(playerUUID);
        ListTag constructsList = new ListTag();

        if (isStandby) {
            Map<String, List<ConstructData>> allBackups = dam.getAllStandbyBackups(playerUUID);
            for (Map.Entry<String, List<ConstructData>> entry : allBackups.entrySet()) {
                for (ConstructData data : entry.getValue()) {
                    CompoundTag constructTag = new CompoundTag();
                    constructTag.putString("typeId", data.getConstructId());
                    constructTag.put("data", data.saveToNBT());
                    constructsList.add(constructTag);
                }
            }
        } else {
            Map<String, List<ConstructData>> constructs = cm.getPlayerConstructs(playerUUID);
            for (Map.Entry<String, List<ConstructData>> entry : constructs.entrySet()) {
                String typeId = entry.getKey();
                Map<UUID, Entity> entities = cm.getActiveConstructEntities(playerUUID, typeId);
                for (ConstructData data : entry.getValue()) {
                    Entity entity = entities.get(data.getEntityUUID());
                    if (entity == null || !entity.isAlive()) continue;
                    if (entity.level() != player.level()) continue;
                    if (entity.distanceTo(player) > MAX_SAVE_DISTANCE) continue;

                    if (entity instanceof DroneConstructEntity droneEntity && data instanceof DroneConstructData droneData) {
                        double currentMaxHealth = droneEntity.getMaxHealth();
                        float currentHealth = droneEntity.getHealth();
                        droneData.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                        droneData.setMaxHealth(droneEntity.getBaseMaxHealth());
                        droneData.setHasAssaultModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT));
                        droneData.setHasDefenseModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE));
                        droneData.setArrayType(DroneArrayManager.getInstance().getPlayerArrayType(player));
                    } else if (entity instanceof WingmanConstructEntity wingmanEntity && data instanceof WingmanConstructData wingmanData) {
                        double currentMaxHealth = wingmanEntity.getMaxHealth();
                        float currentHealth = wingmanEntity.getHealth();
                        wingmanData.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                        wingmanData.setMaxHealth(wingmanEntity.getBaseMaxHealth());
                    } else if (entity instanceof SwarmConstructEntity swarmEntity && data instanceof SwarmConstructData swarmData) {
                        double currentMaxHealth = swarmEntity.getMaxHealth();
                        float currentHealth = swarmEntity.getHealth();
                        swarmData.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
                        swarmData.setMaxHealth(swarmEntity.getBaseMaxHealth());
                        swarmData.setTier(swarmEntity.getTier());
                    } else if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
                        data.setHealth(livingEntity.getHealth());
                    }
                    data.setSavedPos(entity.getX(), entity.getY(), entity.getZ());
                    data.setDimension(entity.level().dimension().location().toString());

                    CompoundTag constructTag = new CompoundTag();
                    constructTag.putString("typeId", typeId);
                    constructTag.put("data", data.saveToNBT());
                    constructsList.add(constructTag);
                }
            }
            cm.destroyAllConstructEntities(player);
        }

        if (!constructsList.isEmpty()) {
            CompoundTag constructManagerTag = new CompoundTag();
            constructManagerTag.put("constructs", constructsList);
            attachment.setExtraData("construct_manager", constructManagerTag);
        } else {
            attachment.removeExtraData("construct_manager");
        }

        // 保存无人机阵列数据
        CompoundTag arrayTag = new CompoundTag();
        DroneArrayManager.getInstance().saveToNBT(player, arrayTag);
        if (!arrayTag.isEmpty()) {
            attachment.setExtraData("drone_array", arrayTag);
        } else {
            attachment.removeExtraData("drone_array");
        }
    }

    private static void loadConstructAndDroneData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        PlayerDataAttachment attachment = player.getData(ModAttachments.PLAYER_DATA);

        ConstructManager cm = ConstructManager.getInstance();
        DroneArrayManager dam = DroneArrayManager.getInstance();

        cm.clearPlayerData(player);
        dam.removePlayerData(player);

        // 加载无人机阵列数据
        if (attachment.hasExtraData("drone_array")) {
            CompoundTag arrayTag = attachment.getExtraData("drone_array");
            dam.loadFromNBT(player, arrayTag);
        }

        // 加载构造体数据
        if (attachment.hasExtraData("construct_manager")) {
            CompoundTag constructManagerTag = attachment.getExtraData("construct_manager");
            ListTag constructsList = constructManagerTag.getList("constructs", 10);

            boolean isStandby = dam.isInStandby(playerUUID);

            List<ConstructData> allData = new ArrayList<>();
            for (int i = 0; i < constructsList.size(); i++) {
                CompoundTag constructTag = constructsList.getCompound(i);
                String typeId = constructTag.getString("typeId");
                CompoundTag dataTag = constructTag.getCompound("data");

                ConstructData data;
                if ("drone".equals(typeId)) {
                    data = DroneConstructData.loadFromNBT(dataTag);
                } else if ("wingman".equals(typeId)) {
                    data = WingmanConstructData.loadFromNBT(dataTag);
                } else if ("swarm".equals(typeId)) {
                    data = SwarmConstructData.loadFromNBT(dataTag);
                } else {
                    data = ConstructData.loadFromNBT(dataTag);
                }
                allData.add(data);
            }

            if (isStandby) {
                Map<String, List<ConstructData>> backups = new HashMap<>();
                for (ConstructData data : allData) {
                    cm.addConstruct(player, data);
                    backups.computeIfAbsent(data.getConstructId(), k -> new ArrayList<>()).add(data);
                }
                for (Map.Entry<String, List<ConstructData>> entry : backups.entrySet()) {
                    dam.setStandbyBackup(playerUUID, entry.getKey(), entry.getValue());
                }
                cm.setBuildingDisabled(player, true);
            } else {
                for (ConstructData data : allData) {
                    cm.addConstruct(player, data);
                }

                DroneArrayType arrayType = dam.getPlayerArrayType(player);
                if (arrayType == null) {
                    arrayType = DroneArrayType.Types.ORBIT;
                }

                createConstructEntities(player, DroneConstructTypes.DRONE, arrayType);
                createConstructEntities(player, WingmanConstructTypes.WINGMAN, arrayType);
                createConstructEntities(player, SwarmConstructTypes.SWARM, arrayType);
            }
        }
    }

    /**
     * 从存档数据重建指定类型的所有构造体实体（玩家登录时）
     */
    private static void createConstructEntities(ServerPlayer player, String typeId, DroneArrayType arrayType) {
        List<ConstructData> dataList = ConstructManager.getInstance().getPlayerConstructsByType(player, typeId);
        for (ConstructData data : dataList) {
            createConstructEntity(player, data, typeId, arrayType);
        }
    }

    /**
     * 从存档数据重建单个构造体实体
     */
    private static void createConstructEntity(ServerPlayer player, ConstructData data, String typeId, DroneArrayType arrayType) {
        ServerLevel serverLevel = player.serverLevel();
        AbstractConstructEntity entity = createConstructEntityOfType(typeId, serverLevel);
        if (entity == null) return;

        // 位置：优先使用保存的位置（同维度），否则在玩家上方生成
        String currentDimension = player.level().dimension().location().toString();
        if (data.hasPosition() && data.getDimension().equals(currentDimension)) {
            entity.setPos(data.getPosX(), data.getPosY(), data.getPosZ());
        } else {
            entity.setPos(player.getX(), player.getY() + 1, player.getZ());
        }

        entity.setOwnerUUID(player.getUUID());

        // 类型特定的属性和状态恢复
        setupEntityFromData(entity, data, arrayType);

        // 用保存的生命值比例恢复当前生命值
        float healthRatio = (float) data.getHealthRatio();
        float newMaxHealth = entity.getMaxHealth();
        entity.setHealth(newMaxHealth * healthRatio);
        serverLevel.addFreshEntity(entity);

        data.setEntityUUID(entity.getUUID());
        ConstructManager.getInstance().registerConstructEntity(player.getUUID(), typeId, entity);
    }

    private static AbstractConstructEntity createConstructEntityOfType(String typeId, ServerLevel serverLevel) {
        return switch (typeId) {
            case DroneConstructTypes.DRONE -> new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), serverLevel);
            case WingmanConstructTypes.WINGMAN -> new WingmanConstructEntity(ModEntities.WINGMAN_CONSTRUCT.get(), serverLevel);
            case SwarmConstructTypes.SWARM -> new SwarmConstructEntity(ModEntities.SWARM_CONSTRUCT.get(), serverLevel);
            default -> null;
        };
    }

    /**
     * 根据存档数据设置实体的属性和类型特定状态
     */
    private static void setupEntityFromData(AbstractConstructEntity entity, ConstructData data, DroneArrayType arrayType) {
        if (entity instanceof DroneConstructEntity drone && data instanceof DroneConstructData droneData) {
            drone.setBaseMaxHealth(droneData.getMaxHealth());
            drone.getAttribute(Attributes.MAX_HEALTH).setBaseValue(droneData.getMaxHealth());
            drone.setArrayType(arrayType);
            if (droneData.hasAssaultModule()) {
                drone.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
            }
            if (droneData.hasDefenseModule()) {
                drone.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
            }
            if (!droneData.hasAssaultModule() && !droneData.hasDefenseModule()) {
                drone.refreshConstructAttributes();
            }
        } else if (entity instanceof WingmanConstructEntity wingman && data instanceof WingmanConstructData wingmanData) {
            wingman.setBaseMaxHealth(wingmanData.getMaxHealth());
            wingman.getAttribute(Attributes.MAX_HEALTH).setBaseValue(wingmanData.getMaxHealth());
            wingman.refreshConstructAttributes();
        } else if (entity instanceof SwarmConstructEntity swarm && data instanceof SwarmConstructData swarmData) {
            // 蜂群通过 setTier 同时应用属性修饰器
            swarm.setTier(swarmData.getTier());
        }
    }

    // ===== 护盾移植数据保存/加载（使用Attachment替代SavedData） =====

    private static void saveShieldTransferData(ServerPlayer player) {
        PlayerDataAttachment attachment = player.getData(ModAttachments.PLAYER_DATA);

        CompoundTag transferTag = new CompoundTag();
        ShieldTransferManager.saveTransfersToNBT(player, transferTag);
        if (!transferTag.isEmpty()) {
            attachment.setExtraData("shield_transfer", transferTag);
        } else {
            attachment.removeExtraData("shield_transfer");
        }
    }

    private static void loadShieldTransferData(ServerPlayer player) {
        PlayerDataAttachment attachment = player.getData(ModAttachments.PLAYER_DATA);

        if (!attachment.hasExtraData("shield_transfer")) {
            return;
        }

        CompoundTag transferTag = attachment.getExtraData("shield_transfer");
        ShieldTransferManager.loadTransfersFromNBT(player, transferTag);

        NeoForge.EVENT_BUS.post(
            new com.gytrinket.gytrinket.core.shield_transfer.event.ShieldTransferRebuiltEvent(player));
    }
}
