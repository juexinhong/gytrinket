package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.core.entity.construct.ConstructBuilder;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.core.entity.construct.IConstruct;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 蜂群实例构建器
 * <p>
 * 实例化机制专用：每个来源物品（母舰机身，实例）一个构建器，构建时间与产出的
 * 蜂群构建体都使用该物品定义的实例参数。保留溢出倍率对构建速度的修正
 * （与 {@link SwarmBuilder} 一致）。
 * 通过 {@link com.gytrinket.gytrinket.core.entity.construct.ConstructManager#storageKey}
 * 以 "swarm|&lt;物品ID&gt;" 为存储键注册，各实例独立构建互不干扰。
 */
public class SwarmInstanceBuilder extends ConstructBuilder {
    private final String instanceKey;
    private final int buildTime;

    public SwarmInstanceBuilder(Player player, ConstructType constructType, String instanceKey) {
        super(player, constructType);
        this.instanceKey = instanceKey;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        this.buildTime = SwarmInstanceParams.getBuildTime(server, instanceKey);
    }

    /** 实例键（来源物品 ID） */
    public String getInstanceKey() {
        return instanceKey;
    }

    /** 构建时间使用物品级参数（而非类型级默认） */
    @Override
    protected int getEffectiveBuildTime() {
        return buildTime;
    }

    /** 溢出倍率对构建速度的修正（与 SwarmBuilder 一致） */
    @Override
    protected void updateBuildSpeed() {
        super.updateBuildSpeed();
        double overflowMult = MothershipManager.getOverflowMultiplier(getPlayer().getUUID());
        if (overflowMult > 1.0) {
            setBuildSpeedMultiplier(getBuildSpeedMultiplier() / overflowMult);
        }
    }

    /** 产出带实例键的蜂群构建体 */
    @Override
    protected IConstruct createConstruct() {
        return SwarmConstructTypes.createSwarmConstruct(getPlayer(), getConstructType(), instanceKey);
    }
}
