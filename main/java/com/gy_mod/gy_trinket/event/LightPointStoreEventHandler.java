package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LightPointStoreEventHandler {

    private static final double MAX_SAVE_DISTANCE = 40.0;

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        savePlayerStoreToNBT(player);
        saveConstructManagerToNBT(player);
        saveDroneArrayToNBT(player);
        saveShieldTransferToNBT(player);

        ConstructManager.getInstance().clearPlayerData(player);
        DroneArrayManager.getInstance().removePlayerData(player);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        ConstructManager.getInstance().clearPlayerData(player);
        DroneArrayManager.getInstance().removePlayerData(player);

        loadPlayerStoreFromNBT(player);
        loadDroneArrayFromNBT(player);
        loadConstructManagerFromNBT(player);
        loadShieldTransferFromNBT(player);

        com.gy_mod.gy_trinket.core.disable.DisableSystem.updateDisabledItems(player.getUUID());

        if (player instanceof ServerPlayer serverPlayer) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.gy_mod.gy_trinket.core.shield_transfer.event.ShieldTransferRebuiltEvent(serverPlayer));
        }
    }

    private void savePlayerStoreToNBT(Player player) {
        CompoundTag storeTag = PlayerStoreManager.saveToNBT(player);
        if (!storeTag.isEmpty()) {
            player.getPersistentData().put("gy_trinket:light_point_store", storeTag);
        }
    }

    private void loadPlayerStoreFromNBT(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains("gy_trinket:light_point_store")) {
            CompoundTag storeTag = persistentData.getCompound("gy_trinket:light_point_store");
            PlayerStoreManager.loadFromNBT(player, storeTag);
        } else {
            PlayerStoreManager.getOrCreatePlayerStore(player);
        }
    }

    private void saveConstructManagerToNBT(Player player) {
        UUID playerUUID = player.getUUID();
        ConstructManager cm = ConstructManager.getInstance();
        DroneArrayManager dam = DroneArrayManager.getInstance();

        boolean isStandby = dam.isInStandby(playerUUID);
        ListTag constructsList = new ListTag();

        if (isStandby) {
            List<DroneConstructData> backupList = dam.getStandbyBackup(playerUUID);
            if (backupList != null) {
                for (DroneConstructData data : backupList) {
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
                    if (entity == null || !entity.isAlive()) {
                        continue;
                    }
                    if (entity.level() != player.level()) {
                        continue;
                    }
                    double distance = entity.distanceTo(player);
                    if (distance > MAX_SAVE_DISTANCE) {
                        continue;
                    }
                    if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
                        data.setHealth(livingEntity.getHealth());
                    }
                    if (entity instanceof DroneConstructEntity droneEntity && data instanceof DroneConstructData droneData) {
                        droneData.setHasAssaultModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT));
                        droneData.setHasDefenseModule(droneEntity.hasEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE));
                        droneData.setArrayType(DroneArrayManager.getInstance().getPlayerArrayType(player));
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
            player.getPersistentData().put("gy_trinket:construct_manager", constructManagerTag);
        } else {
            player.getPersistentData().remove("gy_trinket:construct_manager");
        }
    }

    private void loadConstructManagerFromNBT(Player player) {
        CompoundTag persistentData = player.getPersistentData();

        if (!persistentData.contains("gy_trinket:construct_manager")) {
            return;
        }

        CompoundTag constructManagerTag = persistentData.getCompound("gy_trinket:construct_manager");
        ListTag constructsList = constructManagerTag.getList("constructs", 10);

        DroneArrayManager dam = DroneArrayManager.getInstance();
        boolean isStandby = dam.isInStandby(player.getUUID());

        List<ConstructData> allData = new ArrayList<>();
        for (int i = 0; i < constructsList.size(); i++) {
            CompoundTag constructTag = constructsList.getCompound(i);
            String typeId = constructTag.getString("typeId");
            CompoundTag dataTag = constructTag.getCompound("data");

            ConstructData data;
            if ("drone".equals(typeId)) {
                data = DroneConstructData.loadFromNBT(dataTag);
            } else {
                data = ConstructData.loadFromNBT(dataTag);
            }
            allData.add(data);
        }

        ConstructManager cm = ConstructManager.getInstance();

        if (isStandby) {
            List<DroneConstructData> backupList = new ArrayList<>();
            for (ConstructData data : allData) {
                cm.addConstruct(player, data);
                if (data instanceof DroneConstructData droneData) {
                    backupList.add(droneData);
                }
            }
            dam.setStandbyBackup(player.getUUID(), backupList);
            cm.setBuildingDisabled(player, true);
        } else {
            for (ConstructData data : allData) {
                cm.addConstruct(player, data);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                DroneArrayType arrayType = dam.getPlayerArrayType(player);
                if (arrayType == null) {
                    arrayType = DroneArrayType.Types.ORBIT;
                }

                List<ConstructData> droneDataList = cm.getPlayerConstructsByType(player, DroneConstructTypes.DRONE);
                for (ConstructData data : droneDataList) {
                    if (data instanceof DroneConstructData droneData) {
                        createDroneEntity(serverPlayer, droneData, arrayType);
                    }
                }
            }
        }
    }

    private void createDroneEntity(ServerPlayer player, DroneConstructData data, DroneArrayType arrayType) {
        ServerLevel serverLevel = player.serverLevel();

        DroneConstructEntity droneEntity = new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), serverLevel);

        String currentDimension = player.level().dimension().location().toString();
        if (data.hasPosition() && data.getDimension().equals(currentDimension)) {
            droneEntity.setPos(data.getPosX(), data.getPosY(), data.getPosZ());
        } else {
            droneEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
        }

        droneEntity.setOwnerUUID(player.getUUID());
        droneEntity.setArrayType(arrayType);

        if (data.hasAssaultModule()) {
            droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
        }
        if (data.hasDefenseModule()) {
            droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
        }

        droneEntity.setHealth((float) data.getHealth());
        serverLevel.addFreshEntity(droneEntity);

        data.setEntityUUID(droneEntity.getUUID());
        ConstructManager.getInstance().registerConstructEntity(player.getUUID(), DroneConstructTypes.DRONE, droneEntity);
    }

    private void saveDroneArrayToNBT(Player player) {
        CompoundTag arrayTag = new CompoundTag();
        DroneArrayManager.getInstance().saveToNBT(player, arrayTag);
        if (!arrayTag.isEmpty()) {
            player.getPersistentData().put("gy_trinket:drone_array", arrayTag);
        }
    }

    private void loadDroneArrayFromNBT(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains("gy_trinket:drone_array")) {
            CompoundTag arrayTag = persistentData.getCompound("gy_trinket:drone_array");
            DroneArrayManager.getInstance().loadFromNBT(player, arrayTag);
        }
    }

    private void saveShieldTransferToNBT(Player player) {
        CompoundTag transferTag = new CompoundTag();
        ShieldTransferManager.saveTransfersToNBT(player, transferTag);
        if (!transferTag.isEmpty()) {
            player.getPersistentData().put("gy_trinket:shield_transfer", transferTag);
        }
    }

    private void loadShieldTransferFromNBT(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains("gy_trinket:shield_transfer")) {
            CompoundTag transferTag = persistentData.getCompound("gy_trinket:shield_transfer");
            ShieldTransferManager.loadTransfersFromNBT(player, transferTag);
        }
    }
}
