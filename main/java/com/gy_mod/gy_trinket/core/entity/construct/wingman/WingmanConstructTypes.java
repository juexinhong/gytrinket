package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructCategory;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IEntityRestorer;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * 僚机构造体类型注册
 */
public class WingmanConstructTypes {
    public static final String WINGMAN = "wingman";

    public static void register() {
        ConstructManager manager = ConstructManager.getInstance();

        manager.registerConstructType(ConstructType.builder(WINGMAN)
                .name("僚机")
                .categories(ConstructCategory.createOtherCategories(ConstructCategory.Tier.ADVANCED))
                .tags(Set.of("wingman"))
                .buildTime(500) // 25秒 = 500tick
                .maxHealth(Config.getWingmanBaseHealth())
                .maxCount(Config.getWingmanMaxCount())
                .constructFactory((player, type) ->
                        new WingmanConstruct(type.getId(), player, type.getMaxHealth()))
                .entityRestorer(new WingmanEntityRestorer())
                .build());
    }

    /**
     * 僚机实体恢复器：从持久化数据中恢复僚机实体
     */
    private static class WingmanEntityRestorer implements IEntityRestorer {
        @Override
        public Entity restore(ServerPlayer player, ConstructData data, ServerLevel level) {
            if (!(data instanceof WingmanConstructData wingmanData)) return null;

            WingmanConstructEntity wingmanEntity = new WingmanConstructEntity(ModEntities.WINGMAN_CONSTRUCT.get(), level);

            // 恢复位置
            String currentDimension = player.level().dimension().location().toString();
            if (wingmanData.hasPosition() && wingmanData.getDimension().equals(currentDimension)) {
                wingmanEntity.setPos(wingmanData.getPosX(), wingmanData.getPosY(), wingmanData.getPosZ());
            } else {
                wingmanEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            }

            wingmanEntity.setOwnerUUID(player.getUUID());

            // 恢复生命值比例
            float healthRatio = (float) wingmanData.getHealthRatio();
            float newMaxHealth = wingmanEntity.getMaxHealth();
            wingmanEntity.setHealth(newMaxHealth * healthRatio);

            level.addFreshEntity(wingmanEntity);
            wingmanData.setEntityUUID(wingmanEntity.getUUID());
            return wingmanEntity;
        }
    }
}
