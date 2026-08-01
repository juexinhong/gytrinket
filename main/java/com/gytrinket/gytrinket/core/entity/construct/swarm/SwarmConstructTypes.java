package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.entity.construct.ConstructCategory;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.core.entity.construct.IEntityRestorer;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * 蜂群构造体类型注册
 * <p>
 * 蜂群属于基础、其他、构造体类别。
 * 构建时有小概率提升等阶（标准/高阶），由 rollTier() 在构建时决定单实例的等阶。
 */
public class SwarmConstructTypes {
    public static final String SWARM = "swarm";

    /** 等阶：基础 */
    public static final int TIER_BASIC = 0;
    /** 等阶：标准 */
    public static final int TIER_STANDARD = 1;
    /** 等阶：高阶 */
    public static final int TIER_ADVANCED = 2;

    public static void register() {
        ConstructManager manager = ConstructManager.getInstance();

        manager.registerConstructType(ConstructType.builder(SWARM)
                .name("蜂群")
                .categories(ConstructCategory.createOtherCategories(ConstructCategory.Tier.BASIC))
                .tags(Set.of("swarm"))
                .buildTime(Config.getSwarmBuildTime())
                .maxHealth(Config.getSwarmBaseHealth())
                .maxCount(Config.getSwarmMaxCount())
                .constructFactory((player, type) -> {
                    int tier = rollTier();
                    return new SwarmConstruct(type.getId(), player, type.getMaxHealth(), tier);
                })
                .entityRestorer(new SwarmEntityRestorer())
                .build());
    }

    private static int rollTier() {
        double advancedChance = Config.getSwarmTierUpgradeChanceAdvanced();
        double standardChance = Config.getSwarmTierUpgradeChanceStandard();

        double r = Math.random();
        if (r < advancedChance) {
            return TIER_ADVANCED;
        }
        if (r < advancedChance + standardChance) {
            return TIER_STANDARD;
        }
        return TIER_BASIC;
    }

    private static class SwarmEntityRestorer implements IEntityRestorer {
        @Override
        public Entity restore(ServerPlayer player, ConstructData data, ServerLevel level) {
            if (!(data instanceof SwarmConstructData swarmData)) return null;

            SwarmConstructEntity swarmEntity = new SwarmConstructEntity(ModEntities.SWARM_CONSTRUCT.get(), level);

            String currentDimension = player.level().dimension().location().toString();
            if (swarmData.hasPosition() && swarmData.getDimension().equals(currentDimension)) {
                swarmEntity.setPos(swarmData.getPosX(), swarmData.getPosY(), swarmData.getPosZ());
            } else {
                swarmEntity.setPos(player.getX(), player.getY() + 1, player.getZ());
            }

            swarmEntity.setOwnerUUID(player.getUUID());
            swarmEntity.setTier(swarmData.getTier());
            swarmEntity.applyAttributeModifiers();

            float healthRatio = (float) swarmData.getHealthRatio();
            float newMaxHealth = swarmEntity.getMaxHealth();
            swarmEntity.setHealth(newMaxHealth * healthRatio);

            level.addFreshEntity(swarmEntity);
            swarmData.setEntityUUID(swarmEntity.getUUID());
            return swarmEntity;
        }
    }
}
