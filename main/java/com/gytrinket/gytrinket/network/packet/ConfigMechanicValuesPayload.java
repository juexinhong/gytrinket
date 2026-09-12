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
 * 物品级特殊机制数值覆盖 payload（配置面板「机制数值」编辑）。
 * <p>
 * values 为该物品某机制集合的参数覆盖（paramKey -> value）；stackables 与之平行（paramKey -> 是否可叠加，
 * 缺省视为可叠加）。空 values 表示清除覆盖（回退 Config 默认）。
 * 合并规则：可叠加项求和，不可叠加项各自比对，最终值 = max(可叠加和, 最大的不可叠加项)。
 * 仅对已声明该机制的物品生效；写入运行时覆盖文件后立即重新加载生效。
 */
public record ConfigMechanicValuesPayload(String itemId, String mechanicSet,
                                          Map<String, Double> values,
                                          Map<String, Boolean> stackables) implements CustomPacketPayload {
    public static final Type<ConfigMechanicValuesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_mechanic_values"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigMechanicValuesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigMechanicValuesPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag root = buf.readNbt();
            if (root == null) {
                return new ConfigMechanicValuesPayload("", "", Map.of(), Map.of());
            }
            String itemId = root.getString("itemId");
            String mechanicSet = root.getString("mechanicSet");
            Map<String, Double> values = new HashMap<>();
            CompoundTag valuesTag = root.getCompound("values");
            for (String key : valuesTag.getAllKeys()) {
                values.put(key, valuesTag.getDouble(key));
            }
            Map<String, Boolean> stackables = new HashMap<>();
            CompoundTag stackablesTag = root.getCompound("stackables");
            for (String key : stackablesTag.getAllKeys()) {
                stackables.put(key, stackablesTag.getBoolean(key));
            }
            return new ConfigMechanicValuesPayload(itemId, mechanicSet, values, stackables);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ConfigMechanicValuesPayload msg) {
            CompoundTag root = new CompoundTag();
            root.putString("itemId", msg.itemId() == null ? "" : msg.itemId());
            root.putString("mechanicSet", msg.mechanicSet() == null ? "" : msg.mechanicSet());
            CompoundTag valuesTag = new CompoundTag();
            if (msg.values() != null) {
                msg.values().forEach(valuesTag::putDouble);
            }
            root.put("values", valuesTag);
            CompoundTag stackablesTag = new CompoundTag();
            if (msg.stackables() != null) {
                msg.stackables().forEach(stackablesTag::putBoolean);
            }
            root.put("stackables", stackablesTag);
            buf.writeNbt(root);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigMechanicValuesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (payload.itemId() == null || ResourceLocation.tryParse(payload.itemId()) == null) return;
            if (payload.mechanicSet() == null || payload.mechanicSet().isEmpty()) return;

            DefsManager.updateSpecialMechanicValues(player.server, payload.itemId(),
                    payload.mechanicSet(), payload.values(), payload.stackables());

            // 立即重算玩家属性并同步覆盖层到所有客户端（编辑即生效）
            for (var p : player.server.getPlayerList().getPlayers()) {
                AttributeManager.recalculateAndCachePlayerAttributes(p);
            }
            NetworkHandler.sendDefsOverridesToAllPlayers(player);
            player.sendSystemMessage(Component.translatable("message.gytrinket.defs_applied"));
        });
    }
}
