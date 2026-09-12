package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructBuilder;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IConstruct;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

/**
 * 无人机实例构建器
 * <p>
 * 实例化机制专用：每个来源物品（实例）一个构建器，构建时间与产出的
 * 无人机构建体都使用该物品定义的实例参数。
 * 通过 {@link com.gy_mod.gy_trinket.core.entity.construct.ConstructManager#storageKey} 以 "drone|&lt;物品ID&gt;" 为存储键注册，
 * 各实例独立构建互不干扰。
 */
public class DroneInstanceBuilder extends ConstructBuilder {
    private final String instanceKey;
    private final int buildTime;

    public DroneInstanceBuilder(Player player, ConstructType constructType, String instanceKey) {
        super(player, constructType);
        this.instanceKey = instanceKey;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        this.buildTime = DroneInstanceParams.getBuildTime(server, instanceKey);
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

    /** 产出带实例键的无人机构建体 */
    @Override
    protected IConstruct createConstruct() {
        return DroneConstructTypes.createDroneConstruct(getPlayer(), instanceKey);
    }
}
