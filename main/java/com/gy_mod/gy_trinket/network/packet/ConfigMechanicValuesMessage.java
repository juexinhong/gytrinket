package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 物品级特殊机制数值覆盖消息（配置面板「机制数值」编辑）。
 * <p>
 * values 为该物品某机制集合的参数覆盖（paramKey -> value）；空 map 表示清除覆盖（回退 Config 默认）。
 * stackables 与 values 平行（paramKey -> 是否可叠加；多物品合并时可叠加求和、不可叠加取最大），缺省视为可叠加。
 * 仅对已声明该机制的物品生效；写入运行时覆盖文件后立即重新加载生效。
 * 权限：需 2 级（管理员）。
 */
public class ConfigMechanicValuesMessage {
    private final String itemId;
    private final String mechanicSet;
    private final Map<String, Double> values;
    private final Map<String, Boolean> stackables;

    public ConfigMechanicValuesMessage(String itemId, String mechanicSet, Map<String, Double> values, Map<String, Boolean> stackables) {
        this.itemId = itemId;
        this.mechanicSet = mechanicSet;
        this.values = values;
        this.stackables = stackables;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId == null ? "" : itemId);
        buf.writeUtf(mechanicSet == null ? "" : mechanicSet);
        int n = values == null ? 0 : values.size();
        buf.writeVarInt(n);
        if (values != null) {
            for (var e : values.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeDouble(e.getValue());
            }
        }
        int sn = stackables == null ? 0 : stackables.size();
        buf.writeVarInt(sn);
        if (stackables != null) {
            for (var e : stackables.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeBoolean(e.getValue());
            }
        }
    }

    public ConfigMechanicValuesMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.mechanicSet = buf.readUtf();
        int n = buf.readVarInt();
        Map<String, Double> vals = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            double v = buf.readDouble();
            if (!k.isEmpty()) {
                vals.put(k, v);
            }
        }
        this.values = vals;
        int sn = buf.readVarInt();
        Map<String, Boolean> stacks = new HashMap<>();
        for (int i = 0; i < sn; i++) {
            String k = buf.readUtf();
            boolean s = buf.readBoolean();
            if (!k.isEmpty()) {
                stacks.put(k, s);
            }
        }
        this.stackables = stacks;
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
            if (itemId == null || ResourceLocation.tryParse(itemId) == null) return;
            if (mechanicSet == null || mechanicSet.isEmpty()) return;

            DefsManager.updateSpecialMechanicValues(player.server, itemId, mechanicSet, values, stackables);

            // 立即重算玩家属性并同步覆盖层到所有客户端（编辑即生效）
            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
            player.sendSystemMessage(Component.translatable("message.gytrinket.defs_applied"));
        });
        context.setPacketHandled(true);
    }
}
