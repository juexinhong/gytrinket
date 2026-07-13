package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 蜂群构造体数据
 * <p>
 * 扩展基础构造体数据，记录单实例的等阶（基础/标准/高阶）。
 * 等阶由构建时随机决定，影响该实例的生命与伤害加成。
 */
public class SwarmConstructData extends ConstructData {
    /** 等阶：0=基础 1=标准 2=高阶 */
    private int tier = SwarmConstructTypes.TIER_BASIC;

    public SwarmConstructData(String constructId, UUID entityUUID, double maxHealth) {
        super(constructId, entityUUID, maxHealth);
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    @Override
    public CompoundTag saveToNBT() {
        CompoundTag tag = super.saveToNBT();
        tag.putInt("tier", tier);
        return tag;
    }

    public static SwarmConstructData loadFromNBT(CompoundTag tag) {
        String constructId = tag.getString("constructId");
        UUID entityUUID = tag.getUUID("entityUUID");
        double maxHealth = tag.getDouble("maxHealth");

        SwarmConstructData data = new SwarmConstructData(constructId, entityUUID, maxHealth);
        // 手动加载公共字段（ConstructData 无 loadCommonFields）
        data.setHealth(tag.getDouble("health"));
        data.setActive(tag.getBoolean("active"));
        if (tag.contains("dimension")) {
            data.setSavedPos(tag.getDouble("posX"), tag.getDouble("posY"), tag.getDouble("posZ"));
            data.setDimension(tag.getString("dimension"));
        }
        if (tag.contains("tier")) {
            data.setTier(tag.getInt("tier"));
        }
        return data;
    }
}
