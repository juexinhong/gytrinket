package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.config.ConfigValueRegistry;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S 修改单个配置项（"配置项"界面编辑提交）。
 * 服务端白名单校验 + 范围钳制后应用并落盘，随后广播全量同步刷新所有客户端。
 * 权限：需 2 级（管理员）。
 */
public class ConfigValueUpdateMessage {
    private String id;
    private double value;

    public ConfigValueUpdateMessage() {}

    public ConfigValueUpdateMessage(String id, double value) {
        this.id = id;
        this.value = value;
    }

    public ConfigValueUpdateMessage(FriendlyByteBuf buf) {
        this.id = buf.readUtf();
        this.value = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeDouble(value);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            if (!ConfigValueRegistry.applyServer(id, value)) return;

            Config.SPEC.save();

            NetworkHandler.sendConfigValuesToAllPlayers(player, false);
        });
        context.setPacketHandled(true);
    }
}
