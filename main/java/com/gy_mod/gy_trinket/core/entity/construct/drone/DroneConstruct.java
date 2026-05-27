package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IConstruct;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.IDroneBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.IDroneEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 无人机构建体
 * <p>
 * 唯一的无人机构造体实现，可以生成在世界里。
 * 通过组合方式支持不同的阵列行为和效果修饰。
 * <p>
 * 层级结构：
 * <ul>
 *   <li>构造体 (IConstruct)</li>
 *   <li>   └── 无人机构建体 (DroneConstruct) ← 具体实现</li>
 * </ul>
 * <p>
 * 无人机通过以下方式扩展功能：
 * <ul>
 *   <li>阵列行为 (IDroneBehavior)：环绕/待机，决定基础移动模式</li>
 *   <li>效果修饰 (IDroneEffect)：突击/防御，附加额外能力</li>
 * </ul>
 */
public class DroneConstruct implements IConstruct {
    private final String constructId;
    private final LivingEntity owner;
    private final double maxHealth;
    private double health;
    private boolean active;
    private UUID droneEntityUUID;

    private DroneArrayType arrayType;
    private final List<IDroneEffect> effects = new ArrayList<>();
    private boolean commander = false;

    public DroneConstruct(String constructId, DroneArrayType arrayType, List<IDroneEffect> effects, LivingEntity owner, double maxHealth) {
        this.constructId = constructId;
        this.arrayType = arrayType;
        this.effects.addAll(effects);
        this.owner = owner;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.active = true;
    }

    @Override
    public String getConstructId() {
        return constructId;
    }

    @Override
    public ConstructType getConstructType() {
        return ConstructManager.getInstance().getConstructType(constructId);
    }

    @Override
    public Entity getEntity() {
        if (droneEntityUUID == null || owner == null) {
            return null;
        }
        Level level = owner.level();
        AABB searchBox = new AABB(-1000, -1000, -1000, 1000, 1000, 1000);
        List<Entity> entities = level.getEntities(null, searchBox);
        for (Entity entity : entities) {
            if (entity.getUUID().equals(droneEntityUUID)) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public double getHealth() {
        return health;
    }

    @Override
    public void setHealth(double health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
    }

    @Override
    public double getMaxHealth() {
        return maxHealth;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void activate() {
        this.active = true;
    }

    @Override
    public void deactivate() {
        this.active = false;
    }

    /**
     * 获取攻击行为
     */
    public IDroneBehavior getBehavior() {
        return arrayType.getBehavior();
    }
    
    @Override
    public void tick() {
        // 行为逻辑已在 DroneConstructEntity 中实现
    }

    private void spawnDroneEntity() {
        Level level = owner.level();
        if (level.isClientSide) {
            return;
        }

        DroneConstructEntity drone = new DroneConstructEntity(level, owner.getUUID(), this);
        drone.setArrayType(arrayType);

        for (IDroneEffect effect : effects) {
            if (effect.getName().equals("突击")) {
                drone.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
            } else if (effect.getName().equals("防御")) {
                drone.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
            }
        }

        Vec3 spawnPos = owner.position().add(0, 2, 0);
        drone.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        
        // 设置生命值为最大生命值
        drone.setHealth(drone.getMaxHealth());

        level.addFreshEntity(drone);
        droneEntityUUID = drone.getUUID();

        // 注册到构造体管理器
        ConstructManager.getInstance().registerConstructEntity(owner.getUUID(), constructId, drone);
    }

    @Override
    public void onCreated() {
        spawnDroneEntity();
    }

    @Override
    public void onDestroyed() {
        if (droneEntityUUID != null && owner != null) {
            Level level = owner.level();
            AABB searchBox = new AABB(-1000, -1000, -1000, 1000, 1000, 1000);
            List<Entity> entities = level.getEntities(null, searchBox);
            for (Entity entity : entities) {
                if (entity.getUUID().equals(droneEntityUUID)) {
                    entity.discard();
                    break;
                }
            }
        }
    }

    @Override
    public boolean canBeCreated() {
        return owner != null && owner.isAlive();
    }

    @Override
    public void onBuildProgress(int progress, int total) {
    }

    public UUID getEntityUUID() {
        return droneEntityUUID;
    }

    public DroneArrayType getArrayType() {
        return arrayType;
    }

    public void setArrayType(DroneArrayType arrayType) {
        this.arrayType = arrayType;
    }

    public List<IDroneEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    public void addEffect(IDroneEffect effect) {
        this.effects.add(effect);
    }

    public boolean hasEffect(String effectName) {
        for (IDroneEffect effect : effects) {
            if (effect.getName().equals(effectName)) {
                return true;
            }
        }
        return false;
    }

    public LivingEntity getOwner() {
        return owner;
    }

    public boolean isAssaultDrone() {
        return hasEffect("突击");
    }

    public boolean isDefenseDrone() {
        return hasEffect("防御");
    }

    @Override
    public Set<String> getCurrentTags() {
        Set<String> allTags = new HashSet<>();
        ConstructType type = getConstructType();
        if (type != null) {
            allTags.addAll(type.getTags());
        }
        for (IDroneEffect effect : effects) {
            allTags.add(effect.getTagId());
        }
        if (commander) {
            allTags.add("commander");
        }
        return allTags;
    }

    public boolean isCommander() {
        return commander;
    }

    public void setCommander(boolean commander) {
        this.commander = commander;
    }
}
