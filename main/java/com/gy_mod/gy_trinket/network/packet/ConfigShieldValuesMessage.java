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
 * 物品级护盾类型数值覆盖消息（配置面板「护盾数值」编辑）。
 * <p>
 * values 为该物品某护盾类型的参数覆盖（paramKey -> value）；空 map 表示清除覆盖（回退 Config 默认）。
 * 护盾类型数值为"每物品实例独立"，不参与叠/单合并（兼容=多实例并存各用各的数值，不兼容=冲突链决定生效实例）。
 * 仅对已声明该护盾类型的物品生效；写入运行时覆盖文件后立即重新加载生效。
 * 权限：需 2 级（管理员）。
 */
public class ConfigShieldValuesMessage {
    private final String itemId;
    private final String shieldType;
    private final Map<String, Double> values;

    public ConfigShieldValuesMessage(String itemId, String shieldType, Map<String, Double> values) {
        this.itemId = itemId;
        this.shieldType = shieldType;
        this.values = values;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId == null ? "" : itemId);
        buf.writeUtf(shieldType == null ? "" : shieldType);
        int n = values == null ? 0 : values.size();
        buf.writeVarInt(n);
        if (values != null) {
            for (var e : values.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeDouble(e.getValue());
            }
        }
    }

    public ConfigShieldValuesMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.shieldType = buf.readUtf();
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
            if (shieldType == null || shieldType.isEmpty()) return;

            DefsManager.updateShieldTypeValues(player.server, itemId, shieldType, values);

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
