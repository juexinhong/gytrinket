package com.gy_mod.gy_trinket.client.shield.type;

import com.gy_mod.gy_trinket.network.packet.SyncShieldMessage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 虹吸护盾客户端显示数据：按物品实例分桶，
 * 多个虹吸实例各自独立插值与渲染贴图
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class SiphonClientData {

    /** 单个物品实例的显示状态 */
    public static class State {
        public final String itemId;
        /** 最近一次同步中该实例是否存在（不存在时层数归零并回收） */
        public boolean active = false;
        public int targetStacks = 0;
        public double effectiveRadius = 1.0;
        public double displayStacks = 0;
        public double displayAlpha = 0;
        public double displaySize = 0;

        State(String itemId) {
            this.itemId = itemId;
        }
    }

    private static final Map<String, State> STATES = new HashMap<>();

    private static final double LERP_SPEED = 0.15;

    /** 共享：护盾转移保护实体（渲染位置用，与具体实例无关） */
    private static int[] protectedEntityIds = new int[0];

    private SiphonClientData() {}

    public static void setProtectedEntityIds(int[] ids) {
        protectedEntityIds = ids != null ? ids : new int[0];
    }

    public static int[] getProtectedEntityIds() {
        return protectedEntityIds;
    }

    /** 同步：护盾类型状态按物品实例分桶更新（有效半径由服务端按实例下发） */
    public static void syncStates(List<SyncShieldMessage.ItemShieldState> states) {
        for (State s : STATES.values()) {
            s.active = false;
        }
        for (SyncShieldMessage.ItemShieldState item : states) {
            State s = STATES.computeIfAbsent(item.itemId, SiphonClientData.State::new);
            s.active = true;
            s.targetStacks = item.siphonStacks;
            s.effectiveRadius = item.siphonRadius;
        }
    }

    /** 当前可渲染的实例列表（透明度大于阈值） */
    public static List<State> getRenderStates() {
        List<State> result = new ArrayList<>();
        for (State s : STATES.values()) {
            if (s.displayAlpha > 0.001) {
                result.add(s);
            }
        }
        return result;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 回收：同步列表中已消失且层数归零的实例
        STATES.values().removeIf(s -> !s.active && s.displayStacks <= 0.01);

        for (State s : STATES.values()) {
            if (!s.active) {
                s.targetStacks = 0;
            }

            double diff = s.targetStacks - s.displayStacks;
            if (Math.abs(diff) > 0.01) {
                s.displayStacks += diff * LERP_SPEED;
                if (Math.abs(s.targetStacks - s.displayStacks) < 0.01) {
                    s.displayStacks = s.targetStacks;
                }
            } else {
                s.displayStacks = s.targetStacks;
            }

            if (s.displayStacks <= 0) {
                s.displayAlpha = 0;
                s.displaySize = 0;
                continue;
            }

            double baseAlpha = 0.05;
            double alphaPerStack = 0.10;
            double targetAlpha = baseAlpha + s.displayStacks * alphaPerStack;
            targetAlpha = Math.min(targetAlpha, 1.0);

            double alphaDiff = targetAlpha - s.displayAlpha;
            if (Math.abs(alphaDiff) > 0.001) {
                s.displayAlpha += alphaDiff * LERP_SPEED;
                if (Math.abs(targetAlpha - s.displayAlpha) < 0.001) {
                    s.displayAlpha = targetAlpha;
                }
            } else {
                s.displayAlpha = targetAlpha;
            }

            double targetSize = s.effectiveRadius * 2.0 * (4.0 / 3.0); // 补偿材质内容缩小至3/4

            double sizeDiff = targetSize - s.displaySize;
            if (Math.abs(sizeDiff) > 0.01) {
                s.displaySize += sizeDiff * LERP_SPEED;
                if (Math.abs(targetSize - s.displaySize) < 0.01) {
                    s.displaySize = targetSize;
                }
            } else {
                s.displaySize = targetSize;
            }
        }
    }

    public static void reset() {
        STATES.clear();
        protectedEntityIds = new int[0];
    }
}
