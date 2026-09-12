package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructCategory;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IConstructFactory;
import com.gy_mod.gy_trinket.core.entity.construct.IEntityRestorer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.IDroneEffect;
import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.AssaultEffect;
import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.DefenseEffect;

import java.util.Set;

public class DroneConstructTypes {
    public static final String DRONE = "drone";

    public static void register() {
        ConstructManager manager = ConstructManager.getInstance();

        manager.registerConstructType(ConstructType.builder(DRONE)
                .name("无人机")
                .categories(ConstructCategory.createOtherCategories(ConstructCategory.Tier.STANDARD))
                .tags(Set.of("drone"))
                .buildTime(100)
                .maxHealth(Config.getDroneBaseHealth())
                .maxCount(Config.getDroneMaxCount())
                .constructFactory((player, type) -> createDroneConstruct(player, null))
                .entityRestorer(new DroneEntityRestorer())
                .build());
    }

    /**
     * 创建无人机构建体（类型工厂与实例构建器共用）。
     *
     * @param player      玩家
     * @param instanceKey 实例键（来源物品 ID）；null 表示非实例化路径
     */
    public static DroneConstruct createDroneConstruct(net.minecraft.world.entity.player.Player player,
                                                      @javax.annotation.Nullable String instanceKey) {
        ConstructType type = ConstructManager.getInstance().getConstructType(DRONE);
        DroneArrayType arrayType = DroneArrayManager.getInstance().getPlayerArrayType(player);
        boolean hasAssault = DroneManager.getInstance().hasAssaultModule(player);
        boolean hasDefense = DroneManager.getInstance().hasDefenseModule(player);

        java.util.List<IDroneEffect> effects = new java.util.ArrayList<>();
        if (hasAssault) effects.add(new AssaultEffect());
        if (hasDefense) effects.add(new DefenseEffect());

        double maxHealth = type != null ? type.getMaxHealth() : Config.getDroneBaseHealth();
        return new DroneConstruct(DRONE, arrayType, effects, player, maxHealth, instanceKey);
    }

    private static class DroneEntityRestorer implements IEntityRestorer {
        @Override
        public Entity restore(ServerPlayer player, ConstructData data, ServerLevel level) {
            if (!(data instanceof DroneConstructData droneData)) return null;

            // 实例化改造：旧存档无实例键的无人机不恢复，
            // 交给 TickScheduler 的实例构建循环按当前装备的实例物品自动补建
            String instanceKey = droneData.getInstanceKey();
            if (instanceKey == null) return null;

            DroneArrayType arrayType = DroneArrayManager.getInstance().getPlayerArrayType(player);
            if (arrayType == null) arrayType = DroneArrayType.Types.ORBIT;

            DroneConstructEntity droneEntity = new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), level);
            droneEntity.setInstanceKey(instanceKey);

            String currentDimension = player.level().dimension().location().toString();
            if (droneData.hasPosition() && droneData.getDimension().equals(currentDimension)) {
                droneEntity.setPos(droneData.getPosX(), droneData.getPosY(), droneData.getPosZ());
            } else {
                droneEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            }

            droneEntity.setOwnerUUID(player.getUUID());
            droneEntity.setArrayType(arrayType);
            droneEntity.setBaseMaxHealth(droneData.getMaxHealth());
            droneEntity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(droneData.getMaxHealth());

            if (droneData.hasAssaultModule()) {
                droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.ASSAULT);
            }
            if (droneData.hasDefenseModule()) {
                droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.DEFENSE);
            }
            // 指挥官标记随存档恢复：addEffectTag 内部会触发属性刷新，
            // 使指挥官加成（生命/伤害等）在重登后立即生效，无需等待重新任命
            if (droneData.hasCommander()) {
                droneEntity.addEffectTag(DroneConstructEntity.DroneEffectTag.COMMANDER);
            }

            // 实例化无人机：基础生命/伤害等参数由实例参数动态解析，
            // 恢复后立即刷新属性使当前实例参数生效
            droneEntity.refreshConstructAttributes();

            float healthRatio = (float) droneData.getHealthRatio();
            float newMaxHealth = droneEntity.getMaxHealth();
            droneEntity.setHealth(newMaxHealth * healthRatio);

            level.addFreshEntity(droneEntity);
            droneData.setEntityUUID(droneEntity.getUUID());
            return droneEntity;
        }
    }
}

