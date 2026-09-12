package com.gy_mod.gy_trinket.client.shield.type;

import com.gy_mod.gy_trinket.network.packet.SyncShieldMessage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增幅护盾客户端显示数据：按物品实例分桶，
 * 多个增幅实例各自独立插值与渲染贴图
 * <p>
 * 由服务端同步的增幅进度（0~1）驱动：
 * - 仅在检测到危险物（进度>0）时渲染
 * - 亮度基础为8，随进度线性提升至15
 * - 透明度与亮度均平滑插值（淡入淡出）
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID, value = Dist.CLIENT)
public class AmplifierClientData {

    /** 亮度范围：基础8（无危险物）~ 15（达到增幅上限） */
    private static final double BRIGHTNESS_BASE = 8.0;
    private static final double BRIGHTNESS_MAX = 15.0;

    private static final int FADE_OUT_DURATION = 20;

    // 超时机制：如果超过此tick数未收到progress>0的同步包，自动认为无危险物
    private static final int PROGRESS_CONFIRM_TIMEOUT = 10;

    private static final double ALPHA_LERP_SPEED = 0.15;
    private static final double SIZE_LERP_SPEED = 0.15;
    private static final double PROGRESS_LERP_SPEED = 0.15;

    /** 单个物品实例的显示状态 */
    public static class State {
        public final String itemId;
        /** 最近一次同步中该实例是否存在（不存在时进度归零并回收） */
        public boolean active = false;
        public double targetProgress = 0;
        public double effectiveRadius = 1.0;
        public double displayProgress = 0;
        public double displayAlpha = 0;
        public double displaySize = 0;
        public int fadeOutTicks = 0;
        public int progressConfirmTicks = 0;

        State(String itemId) {
            this.itemId = itemId;
        }

        /** 当前渲染亮度（8~15） */
        public double getDisplayBrightness() {
            return BRIGHTNESS_BASE + displayProgress * (BRIGHTNESS_MAX - BRIGHTNESS_BASE);
        }
    }

    private static final Map<String, State> STATES = new HashMap<>();

    private AmplifierClientData() {}

    /** 同步：护盾类型状态按物品实例分桶更新（有效半径由服务端按实例下发） */
    public static void syncStates(List<SyncShieldMessage.ItemShieldState> states) {
        for (State s : STATES.values()) {
            s.active = false;
        }
        for (SyncShieldMessage.ItemShieldState item : states) {
            State s = STATES.computeIfAbsent(item.itemId, AmplifierClientData.State::new);
            s.active = true;
            s.targetProgress = Math.max(0.0, Math.min(1.0, item.amplificationProgress));
            s.effectiveRadius = item.amplificationRadius;
            if (s.targetProgress > 0) {
                s.fadeOutTicks = 0;
                s.progressConfirmTicks = 0;
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 回收：同步列表中已消失且淡出完毕的实例
        STATES.values().removeIf(s -> !s.active && s.displayAlpha <= 0.001);

        for (State s : STATES.values()) {
            if (!s.active) {
                s.targetProgress = 0;
            }

            // 超时检测：progress>0但长时间未收到确认包，自动设为0（视为无危险物）
            if (s.targetProgress > 0) {
                s.progressConfirmTicks++;
                if (s.progressConfirmTicks >= PROGRESS_CONFIRM_TIMEOUT) {
                    s.targetProgress = 0;
                }
            }

            // 进度插值（驱动亮度）
            double progressDiff = s.targetProgress - s.displayProgress;
            if (Math.abs(progressDiff) > 0.001) {
                s.displayProgress += progressDiff * PROGRESS_LERP_SPEED;
                if (Math.abs(s.targetProgress - s.displayProgress) < 0.001) {
                    s.displayProgress = s.targetProgress;
                }
            } else {
                s.displayProgress = s.targetProgress;
            }

            // 透明度：有危险物（进度>0）时淡入，无危险物时淡出
            double targetAlpha;
            if (s.displayProgress > 0.001) {
                s.fadeOutTicks = 0;
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
