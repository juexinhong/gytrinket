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
                                    Map<String, DefsManager.ShieldTypeOverride> shieldTypes) implements CustomPacketPayload {
    public static final Type<ConfigDefsSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_defs_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigDefsSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigDefsSyncPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag root = buf.readNbt();
            Map<String, DefsManager.SpecialMechanicOverride> sm = new HashMap<>();
            Map<String, DefsManager.ShieldTypeOverride> st = new HashMap<>();
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
                        // 物品级机制数值覆盖：set -> param -> ParamValue（values 数值 + stackables 平行布尔，缺省可叠加）
                        Map<String, Map<String, DefsManager.ParamValue>> values = new HashMap<>();
                        CompoundTag valuesTag = def.getCompound("values");
                        CompoundTag stackablesTag = def.getCompound("stackables");
                        for (String setName : valuesTag.getAllKeys()) {
                            CompoundTag paramsTag = valuesTag.getCompound(setName);
                            CompoundTag setStackables = stackablesTag.getCompound(setName);
                            Map<String, DefsManager.ParamValue> params = new HashMap<>();
                            for (String paramKey : paramsTag.getAllKeys()) {
                                params.put(paramKey, new DefsManager.ParamValue(
                                        paramsTag.getDouble(paramKey), setStackables.getBoolean(paramKey)));
                            }
                            values.put(setName, params);
                        }
                        sm.put(key, removed
                                ? DefsManager.SpecialMechanicOverride.removedState()
                                : DefsManager.SpecialMechanicOverride.declared(sets, values));
                    }
                }
                if (root.contains("shieldTypes")) {
                    CompoundTag stTag = root.getCompound("shieldTypes");
                    for (String key : stTag.getAllKeys()) {
                        CompoundTag def = stTag.getCompound(key);
                        List<String> types = new ArrayList<>();
                        ListTag typesTag = def.getList("types", 8);
                        for (int i = 0; i < typesTag.size(); i++) {
                            types.add(typesTag.getString(i));
                        }
                        List<String> exclusive = new ArrayList<>();
                        ListTag exTag = def.getList("exclusiveTypes", 8);
                        for (int i = 0; i < exTag.size(); i++) {
                            exclusive.add(exTag.getString(i));
                        }
                        // 物品级护盾数值覆盖：type -> param -> value（每物品实例独立，无叠/单语义）
                        Map<String, Map<String, DefsManager.ParamValue>> values = new HashMap<>();
                        CompoundTag valuesTag = def.getCompound("values");
                        for (String typeName : valuesTag.getAllKeys()) {
                            CompoundTag paramsTag = valuesTag.getCompound(typeName);
                            Map<String, DefsManager.ParamValue> params = new HashMap<>();
                            for (String paramKey : paramsTag.getAllKeys()) {
                                params.put(paramKey, new DefsManager.ParamValue(paramsTag.getDouble(paramKey), false));
                            }
                            values.put(typeName, params);
                        }
                        st.put(key, new DefsManager.ShieldTypeOverride(types, exclusive, values));
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
                // 物品级机制数值覆盖：set -> param -> ParamValue（values 数值 + stackables 平行布尔）
                CompoundTag valuesTag = new CompoundTag();
                CompoundTag stackablesTag = new CompoundTag();
                for (var setEntry : e.getValue().values().entrySet()) {
                    CompoundTag paramsTag = new CompoundTag();
                    CompoundTag setStackables = new CompoundTag();
                    for (var paramEntry : setEntry.getValue().entrySet()) {
                        paramsTag.putDouble(paramEntry.getKey(), paramEntry.getValue().value());
                        setStackables.putBoolean(paramEntry.getKey(), paramEntry.getValue().stackable());
                    }
                    valuesTag.put(setEntry.getKey(), paramsTag);
                    stackablesTag.put(setEntry.getKey(), setStackables);
                }
                def.put("values", valuesTag);
                def.put("stackables", stackablesTag);
                smTag.put(e.getKey(), def);
            }
            root.put("specialMechanics", smTag);
            CompoundTag stTag = new CompoundTag();
            for (var e : msg.shieldTypes().entrySet()) {
                CompoundTag def = new CompoundTag();
                ListTag types = new ListTag();
                for (String t : e.getValue().types()) {
                    types.add(net.minecraft.nbt.StringTag.valueOf(t));
                }
                def.put("types", types);
                ListTag exclusive = new ListTag();
                for (String t : e.getValue().exclusiveTypes()) {
                    exclusive.add(net.minecraft.nbt.StringTag.valueOf(t));
                }
                def.put("exclusiveTypes", exclusive);
                // 物品级护盾数值覆盖：type -> param -> ParamValue（values 数值 + stackables 平行布尔）
                CompoundTag valuesTag = new CompoundTag();
                CompoundTag stackablesTag = new CompoundTag();
                for (var typeEntry : e.getValue().values().entrySet()) {
                    CompoundTag paramsTag = new CompoundTag();
                    CompoundTag setStackables = new CompoundTag();
                    for (var paramEntry : typeEntry.getValue().entrySet()) {
                        paramsTag.putDouble(paramEntry.getKey(), paramEntry.getValue().value());
                        setStackables.putBoolean(paramEntry.getKey(), paramEntry.getValue().stackable());
                    }
                    valuesTag.put(typeEntry.getKey(), paramsTag);
                    stackablesTag.put(typeEntry.getKey(), setStackables);
                }
                def.put("values", valuesTag);
                def.put("stackables", stackablesTag);
                stTag.put(e.getKey(), def);
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
