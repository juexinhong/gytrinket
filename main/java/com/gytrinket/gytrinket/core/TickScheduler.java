package com.gytrinket.gytrinket.core;

import com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.CommanderManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.FormationBehavior;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanManager;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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
    public static void onServerTick(ServerTickEvent.Post event) {
            currentTick++;

            // 创建快照遍历，避免 task 执行期间调用 register() 修改 TASKS 导致 ConcurrentModificationException
            for (ScheduledTask task : new ArrayList<>(TASKS.values())) {
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
                        
                        // 2. 驱动无人机实例构建循环（每实例物品独立构建，参数为物品级）
                        DroneManager.getInstance().tickBuilds(player);

                        // 3. 驱动僚机实例构建循环（每实例物品独立构建，参数为物品级）
                        WingmanManager.getInstance().tickBuilds(player);

                        // 4. 驱动蜂群实例构建循环（每实例物品独立构建，参数为物品级）
                        SwarmManager.getInstance().tickBuilds(player);

                        // 5. 指挥官任命逻辑
                        CommanderManager.getInstance().tick(player);
                    }
                }
            }

            // 重置列队阵列的攻击传递状态
            FormationBehavior.resetTickState();
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