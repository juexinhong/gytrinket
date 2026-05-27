package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 无人机构造体数据
 * <p>
 * 扩展了基础构造体数据，添加了阵列类型和突击/防御模块信息。
 */
public class DroneConstructData extends ConstructData {
    private DroneArrayType arrayType;
    private boolean hasAssaultModule;
    private boolean hasDefenseModule;

    public DroneConstructData(String constructId, UUID entityUUID, double maxHealth, DroneArrayType arrayType) {
        super(constructId, entityUUID, maxHealth);
        this.arrayType = arrayType;
        this.hasAssaultModule = false;
        this.hasDefenseModule = false;
    }

    public DroneArrayType getArrayType() {
        return arrayType;
    }

    public void setArrayType(DroneArrayType arrayType) {
        this.arrayType = arrayType;
    }

    public boolean hasAssaultModule() {
        return hasAssaultModule;
    }

    public void setHasAssaultModule(boolean hasAssaultModule) {
        this.hasAssaultModule = hasAssaultModule;
    }

    public boolean hasDefenseModule() {
        return hasDefenseModule;
    }

    public void setHasDefenseModule(boolean hasDefenseModule) {
        this.hasDefenseModule = hasDefenseModule;
    }

    @Override
    public CompoundTag saveToNBT() {
        CompoundTag tag = super.saveToNBT();
        tag.putString("arrayType", arrayType.getId());
        tag.putBoolean("hasAssaultModule", hasAssaultModule);
        tag.putBoolean("hasDefenseModule", hasDefenseModule);
        return tag;
    }

    public static DroneConstructData loadFromNBT(CompoundTag tag) {
        String constructId = tag.getString("constructId");
        UUID entityUUID = tag.getUUID("entityUUID");
        double maxHealth = tag.getDouble("maxHealth");
        DroneArrayType arrayType = DroneArrayType.Types.fromId(tag.getString("arrayType"));
        
        DroneConstructData data = new DroneConstructData(constructId, entityUUID, maxHealth, arrayType);
        data.setHealth(tag.getDouble("health"));
        data.setActive(tag.getBoolean("active"));
        data.setHasAssaultModule(tag.getBoolean("hasAssaultModule"));
        data.setHasDefenseModule(tag.getBoolean("hasDefenseModule"));
        if (tag.contains("dimension")) {
            data.setSavedPos(tag.getDouble("posX"), tag.getDouble("posY"), tag.getDouble("posZ"));
            data.setDimension(tag.getString("dimension"));
        }
        return data;
    }
}
