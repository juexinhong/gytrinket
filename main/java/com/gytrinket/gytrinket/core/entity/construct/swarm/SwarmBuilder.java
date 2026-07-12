package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.ConstructBuilder;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 蜂群构造体构建器
 * <p>
 * 扩展基础构建器。构建完成时随机决定单实例等阶：
 * 小概率升阶为标准，更小概率升阶为高阶。
 */
public class SwarmBuilder extends ConstructBuilder {

    public SwarmBuilder(Player player, ConstructType constructType) {
        super(player, constructType);
    }

    /**
     * 蜂群构建时间按溢出倍率延长：当蜂群数量超过极限值时，
     * 每只蜂群的等效战力更高，构建时间也需相应延长以保持平衡。
     */
    @Override
    protected int getEffectiveBuildTime() {
        int baseTime = getConstructType().getBuildTime();
        double overflowMult = MothershipManager.getOverflowMultiplier(getPlayer().getUUID());
        return (int) Math.ceil(baseTime * overflowMult);
    }

    @Override
    protected void onBuildComplete() {
        // 随机决定等阶
        int tier = rollTier();

        UUID entityUUID = UUID.randomUUID();

        SwarmConstructData data = new SwarmConstructData(
                getConstructType().getId(),
                entityUUID,
                getConstructType().getMaxHealth()
        );
        data.setTier(tier);

        ConstructManager.getInstance().addConstruct(getPlayer(), data);

        SwarmConstruct swarmConstruct = new SwarmConstruct(
                getConstructType().getId(),
                getPlayer(),
                getConstructType().getMaxHealth(),
                tier
        );
        swarmConstruct.onCreated();

        if (swarmConstruct.getEntityUUID() != null) {
            data.setEntityUUID(swarmConstruct.getEntityUUID());
        }
    }

    /**
     * 随机决定等阶：
     * - 先判高阶概率（独立）
     * - 否则判标准概率
     * - 否则基础
     */
    private int rollTier() {
        double advancedChance = Config.getSwarmTierUpgradeChanceAdvanced();
        double standardChance = Config.getSwarmTierUpgradeChanceStandard();

        double r = Math.random();
        if (r < advancedChance) {
            return SwarmConstructTypes.TIER_ADVANCED;
        }
        if (r < advancedChance + standardChance) {
            return SwarmConstructTypes.TIER_STANDARD;
        }
        return SwarmConstructTypes.TIER_BASIC;
    }
}
