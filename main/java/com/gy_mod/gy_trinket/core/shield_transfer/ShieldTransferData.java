package com.gy_mod.gy_trinket.core.shield_transfer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class ShieldTransferData {

    private final UUID ownerUUID;
    private final UUID protectedEntityUUID;
    private long transferTime;

    public ShieldTransferData(UUID ownerUUID, LivingEntity protectedEntity) {
        this.ownerUUID = ownerUUID;
        this.protectedEntityUUID = protectedEntity.getUUID();
        this.transferTime = System.currentTimeMillis();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public UUID getProtectedEntityUUID() {
        return protectedEntityUUID;
    }

    public long getTransferTime() {
        return transferTime;
    }

    public void updateTransferTime() {
        this.transferTime = System.currentTimeMillis();
    }

    public LivingEntity getProtectedEntity(Level level) {
        if (level == null) {
            return null;
        }
        if (level instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(protectedEntityUUID);
            if (entity instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    public boolean isEntityValid(Level level) {
        if (level == null) {
            return false;
        }
        LivingEntity entity = getProtectedEntity(level);
        return entity != null && entity.isAlive();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ownerUUID", ownerUUID);
        tag.putUUID("protectedEntityUUID", protectedEntityUUID);
        tag.putLong("transferTime", transferTime);
        return tag;
    }

    public static ShieldTransferData load(CompoundTag tag) {
        UUID ownerUUID = tag.getUUID("ownerUUID");
        UUID protectedEntityUUID = tag.getUUID("protectedEntityUUID");
        long transferTime = tag.getLong("transferTime");

        ShieldTransferData data = new ShieldTransferData(ownerUUID, protectedEntityUUID);
        data.transferTime = transferTime;
        return data;
    }

    private ShieldTransferData(UUID ownerUUID, UUID protectedEntityUUID) {
        this.ownerUUID = ownerUUID;
        this.protectedEntityUUID = protectedEntityUUID;
        this.transferTime = System.currentTimeMillis();
    }
}
