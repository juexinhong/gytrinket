package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.ghost_fuselage.GhostFuselageClientData;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.SyncGhostMoveSpeedMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 幽灵机身客户端tick：计算移动隐身消耗量并发送到服务端
 * <p>
 * 服务端无法准确获取玩家移动速度（位置由客户端包驱动），
 * 因此由客户端每tick计算真实位移 → 消耗量，仅值变化时发包以减少网络压力。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GhostFuselageClientTick {

    /** 噪声阈值：低于此速度视为静止（过滤浮点误差和微小抖动） */
    private static final double NOISE_THRESHOLD = 0.005;

    /** 发包间隔（每5tick发包一次，降低网络压力） */
    private static final int SEND_INTERVAL = 5;

    /** 上次发送的消耗值，用于去重 */
    private static float lastSentReduction = -1f;

    /** tick计数器，用于控制发包频率 */
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            lastSentReduction = -1f;
            tickCounter = 0;
            return;
        }

        // 仅在有隐身进度时计算（避免无意义计算和发包）
        float stealthProgress = GhostFuselageClientData.getStealthProgress(player.getId());
        if (stealthProgress < 0.001f) {
            if (lastSentReduction != 0f) {
                // 隐身进度归零，发送一次0清除服务端残留
                NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new SyncGhostMoveSpeedMessage(0f));
                lastSentReduction = 0f;
            }
            tickCounter = 0;
            return;
        }

        // 用实际位移计算三维速度（客户端position-oldPosition是准确的）
        double dx = player.getX() - player.xOld;
        double dy = player.getY() - player.yOld;
        double dz = player.getZ() - player.zOld;
        double moveSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 过滤噪声：低于阈值视为静止
        float moveReduction = 0f;
        if (moveSpeed > NOISE_THRESHOLD) {
            double threshold = Config.getGhostFuselageMoveSpeedThreshold();
            if (moveSpeed > threshold) {
                double excessSpeed = moveSpeed - threshold;
                moveReduction = (float) (excessSpeed * Config.getGhostFuselageMoveSpeedReduction());
            }
        }

        // 每5tick发包一次（降低网络压力）
        tickCounter++;
        if (tickCounter >= SEND_INTERVAL) {
            tickCounter = 0;
            // 仅值变化时发包（进一步减少网络压力）
            if (Math.abs(moveReduction - lastSentReduction) > 0.0001f) {
                NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                    new SyncGhostMoveSpeedMessage(moveReduction));
                lastSentReduction = moveReduction;
            }
        }
    }
}
