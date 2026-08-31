package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C->S 护盾类型编辑：更新/恢复某物品的护盾类型覆盖，并广播定义同步。
 * 权限：需 2 级（管理员）。
 */
public class ConfigShieldTypesMessage {
    private final String itemId;
    private final List<String> types;
    private final boolean reset;

    public ConfigShieldTypesMessage(String itemId, List<String> types, boolean reset) {
        this.itemId = itemId;
        this.types = types != null ? types : new ArrayList<>();
        this.reset = reset;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeBoolean(reset);
        if (!reset) {
            buf.writeVarInt(types.size());
            for (String t : types) {
                buf.writeUtf(t);
            }
        }
    }

    public ConfigShieldTypesMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.reset = buf.readBoolean();
        this.types = new ArrayList<>();
        if (!reset) {
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                this.types.add(buf.readUtf());
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        if (!player.hasPermissions(2)) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            MinecraftServer server = player.server;
            if (reset) {
                DefsManager.removeShieldTypeOverride(server, itemId);
            } else {
                DefsManager.updateShieldTypeOverride(server, itemId, types);
            }
            NetworkHandler.sendDefsSyncToAllPlayers(server);
            // 护盾类型变化影响玩家属性，立即重算所有在线玩家
            for (var p : server.getPlayerList().getPlayers()) {
                com.gy_mod.gy_trinket.core.attribute.AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
        });
        context.setPacketHandled(true);
    }
}
