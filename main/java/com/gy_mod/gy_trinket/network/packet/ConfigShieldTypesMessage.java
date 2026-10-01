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
 * C->S 护盾类型编辑：更新某物品的护盾类型覆盖，并广播定义同步。
 * 护盾全部按实例生效，独占/兼容设置已取消；types 为空列表表示清空该物品的护盾类型。
 * 权限：需 2 级（管理员）。
 */
public class ConfigShieldTypesMessage {
    private final String itemId;
    private final List<String> types;

    public ConfigShieldTypesMessage(String itemId, List<String> types) {
        this.itemId = itemId;
        this.types = types != null ? types : new ArrayList<>();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeVarInt(types.size());
        for (String t : types) {
            buf.writeUtf(t);
        }
    }

    public ConfigShieldTypesMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.types = new ArrayList<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            this.types.add(buf.readUtf());
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
            DefsManager.updateShieldTypeOverride(server, itemId, types);

            // 立即重算玩家属性并同步覆盖层到所有客户端（编辑即生效）
            for (var p : server.getPlayerList().getPlayers()) {
                com.gy_mod.gy_trinket.core.attribute.AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
            NetworkHandler.sendDefsSyncToAllPlayers(server);
        });
        context.setPacketHandled(true);
    }
}
