package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageClientData;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.packet.GhostFuselageAttackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 幽灵机身客户端攻击检测
 * <p>
 * 检测玩家左键攻击（空挥）并发送网络包到服务端扣除隐身进度。
 * 仅在攻击目标不是方块时触发（左键挖方块不算空挥）。
 * <p>
 * 近战攻击实体的检测由服务端 AttackEntityEvent 处理，
 * 此处仅处理空挥（攻击空气/无目标）的情况。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class GhostFuselageAttackHandler {

    @SubscribeEvent
    public static void onAttackKeyInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 有隐身进度才需要检测
        float stealthProgress = GhostFuselageClientData.getStealthProgress(mc.player.getId());
        if (stealthProgress < 0.001f) {
            return;
        }

        // 左键方块（挖掘）不算空挥，不扣除隐身进度
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        // 左键攻击实体由服务端 AttackEntityEvent 处理
        // 此处仅处理空挥（hitResult 为 MISS 或 ENTITY 以外的 null）
        if (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) {
            PacketDistributor.sendToServer(new GhostFuselageAttackPayload());
        }
    }
}
