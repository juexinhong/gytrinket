package com.gy_mod.gy_trinket.core;

import com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.CommanderManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.FormationBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanManager;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.function.Consumer;

public class TickScheduler {
    private static final Map<String, ScheduledTask> TASKS = new HashMap<>();
    private static long currentTick = 0;
    private static long lastCleanupTick = 0;

    private static class ScheduledTask {
        final int intervalTicks;
        final Consumer<Long> action;
        long lastRunTick = -1;

        ScheduledTask(int intervalTicks, Consumer<Long> action) {
            this.intervalTicks = intervalTicks;
            this.action = action;
        }

        boolean shouldRun(long tick) {
            if (lastRunTick < 0 || tick - lastRunTick >= intervalTicks) {
                return true;
            }
            return false;
        }

        void run(long tick) {
            lastRunTick = tick;
            action.accept(tick);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            currentTick++;

            for (ScheduledTask task : TASKS.values()) {
                if (task.shouldRun(currentTick)) {
                    task.run(currentTick);
                }
            }
            
            ElectricDischargeManager.tick();
            
            // 处理无人机构建逻辑
            if (EffectiveSide.get() == LogicalSide.SERVER) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        // 1. 更新构造体构建进度
                        ConstructManager.getInstance().tick(player);
                        
                        // 2. 检查是否需要继续尝试构建无人机
                        if (DroneManager.getInstance().canBuildDroneInternal(player)) {
                            if (!ConstructManager.getInstance().isBuilding(player, DroneConstructTypes.DRONE)) {
                                if (ConstructManager.getInstance().canCreateConstruct(player, DroneConstructTypes.DRONE)) {
                                    // 没有正在构建，且还可以创建，继续尝试构建
                                    DroneManager.getInstance().startBuildingDrone(player);
                                }
                            }
                        }

                        // 3. 检查是否需要继续尝试构建僚机
                        if (WingmanManager.getInstance().canBuildWingmanInternal(player)) {
                            if (!ConstructManager.getInstance().isBuilding(player, WingmanConstructTypes.WINGMAN)) {
                                if (ConstructManager.getInstance().canCreateConstruct(player, WingmanConstructTypes.WINGMAN)) {
                                    WingmanManager.getInstance().startBuildingWingman(player);
                                }
                            }
                        }

                        // 4. 检查是否需要继续尝试构建蜂群
                        if (SwarmManager.getInstance().canBuildSwarmInternal(player)) {
                            if (!ConstructManager.getInstance().isBuilding(player, SwarmConstructTypes.SWARM)) {
                                if (ConstructManager.getInstance().canCreateConstruct(player, SwarmConstructTypes.SWARM)) {
                                    SwarmManager.getInstance().startBuildingSwarm(player);
                                }
                            }
                        }

                        // 5. 指挥官任命逻辑
                        CommanderManager.getInstance().tick(player);
                    }
                }
            }

            // 重置列队阵列的攻击传递状态
            FormationBehavior.resetTickState();
        }
    }

    public static void register(String name, int intervalTicks, Runnable task) {
        TASKS.put(name, new ScheduledTask(intervalTicks, tick -> task.run()));
    }

    public static void register(String name, int intervalTicks, Consumer<Long> task) {
        TASKS.put(name, new ScheduledTask(intervalTicks, task));
    }

    public static void unregister(String name) {
        TASKS.remove(name);
    }

    public static void clear() {
        TASKS.clear();
    }

    public static long getCurrentTick() {
        return currentTick;
    }
}