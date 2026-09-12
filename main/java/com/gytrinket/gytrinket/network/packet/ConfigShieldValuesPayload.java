package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品级护盾类型数值覆盖 payload（配置面板「护盾数值」编辑）。
 * <p>
 * values 为该物品某护盾类型的参数覆盖（paramKey -> value）；空 values 表示清除覆盖（回退 Config 默认）。
 * 护盾类型数值为"每物品实例独立"，不参与叠/单合并（兼容=多实例并存各用各的数值，不兼容=冲突链决定生效实例）。
 * 仅对已声明该护盾类型的物品生效；写入运行时覆盖文件后立即重新加载生效。
 */
public record ConfigShieldValuesPayload(String itemId, String shieldType,
                                        Map<String, Double> values) implements CustomPacketPayload {
    public static final Type<ConfigShieldValuesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_shield_values"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigShieldValuesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigShieldValuesPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag root = buf.readNbt();
            if (root == null) {
                return new ConfigShieldValuesPayload("", "", Map.of());
            }
            String itemId = root.getString("itemId");
            String shieldType = root.getString("shieldType");
            Map<String, Double> values = new HashMap<>();
            CompoundTag valuesTag = root.getCompound("values");
            for (String key : valuesTag.getAllKeys()) {
                values.put(key, valuesTag.getDouble(key));
            }
            return new ConfigShieldValuesPayload(itemId, shieldType, values);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ConfigShieldValuesPayload msg) {
            CompoundTag root = new CompoundTag();
            root.putString("itemId", msg.itemId() == null ? "" : msg.itemId());
            root.putString("shieldType", msg.shieldType() == null ? "" : msg.shieldType());
            CompoundTag valuesTag = new CompoundTag();
            if (msg.values() != null) {
                msg.values().forEach(valuesTag::putDouble);
            }
            root.put("values", valuesTag);
            buf.writeNbt(root);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigShieldValuesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (payload.itemId() == null || ResourceLocation.tryParse(payload.itemId()) == null) return;
            if (payload.shieldType() == null || payload.shieldType().isEmpty()) return;

            DefsManager.updateShieldTypeValues(player.server, payload.itemId(),
                    payload.shieldType(), payload.values());

            // 立即重算玩家属性并同步覆盖层到所有客户端（编辑即生效）
            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
            player.sendSystemMessage(Component.translatable("message.gytrinket.defs_applied"));
        });
    }
}
