package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 僚机构造体数据
 * <p>
 * 扩展了基础构造体数据，僚机无额外字段（无阵列系统）。
 */
public class WingmanConstructData extends ConstructData {

    public WingmanConstructData(String constructId, UUID entityUUID, double maxHealth) {
        super(constructId, entityUUID, maxHealth);
    }

    public static WingmanConstructData loadFromNBT(CompoundTag tag) {
        String constructId = tag.getString("constructId");
        UUID entityUUID = tag.getUUID("entityUUID");
        double maxHealth = tag.getDouble("maxHealth");

        WingmanConstructData data = new WingmanConstructData(constructId, entityUUID, maxHealth);
        data.setHealth(tag.getDouble("health"));
        data.setActive(tag.getBoolean("active"));
        if (tag.contains("dimension")) {
            data.setSavedPos(tag.getDouble("posX"), tag.getDouble("posY"), tag.getDouble("posZ"));
            data.setDimension(tag.getString("dimension"));
        }
        return data;
    }
}
