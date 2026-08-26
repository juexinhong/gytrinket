package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SetInterceptorAttackModeMessage {
    private final String attackModeName;

    public SetInterceptorAttackModeMessage() {
        this.attackModeName = "";
    }

    public SetInterceptorAttackModeMessage(String attackModeName) {
        this.attackModeName = attackModeName;
    }

    public SetInterceptorAttackModeMessage(com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode attackMode) {
        this.attackModeName = attackMode.getSerializedName();
    }

    public SetInterceptorAttackModeMessage(FriendlyByteBuf buf) {
        this.attackModeName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(attackModeName);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                InterceptorAttackMode mode = InterceptorAttackMode.byName(attackModeName);
                InterceptorWeaponManager.setAttackMode(player.getUUID(), mode);
                NetworkHandler.sendInterceptorAttackModeToPlayer(player, mode);

                Map<UUID, Entity> entities = ConstructManager.getInstance()
                    .getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);
                for (Entity entity : entities.values()) {
                    if (entity instanceof WingmanConstructEntity wingman) {
                        wingman.refreshInterceptorData();
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
