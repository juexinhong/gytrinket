package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 设置拦截机攻击模式消息（客户端→服务端）
 */
public class SetInterceptorAttackModeMessage {
    private final InterceptorAttackMode attackMode;

    public SetInterceptorAttackModeMessage() {
        this.attackMode = InterceptorAttackMode.MELEE;
    }

    public SetInterceptorAttackModeMessage(InterceptorAttackMode attackMode) {
        this.attackMode = attackMode;
    }

    public SetInterceptorAttackModeMessage(FriendlyByteBuf buf) {
        this.attackMode = InterceptorAttackMode.byName(buf.readUtf());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(attackMode.getSerializedName());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                InterceptorWeaponManager.setAttackMode(player.getUUID(), attackMode);
                NetworkHandler.sendInterceptorAttackModeToPlayer(player, attackMode);

                // 更新所有僚机实体
                updateWingmanAttackMode(player, attackMode);
            }
        });
        context.setPacketHandled(true);
    }

    private static void updateWingmanAttackMode(ServerPlayer player, InterceptorAttackMode mode) {
        com.gy_mod.gy_trinket.core.entity.construct.ConstructManager cm =
            com.gy_mod.gy_trinket.core.entity.construct.ConstructManager.getInstance();
        Map<UUID, net.minecraft.world.entity.Entity> entities =
            cm.getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);
        for (net.minecraft.world.entity.Entity entity : entities.values()) {
            if (entity instanceof WingmanConstructEntity wingman) {
                wingman.refreshInterceptorData();
            }
        }
    }
}
