package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.defs.DefsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义覆盖数据同步 payload（S-&gt;C）：「应用」后把服务端覆盖层推送给客户端，
 * 配置面板/提示框实时显示生效后的特殊机制与护盾类型状态。
 */
public record ConfigDefsSyncPayload(Map<String, DefsManager.SpecialMechanicOverride> specialMechanics,
                                    Map<String, List<String>> shieldTypes) implements CustomPacketPayload {
    public static final Type<ConfigDefsSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_defs_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigDefsSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigDefsSyncPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag root = buf.readNbt();
            Map<String, DefsManager.SpecialMechanicOverride> sm = new HashMap<>();
            Map<String, List<String>> st = new HashMap<>();
            if (root != null) {
                if (root.contains("specialMechanics")) {
                    CompoundTag smTag = root.getCompound("specialMechanics");
                    for (String key : smTag.getAllKeys()) {
                        CompoundTag def = smTag.getCompound(key);
                        boolean removed = def.getBoolean("removed");
                        List<String> sets = new ArrayList<>();
                        ListTag setsTag = def.getList("sets", 8);
                        for (int i = 0; i < setsTag.size(); i++) {
                            sets.add(setsTag.getString(i));
                        }
                        sm.put(key, removed
                                ? DefsManager.SpecialMechanicOverride.removedState()
                                : DefsManager.SpecialMechanicOverride.declared(sets));
                    }
                }
                if (root.contains("shieldTypes")) {
                    CompoundTag stTag = root.getCompound("shieldTypes");
                    for (String key : stTag.getAllKeys()) {
                        List<String> types = new ArrayList<>();
                        ListTag typesTag = stTag.getList(key, 8);
                        for (int i = 0; i < typesTag.size(); i++) {
                            types.add(typesTag.getString(i));
                        }
                        st.put(key, types);
                    }
                }
            }
            return new ConfigDefsSyncPayload(sm, st);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ConfigDefsSyncPayload msg) {
            CompoundTag root = new CompoundTag();
            CompoundTag smTag = new CompoundTag();
            for (var e : msg.specialMechanics().entrySet()) {
                CompoundTag def = new CompoundTag();
                def.putBoolean("removed", e.getValue().removed());
                ListTag sets = new ListTag();
                for (String s : e.getValue().sets()) {
                    sets.add(net.minecraft.nbt.StringTag.valueOf(s));
                }
                def.put("sets", sets);
                smTag.put(e.getKey(), def);
            }
            root.put("specialMechanics", smTag);
            CompoundTag stTag = new CompoundTag();
            for (var e : msg.shieldTypes().entrySet()) {
                ListTag types = new ListTag();
                for (String t : e.getValue()) {
                    types.add(net.minecraft.nbt.StringTag.valueOf(t));
                }
                stTag.put(e.getKey(), types);
            }
            root.put("shieldTypes", stTag);
            buf.writeNbt(root);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigDefsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            DefsManager.setClientOverrides(payload.specialMechanics(), payload.shieldTypes());
        });
    }
}
