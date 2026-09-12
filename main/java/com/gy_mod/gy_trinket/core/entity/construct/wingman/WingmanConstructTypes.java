package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.config.Config;
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
                .constructFactory((player, type) -> createWingmanConstruct(player, type, null))
                .entityRestorer(new WingmanEntityRestorer())
                .build());
    }

    /**
     * 创建僚机构建体（类型工厂与实例构建器共用）。
     *
     * @param player      玩家
     * @param type        僚机构造体类型（可为 null，内部回退 Config 基础生命）
     * @param instanceKey 实例键（来源物品 ID）；null 表示非实例化路径
     */
    public static WingmanConstruct createWingmanConstruct(net.minecraft.world.entity.player.Player player,
                                                          @javax.annotation.Nullable ConstructType type,
                                                          @javax.annotation.Nullable String instanceKey) {
        double maxHealth = type != null ? type.getMaxHealth() : Config.getWingmanBaseHealth();
        return new WingmanConstruct(WINGMAN, player, maxHealth, instanceKey);
    }

    private static class WingmanEntityRestorer implements IEntityRestorer {
        @Override
        public Entity restore(ServerPlayer player, ConstructData data, ServerLevel level) {
            if (!(data instanceof WingmanConstructData wingmanData)) return null;

            // 实例化改造：旧存档无实例键的僚机不恢复，
            // 交给 TickScheduler 的实例构建循环按当前装备的实例物品自动补建
            String instanceKey = wingmanData.getInstanceKey();
            if (instanceKey == null) return null;

            WingmanConstructEntity wingmanEntity = new WingmanConstructEntity(ModEntities.WINGMAN_CONSTRUCT.get(), level);

            String currentDimension = player.level().dimension().location().toString();
            if (wingmanData.hasPosition() && wingmanData.getDimension().equals(currentDimension)) {
                wingmanEntity.setPos(wingmanData.getPosX(), wingmanData.getPosY(), wingmanData.getPosZ());
            } else {
                wingmanEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            }

            wingmanEntity.setOwnerUUID(player.getUUID());
            wingmanEntity.setInstanceKey(instanceKey);
            // 恢复后立即按实例参数刷新属性（基础生命/伤害等物品级定义生效）
            wingmanEntity.refreshConstructAttributes();

            float healthRatio = (float) wingmanData.getHealthRatio();
            float newMaxHealth = wingmanEntity.getMaxHealth();
            wingmanEntity.setHealth(newMaxHealth * healthRatio);

            level.addFreshEntity(wingmanEntity);
            wingmanData.setEntityUUID(wingmanEntity.getUUID());
            return wingmanEntity;
        }
    }
}

