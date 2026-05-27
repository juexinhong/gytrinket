package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 构造体实例数据
 * <p>
 * 存储构造体实例的运行时数据，包括：
 * <ul>
 *   <li>所属的构造体类型ID</li>
 *   <li>关联实体的UUID</li>
 *   <li>当前生命值</li>
 *   <li>最大生命值</li>
 *   <li>激活状态</li>
 *   <li>创建时间戳</li>
 * </ul>
 * <p>
 * 注意：此类仅存储数据，不包含业务逻辑
 */
public class ConstructData {
    /** 构造体类型ID */
    private final String constructId;

    private UUID entityUUID;

    /** 当前生命值 */
    private double health;

    /** 最大生命值 */
    private double maxHealth;

    /** 是否处于激活状态 */
    private boolean active;

    /** 创建时间戳（毫秒） */
    private long createdTime;

    private double posX;
    private double posY;
    private double posZ;
    private String dimension;

    /**
     * 创建构造体数据
     *
     * @param constructId 构造体类型ID
     * @param entityUUID  关联实体UUID
     * @param maxHealth   最大生命值
     */
    public ConstructData(String constructId, UUID entityUUID, double maxHealth) {
        this.constructId = constructId;
        this.entityUUID = entityUUID;
        this.health = maxHealth;
        this.maxHealth = maxHealth;
        this.active = true;
        this.createdTime = System.currentTimeMillis();
    }

    /** 获取构造体类型ID */
    public String getConstructId() {
        return constructId;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    public void setEntityUUID(UUID entityUUID) {
        this.entityUUID = entityUUID;
    }

    /** 获取当前生命值 */
    public double getHealth() {
        return health;
    }

    /**
     * 设置当前生命值
     * <p>
     * 会被自动限制在 [0, 最大生命值] 范围内
     *
     * @param health 新的生命值
     */
    public void setHealth(double health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
    }

    /** 获取最大生命值 */
    public double getMaxHealth() {
        return maxHealth;
    }

    /**
     * 设置最大生命值
     * <p>
     * 如果当前生命值超过新的最大生命值，会自动调整为最大生命值
     *
     * @param maxHealth 新的最大生命值
     */
    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
        if (this.health > maxHealth) {
            this.health = maxHealth;
        }
    }

    /** 检查是否激活 */
    public boolean isActive() {
        return active;
    }

    /** 设置激活状态 */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** 获取创建时间戳 */
    public long getCreatedTime() {
        return createdTime;
    }

    public boolean hasPosition() {
        return dimension != null;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public String getDimension() {
        return dimension;
    }

    public void setSavedPos(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    /**
     * 对构造体造成伤害
     *
     * @param amount 伤害量
     */
    public void damage(double amount) {
        setHealth(health - amount);
    }

    /**
     * 检查构造体是否死亡
     * <p>
     * 当生命值小于等于0时认为已死亡
     *
     * @return 如果已死亡返回true
     */
    public boolean isDead() {
        return health <= 0;
    }

    /**
     * 保存到 NBT
     * @return NBT 标签
     */
    public CompoundTag saveToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("constructId", constructId);
        tag.putUUID("entityUUID", entityUUID);
        tag.putDouble("health", health);
        tag.putDouble("maxHealth", maxHealth);
        tag.putBoolean("active", active);
        tag.putLong("createdTime", createdTime);
        if (dimension != null) {
            tag.putDouble("posX", posX);
            tag.putDouble("posY", posY);
            tag.putDouble("posZ", posZ);
            tag.putString("dimension", dimension);
        }
        return tag;
    }

    /**
     * 从 NBT 加载
     * @param tag NBT 标签
     * @return 加载的构造体数据
     */
    public static ConstructData loadFromNBT(CompoundTag tag) {
        String constructId = tag.getString("constructId");
        UUID entityUUID = tag.getUUID("entityUUID");
        double maxHealth = tag.getDouble("maxHealth");
        ConstructData data = new ConstructData(constructId, entityUUID, maxHealth);
        data.health = tag.getDouble("health");
        data.active = tag.getBoolean("active");
        data.createdTime = tag.getLong("createdTime");
        if (tag.contains("dimension")) {
            data.posX = tag.getDouble("posX");
            data.posY = tag.getDouble("posY");
            data.posZ = tag.getDouble("posZ");
            data.dimension = tag.getString("dimension");
        }
        return data;
    }
}