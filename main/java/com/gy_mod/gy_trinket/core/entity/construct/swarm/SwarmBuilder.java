package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructBuilder;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import net.minecraft.world.entity.player.Player;

/**
 * 蜂群构造体构建器
 * <p>
 * 扩展基础构建器，添加溢出倍率对构建速度的修正：
 * 当蜂群数量超过极限值时，构建速度降低（等效延长构建时间）。
 */
public class SwarmBuilder extends ConstructBuilder {

    public SwarmBuilder(Player player, ConstructType constructType) {
        super(player, constructType);
    }

    @Override
    protected void updateBuildSpeed() {
        super.updateBuildSpeed();
        double overflowMult = MothershipManager.getOverflowMultiplier(getPlayer().getUUID());
        if (overflowMult > 1.0) {
            setBuildSpeedMultiplier(getBuildSpeedMultiplier() / overflowMult);
        }
    }
}
