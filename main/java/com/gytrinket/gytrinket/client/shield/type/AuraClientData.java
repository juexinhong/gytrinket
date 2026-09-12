package com.gytrinket.gytrinket.client.shield.type;

import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.packet.SyncShieldPayload;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 光环护盾客户端显示数据：按物品实例分桶，
 * 多个光环实例各自独立插值与渲染贴图
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID, value = Dist.CLIENT)
public class AuraClientData {

    /** 单个物品实例的显示状态 */
    public static class State {
        public final String itemId;
        /** 最近一次同步中该实例是否存在（不存在时走淡出并回收） */
        public boolean active = false;
        public boolean damaging = false;
        public double effectiveRadius = 1.0;
        public double displayAlpha = 0;
        public double displaySize = 0;
        public int fadeOutTicks = 0;
        public int damagingConfirmTicks = 0;

        State(String itemId) {
            this.itemId = itemId;
        }
    }

    private static final Map<String, State> STATES = new HashMap<>();

    private static final int FADE_OUT_DURATION = 20;

    // 超时机制：如果超过此tick数未收到damaging=true的同步包，自动认为停止伤害
    private static final int DAMAGING_CONFIRM_TIMEOUT = 10;

    private static final double ALPHA_LERP_SPEED = 0.15;
    private static final double SIZE_LERP_SPEED = 0.15;

    private AuraClientData() {}

    /** 同步：护盾类型状态按物品实例分桶更新（有效半径由服务端按实例下发） */
    public static void syncStates(List<SyncShieldPayload.ItemShieldState> states) {
        for (State s : STATES.values()) {
            s.active = false;
        }
        for (SyncShieldPayload.ItemShieldState item : states) {
            State s = STATES.computeIfAbsent(item.itemId, AuraClientData.State::new);
            s.active = true;
            s.damaging = item.auraDamaging;
            s.effectiveRadius = item.auraRadius;
            if (s.damaging) {
                s.fadeOutTicks = 0;
                s.damagingConfirmTicks = 0;
            }
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
    public static void onClientTick(ClientTickEvent.Post event) {
        // 回收：同步列表中已消失且淡出完毕的实例
        STATES.values().removeIf(s -> !s.active && s.displayAlpha <= 0.001);

        for (State s : STATES.values()) {
            if (!s.active) {
                s.damaging = false;
            }

            // 超时检测：damaging为true但长时间未收到确认包，自动设为false
            if (s.damaging) {
                s.damagingConfirmTicks++;
                if (s.damagingConfirmTicks >= DAMAGING_CONFIRM_TIMEOUT) {
                    s.damaging = false;
                }
            }

            double targetAlpha;
            if (s.damaging) {
                targetAlpha = 1.0;
            } else {
                s.fadeOutTicks++;
                if (s.fadeOutTicks >= FADE_OUT_DURATION) {
                    targetAlpha = 0.0;
                } else {
                    targetAlpha = 1.0 - (double) s.fadeOutTicks / FADE_OUT_DURATION;
                }
            }

            double alphaDiff = targetAlpha - s.displayAlpha;
            if (Math.abs(alphaDiff) > 0.001) {
                s.displayAlpha += alphaDiff * ALPHA_LERP_SPEED;
                if (Math.abs(targetAlpha - s.displayAlpha) < 0.001) {
                    s.displayAlpha = targetAlpha;
                }
            } else {
                s.displayAlpha = targetAlpha;
            }

            double targetSize = s.effectiveRadius * 2.0 * (4.0 / 2.8); // 补偿材质内容缩小至3/4

            double sizeDiff = targetSize - s.displaySize;
            if (Math.abs(sizeDiff) > 0.01) {
                s.displaySize += sizeDiff * SIZE_LERP_SPEED;
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
    }
}
