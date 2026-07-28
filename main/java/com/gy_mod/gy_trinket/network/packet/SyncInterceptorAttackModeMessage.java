package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 同步拦截机攻击模式消息（服务端→客户端）
 */
public class SyncInterceptorAttackModeMessage {
    private final InterceptorAttackMode attackMode;

    public SyncInterceptorAttackModeMessage() {
        this.attackMode = InterceptorAttackMode.MELEE;
    }

    public SyncInterceptorAttackModeMessage(InterceptorAttackMode attackMode) {
        this.attackMode = attackMode;
    }

    public SyncInterceptorAttackModeMessage(FriendlyByteBuf buf) {
        this.attackMode = InterceptorAttackMode.byName(buf.readUtf());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(attackMode.getSerializedName());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    com.gy_mod.gy_trinket.client.attack_mode.interceptor.InterceptorWeaponClientData.setAttackMode(mc.player.getUUID(), attackMode);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
