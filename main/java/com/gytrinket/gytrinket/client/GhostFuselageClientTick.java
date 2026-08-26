package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.packet.SyncGhostMoveSpeedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 幽灵机身客户端tick：计算移动隐身消耗量并发送到服务端
 * <p>
 * 服务端无法准确获取玩家移动速度（位置由客户端包驱动），
 * 因此由客户端每tick计算真实位移 → 消耗量，仅值变化时发包以减少网络压力。
 * <p>
 * 移速判定基于指数平滑（EMA）后的速度：单tick位移的瞬时尖峰会被压平，
 * 普通移动即使瞬时越界也不会触发高速状态；持续冲刺才会稳定上穿阈值。
 * EMA的天然滞后起到去抖作用，进出高速状态不会在阈值附近反复横跳。
 * 移动消耗量的上报与隐身进度解耦：即使进度为0，只要玩家仍高速移动就持续上报，
 * 让服务端保持"高速移动中"状态，阻止隐身进度在高速移动期间错误累积。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class GhostFuselageClientTick {

    /** 噪声阈值：低于此速度视为静止（过滤浮点误差和微小抖动） */
    private static final double NOISE_THRESHOLD = 0.005;

    /** 移速平滑系数（EMA）：过滤单tick位移尖峰，避免阈值附近的抖动反复触发高速状态 */
    private static final double SPEED_SMOOTHING_ALPHA = 0.1;

    /** 高速移动状态下的最小消耗值：保证服务端持续收到>0的移动消耗信号 */
    private static final float MIN_MOVE_REDUCTION = 0.0005f;

    /** 发包间隔（每5tick发包一次，降低网络压力） */
    private static final int SEND_INTERVAL = 5;

    /** 上次发送的消耗值，用于去重 */
    private static float lastSentReduction = -1f;

    /** tick计数器，用于控制发包频率 */
    private static int tickCounter = 0;

    /** 当前是否处于高速移动状态（与隐身进度无关） */
    private static boolean movingFast = false;

    /** 平滑后的移速（EMA，用于高速状态判定） */
    private static double smoothedSpeed = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            lastSentReduction = -1f;
            tickCounter = 0;
            movingFast = false;
            return;
        }

        // 用实际位移计算三维速度（客户端position-oldPosition是准确的）
        double dx = player.getX() - player.xOld;
        double dy = player.getY() - player.yOld;
        double dz = player.getZ() - player.zOld;
        double moveSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 计算移动消耗量（含平滑状态判定）
        float moveReduction = computeMoveReduction(moveSpeed);

        // 每5tick发包一次（降低网络压力）
        tickCounter++;
        if (tickCounter >= SEND_INTERVAL) {
            tickCounter = 0;
            // 仅值变化时发包（进一步减少网络压力）
            if (Math.abs(moveReduction - lastSentReduction) > 0.0001f) {
                PacketDistributor.sendToServer(new SyncGhostMoveSpeedPayload(moveReduction));
                lastSentReduction = moveReduction;
            }
        }
    }

    /**
     * 计算移动隐身消耗量
     * <p>
     * 先对单tick位移做EMA平滑，再以平滑速度与阈值比较判定高速状态：
     * 瞬时尖峰不会触发，持续高速才会稳定进入；EMA天然滞后即去抖，无需额外滞回带。
     * 处于高速状态时消耗量 = max(原始超出部分×系数, 最小消耗)，保证服务端持续收到>0信号。
     */
    private static float computeMoveReduction(double moveSpeed) {
        // EMA平滑（静止时平滑值归零，避免残留旧速度）
        smoothedSpeed = smoothedSpeed + SPEED_SMOOTHING_ALPHA * (moveSpeed - smoothedSpeed);

        if (moveSpeed <= NOISE_THRESHOLD) {
            smoothedSpeed = 0;
            movingFast = false;
            return 0f;
        }

        double threshold = Config.getGhostFuselageMoveSpeedThreshold();
        if (movingFast) {
            // 高速移动中：平滑速度降至阈值以下才退出
            if (smoothedSpeed < threshold) {
                movingFast = false;
            }
        } else {
            // 未高速移动：平滑速度超过阈值才进入
            if (smoothedSpeed > threshold) {
                movingFast = true;
            }
        }

        if (!movingFast) {
            return 0f;
        }

        // 消耗量按原始速度计算（更贴近真实移速），阈值以下时取最小消耗保持信号
        double excessSpeed = Math.max(moveSpeed - threshold, 0);
        double reduction = excessSpeed * Config.getGhostFuselageMoveSpeedReduction();
        return (float) Math.max(reduction, MIN_MOVE_REDUCTION);
    }
}
