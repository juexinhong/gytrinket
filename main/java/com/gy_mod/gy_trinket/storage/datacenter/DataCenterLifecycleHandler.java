package com.gy_mod.gy_trinket.storage.datacenter;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.damage.InvincibilityMarkerManager;
import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.*;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructData;
import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeDataSlot;
import com.gy_mod.gy_trinket.storage.datacenter.slot.HealthDataSlot;
import com.gy_mod.gy_trinket.storage.datacenter.slot.LightPointStoreSlot;
import com.gy_mod.gy_trinket.storage.datacenter.slot.ShieldDataSlot;
import com.gy_mod.gy_trinket.storage.datacenter.slot.ModLevelDataSlot;
import com.gy_mod.gy_trinket.storage.datacenter.slot.ShieldTypeSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
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
        PlayerDataCenter.registerSlot(new ShieldTypeSlot());
        PlayerDataCenter.registerSlot(new ShieldDataSlot());
        PlayerDataCenter.registerSlot(new HealthDataSlot());
        PlayerDataCenter.registerSlot(new ModLevelDataSlot());

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

        migrateLegacyData(serverPlayer);

        // Phase 1: 加载基础数据（光点核心物品、护盾类型、护盾值）
        PlayerDataCenter.onLogin(player);

        // Phase 2: 触发光点核心内容变化 → 属性重算（此时物品已加载，属性会正确计算）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new com.gy_mod.gy_trinket.event.PlayerLightPointStoreChangedEvent(player.getUUID()));

        // Phase 3: 属性重算完成后，恢复护盾值（此时maxShield已正确计算）
        ShieldData savedShield = PlayerDataCenter.getData(player.getUUID(), "shield");
        if (savedShield != null && savedShield.getCurrentShield() > 0) {
            double currentMax = ShieldManager.getMaxShield(player.getUUID());
            if (currentMax > 0) {
                double restoredCurrent = Math.min(savedShield.getCurrentShield(), currentMax);
                ShieldManager.setCurrentShield(player.getUUID(), restoredCurrent);
            }
        }

        // Phase 3.5: 恢复当前血量（属性重算后maxHealth已恢复，此时可以安全设置）
        Double savedHealth = PlayerDataCenter.getData(player.getUUID(), "health");
        if (savedHealth != null && savedHealth > 0) {
            float maxHealth = serverPlayer.getMaxHealth();
            float restoredHealth = (float) Math.min(savedHealth, maxHealth);
            serverPlayer.setHealth(restoredHealth);
        }

        String activeType = ShieldTypeSlot.determineActiveType(player.getUUID());
        PlayerDataCenter.setData(player.getUUID(), "active_shield_type", activeType);

        com.gy_mod.gy_trinket.core.disable.DisableSystem.updateDisabledItems(player.getUUID());

        // Phase 4: 加载构造体/无人机数据（此时属性已重算，drone_count加成已生效）
        loadConstructAndDroneData(serverPlayer);

        // Phase 5: 加载护盾移植数据
        loadShieldTransferData(serverPlayer);

        com.gy_mod.gy_trinket.event.LightPointStoreSyncHandler.sendDataSnapshotToClient(serverPlayer);

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

        // Phase 1: 保存当前血量到数据中心
        PlayerDataCenter.setData(player.getUUID(), "health", (double) serverPlayer.getHealth());

        // Phase 2: 保存构造体/无人机/护盾移植数据到 SavedData
        saveConstructAndDroneData(serverPlayer);
        saveShieldTransferData(serverPlayer);

        // Phase 3: 保存基础数据并清理
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
    }

    private static void saveConstructAndDroneData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);
        CompoundTag existingData = storage.hasPlayerData(playerUUID)
            ? storage.getPlayerData(playerUUID) : new CompoundTag();

        // 保存构造体数据
        ConstructManager cm = ConstructManager.getInstance();
        DroneArrayManager dam = DroneArrayManager.getInstance();
        boolean isStandby = dam.isInStandby(playerUUID);
        ListTag constructsList = new ListTag();

        if (isStandby) {
            // 待机模式：直接序列化待机备份数据
            Map<String, List<ConstructData>> allBackups = dam.getAllStandbyBackups(playerUUID);
            for (Map.Entry<String, List<ConstructData>> entry : allBackups.entrySet()) {
                String typeId = entry.getKey();
                for (ConstructData data : entry.getValue()) {
                    CompoundTag constructTag = new CompoundTag();
                    constructTag.putString("typeId", typeId);
                    constructTag.put("data", data.saveToNBT());
                    constructsList.add(constructTag);
                }
            }
        } else {
            // 非待机模式：从活跃实体创建快照
            Map<String, List<ConstructData>> constructs = cm.getPlayerConstructs(playerUUID);
            for (Map.Entry<String, List<ConstructData>> entry : constructs.entrySet()) {
                String typeId = entry.getKey();
                Map<UUID, Entity> entities = cm.getActiveConstructEntities(playerUUID, typeId);
                for (ConstructData data : entry.getValue()) {
                    Entity entity = entities.get(data.getEntityUUID());
                    if (entity == null || !entity.isAlive()) continue;
                    if (entity.level() != player.level()) continue;
                    if (entity.distanceTo(player) > MAX_SAVE_DISTANCE) continue;

                    // 使用 snapshotToData 统一提取实体状态
                    if (entity instanceof AbstractConstructEntity constructEntity) {
                        ConstructData snapshot = constructEntity.snapshotToData();
                        if (snapshot != null) {
                            CompoundTag constructTag = new CompoundTag();
                            constructTag.putString("typeId", typeId);
                            constructTag.put("data", snapshot.saveToNBT());
                            constructsList.add(constructTag);
                        }
                    }
                }
            }
            cm.destroyAllConstructEntities(player);
        }

        if (!constructsList.isEmpty()) {
            CompoundTag constructManagerTag = new CompoundTag();
            constructManagerTag.put("constructs", constructsList);
            existingData.put("construct_manager", constructManagerTag);
        } else {
            existingData.remove("construct_manager");
        }

        // 保存无人机阵列数据
        CompoundTag arrayTag = new CompoundTag();
        DroneArrayManager.getInstance().saveToNBT(player, arrayTag);
        if (!arrayTag.isEmpty()) {
            existingData.put("drone_array", arrayTag);
        } else {
            existingData.remove("drone_array");
        }

        storage.putPlayerData(playerUUID, existingData);
    }

    private static void loadConstructAndDroneData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);
        CompoundTag savedData = storage.getPlayerData(playerUUID);
        if (savedData == null) {
            return;
        }

        ConstructManager cm = ConstructManager.getInstance();
        DroneArrayManager dam = DroneArrayManager.getInstance();

        cm.clearPlayerData(player);
        dam.removePlayerData(player);

        // 加载无人机阵列数据
        if (savedData.contains("drone_array")) {
            CompoundTag arrayTag = savedData.getCompound("drone_array");
            dam.loadFromNBT(player, arrayTag);
        }

        // 加载构造体数据
        if (savedData.contains("construct_manager")) {
            CompoundTag constructManagerTag = savedData.getCompound("construct_manager");
            ListTag constructsList = constructManagerTag.getList("constructs", 10);

            boolean isStandby = dam.isInStandby(playerUUID);

            // 按类型分组加载数据
            Map<String, List<ConstructData>> groupedData = new java.util.LinkedHashMap<>();
            for (int i = 0; i < constructsList.size(); i++) {
                CompoundTag constructTag = constructsList.getCompound(i);
                String typeId = constructTag.getString("typeId");
                CompoundTag dataTag = constructTag.getCompound("data");

                ConstructData data = loadConstructDataByType(typeId, dataTag);
                if (data != null) {
                    groupedData.computeIfAbsent(typeId, k -> new ArrayList<>()).add(data);
                }
            }

            if (isStandby) {
                // 待机模式：仅加载数据到管理器，设置待机备份
                for (Map.Entry<String, List<ConstructData>> entry : groupedData.entrySet()) {
                    String typeId = entry.getKey();
                    List<ConstructData> dataList = entry.getValue();

                    for (ConstructData data : dataList) {
                        cm.addConstruct(player, data);
                    }

                    // 设置待机备份
                    ConstructType constructType = cm.getConstructType(typeId);
                    if (constructType != null) {
                        dam.setStandbyBackup(playerUUID, typeId, dataList);
                    }
                }
                cm.setBuildingDisabled(player, true);
            } else {
                // 非待机模式：加载数据并恢复实体
                for (Map.Entry<String, List<ConstructData>> entry : groupedData.entrySet()) {
                    String typeId = entry.getKey();
                    List<ConstructData> dataList = entry.getValue();

                    ConstructType constructType = cm.getConstructType(typeId);
                    if (constructType == null || !constructType.hasEntityRestorer()) continue;

                    for (ConstructData data : dataList) {
                        cm.addConstruct(player, data);
                        Entity entity = constructType.getEntityRestorer().restore(player, data, player.serverLevel());
                        if (entity != null) {
                            cm.registerConstructEntity(playerUUID, typeId, entity);
                        }
                    }
                }
            }
        }
    }

    /**
     * 根据 typeId 分发到正确的 loadFromNBT 方法
     */
    private static ConstructData loadConstructDataByType(String typeId, CompoundTag dataTag) {
        return switch (typeId) {
            case "drone" -> DroneConstructData.loadFromNBT(dataTag);
            case "wingman" -> WingmanConstructData.loadFromNBT(dataTag);
            case "swarm" -> SwarmConstructData.loadFromNBT(dataTag);
            default -> ConstructData.loadFromNBT(dataTag);
        };
    }

    private static void saveShieldTransferData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);
        CompoundTag existingData = storage.hasPlayerData(playerUUID)
            ? storage.getPlayerData(playerUUID) : new CompoundTag();

        CompoundTag transferTag = new CompoundTag();
        ShieldTransferManager.saveTransfersToNBT(player, transferTag);
        if (!transferTag.isEmpty()) {
            existingData.put("shield_transfer", transferTag);
        } else {
            existingData.remove("shield_transfer");
        }

        storage.putPlayerData(playerUUID, existingData);
    }

    private static void loadShieldTransferData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);
        CompoundTag savedData = storage.getPlayerData(playerUUID);
        if (savedData == null || !savedData.contains("shield_transfer")) {
            return;
        }

        CompoundTag transferTag = savedData.getCompound("shield_transfer");
        ShieldTransferManager.loadTransfersFromNBT(player, transferTag);

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new com.gy_mod.gy_trinket.core.shield_transfer.event.ShieldTransferRebuiltEvent(player));
    }

    private static void migrateLegacyData(ServerPlayer player) {
        var persistentData = player.getPersistentData();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        PlayerDataStorage storage = PlayerDataStorage.get(server);
        UUID uuid = player.getUUID();

        boolean migrated = false;
        CompoundTag existingData = storage.hasPlayerData(uuid) ? storage.getPlayerData(uuid) : new CompoundTag();

        if (persistentData.contains("gy_trinket.current_shield") || persistentData.contains("gy_trinket.max_shield")) {
            double currentShield = persistentData.contains("gy_trinket.current_shield") ? persistentData.getDouble("gy_trinket.current_shield") : 0;
            double maxShield = persistentData.contains("gy_trinket.max_shield") ? persistentData.getDouble("gy_trinket.max_shield") : 0;

            if (maxShield > 0) {
                CompoundTag shieldTag = new CompoundTag();
                shieldTag.putDouble("currentShield", currentShield);
                shieldTag.putDouble("maxShield", maxShield);
                existingData.put("shield", shieldTag);
                migrated = true;
            }

            persistentData.remove("gy_trinket.current_shield");
            persistentData.remove("gy_trinket.max_shield");
            gytrinket.LOGGER.debug("迁移旧版护盾数据到SavedData");
        }

        if (persistentData.contains("gy_trinket:light_point_store")) {
            existingData.put("light_point_store", persistentData.getCompound("gy_trinket:light_point_store"));
            persistentData.remove("gy_trinket:light_point_store");
            migrated = true;
            gytrinket.LOGGER.debug("迁移旧版光点核心数据到SavedData");
        }

        if (persistentData.contains("gy_trinket:construct_manager")) {
            existingData.put("construct_manager", persistentData.getCompound("gy_trinket:construct_manager"));
            persistentData.remove("gy_trinket:construct_manager");
            migrated = true;
            gytrinket.LOGGER.debug("迁移旧版构造体数据到SavedData");
        }

        if (persistentData.contains("gy_trinket:drone_array")) {
            existingData.put("drone_array", persistentData.getCompound("gy_trinket:drone_array"));
            persistentData.remove("gy_trinket:drone_array");
            migrated = true;
            gytrinket.LOGGER.debug("迁移旧版无人机阵列数据到SavedData");
        }

        if (persistentData.contains("gy_trinket:shield_transfer")) {
            existingData.put("shield_transfer", persistentData.getCompound("gy_trinket:shield_transfer"));
            persistentData.remove("gy_trinket:shield_transfer");
            migrated = true;
            gytrinket.LOGGER.debug("迁移旧版护盾移植数据到SavedData");
        }

        if (migrated) {
            storage.putPlayerData(uuid, existingData);
        }
    }
}
