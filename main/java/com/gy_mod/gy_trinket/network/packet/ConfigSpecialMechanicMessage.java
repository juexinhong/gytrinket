package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S 特殊机制编辑：对物品的特殊机制集合执行 添加/移除/撤销声明/全量重置，
 * 并广播定义同步（绕过数据包校验，编辑即时生效）。
 * 权限：需 2 级（管理员）。
 * action: "set" | "remove" | "reset_special" | "reset_all"
 */
public class ConfigSpecialMechanicMessage {
    private final String action;
    private final String itemId;
    private final String set;

    public ConfigSpecialMechanicMessage(String action, String itemId, String set) {
        this.action = action;
        this.itemId = itemId;
        this.set = set;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(action);
        buf.writeUtf(itemId);
        buf.writeBoolean(set != null);
        if (set != null) {
            buf.writeUtf(set);
        }
    }

    public ConfigSpecialMechanicMessage(FriendlyByteBuf buf) {
        this.action = buf.readUtf();
        this.itemId = buf.readUtf();
        this.set = buf.readBoolean() ? buf.readUtf() : null;
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
            switch (action) {
                case "set":
                    if (set != null) DefsManager.updateSpecialMechanicSet(server, itemId, set, false);
                    break;
                case "remove":
                    if (set != null) DefsManager.updateSpecialMechanicSet(server, itemId, set, true);
                    break;
                case "reset_special":
                    DefsManager.updateSpecialMechanicOverride(server, itemId, true);
                    break;
                case "reset_all":
                    DefsManager.resetOverrides(server);
                    break;
            }
            NetworkHandler.sendDefsSyncToAllPlayers(server);
            // 机制集合变化影响玩家属性（如征途/增幅护盾动态属性），立即重算所有在线玩家
            for (var p : server.getPlayerList().getPlayers()) {
                com.gy_mod.gy_trinket.core.attribute.AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
        });
        context.setPacketHandled(true);
    }
}
