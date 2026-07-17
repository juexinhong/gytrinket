package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructCategory;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IEntityRestorer;
import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.AssaultEffect;
import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.DefenseEffect;
import com.gy_mod.gy_trinket.core.entity.construct.drone.effect.IDroneEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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
                .constructFactory((player, type) -> {
                    DroneArrayType arrayType = DroneArrayManager.getInstance().getPlayerArrayType(player);
                    boolean hasAssault = DroneManager.getInstance().hasAssaultModule(player);
                    boolean hasDefense = DroneManager.getInstance().hasDefenseModule(player);

                    java.util.List<IDroneEffect> effects = new java.util.ArrayList<>();
                    if (hasAssault) effects.add(new AssaultEffect());
                    if (hasDefense) effects.add(new DefenseEffect());

                    return new DroneConstruct(type.getId(), arrayType, effects, player, type.getMaxHealth());
                })
                .entityRestorer(new DroneEntityRestorer())
                .build());
    }

    /**
     * 无人机实体恢复器：从持久化数据中恢复无人机实体
     */
    private static class DroneEntityRestorer implements IEntityRestorer {
        @Override
        public Entity restore(ServerPlayer player, ConstructData data, ServerLevel level) {
            if (!(data instanceof DroneConstructData droneData)) return null;

            DroneArrayType arrayType = DroneArrayManager.getInstance().getPlayerArrayType(player);
            if (arrayType == null) arrayType = DroneArrayType.Types.ORBIT;

            DroneConstructEntity droneEntity = new DroneConstructEntity(ModEntities.DRONE_CONSTRUCT.get(), level);

            // 恢复位置
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
            if (!droneData.hasAssaultModule() && !droneData.hasDefenseModule()) {
                droneEntity.refreshConstructAttributes();
            }

            // 属性修饰器应用完毕后，用保存的生命值比例恢复当前生命值
            float healthRatio = (float) droneData.getHealthRatio();
            float newMaxHealth = droneEntity.getMaxHealth();
            droneEntity.setHealth(newMaxHealth * healthRatio);

            level.addFreshEntity(droneEntity);
            droneData.setEntityUUID(droneEntity.getUUID());
            return droneEntity;
        }
    }
}
