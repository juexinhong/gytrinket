package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigResetMessage {
    public ConfigResetMessage() {}

    public void toBytes(FriendlyByteBuf buf) {}

    public ConfigResetMessage(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            // 重置运行时覆盖（特殊机制/护盾类型），恢复数据包默认定义
            DefsManager.resetOverrides(player.server);

            AttributeManager.resetToDefaults();
            Config.resetItemAttributesConfig();

            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }

            NetworkHandler.sendConfigDataToAllPlayers(player);
            // 同步空覆盖层到所有客户端，面板/提示恢复默认显示
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
        });
        context.setPacketHandled(true);
    }
}
