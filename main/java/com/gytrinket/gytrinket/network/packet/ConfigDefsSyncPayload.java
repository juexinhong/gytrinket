package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义覆盖数据同步 payload（S-&gt;C）：「应用」后把服务端覆盖层推送给客户端，
 * 配置面板/提示框实时显示生效后的特殊机制与护盾类型状态。
 * <p>
 * 同时携带物品属性表（登录时由 {@link #sendDefinitionsToPlayer} 推送），使客户端静态属性表
 * （AttributeManager）无需玩家手动打开配置界面即可填充——否则重启进服后物品属性 tooltip
 * 及依赖物品属性的客户端逻辑会失效，直到打开一次配置界面。
 */
public record ConfigDefsSyncPayload(Map<String, DefsManager.SpecialMechanicOverride> specialMechanics,
                                    Map<String, DefsManager.ShieldTypeOverride> shieldTypes,
                                    Map<String, Map<String, Double>> attributes) implements CustomPacketPayload {

    /** 兼容旧调用点：不带物品属性表的构造（attributes 为空表，客户端不清空静态属性表） */
    public ConfigDefsSyncPayload(Map<String, DefsManager.SpecialMechanicOverride> specialMechanics,
                                 Map<String, DefsManager.ShieldTypeOverride> shieldTypes) {
        this(specialMechanics, shieldTypes, Map.of());
    }
    public static final Type<ConfigDefsSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "config_defs_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigDefsSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigDefsSyncPayload decode(RegistryFriendlyByteBuf buf) {
            CompoundTag root = buf.readNbt();
            Map<String, DefsManager.SpecialMechanicOverride> sm = new HashMap<>();
            Map<String, DefsManager.ShieldTypeOverride> st = new HashMap<>();
            Map<String, Map<String, Double>> attrsMap = new HashMap<>();
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
                        st.put(key, new DefsManager.ShieldTypeOverride(types, values));
                    }
                }
                // 物品属性表：item -> attr -> value（缺失视为空表，客户端按兼容路径处理）
                if (root.contains("attributes")) {
                    CompoundTag attrsTag = root.getCompound("attributes");
                    for (String itemId : attrsTag.getAllKeys()) {
                        CompoundTag itemTag = attrsTag.getCompound(itemId);
                        Map<String, Double> attrs = new HashMap<>();
                        for (String attrName : itemTag.getAllKeys()) {
                            attrs.put(attrName, itemTag.getDouble(attrName));
                        }
                        if (!attrs.isEmpty()) {
                            attrsMap.put(itemId, attrs);
                        }
                    }
                }
            }
            return new ConfigDefsSyncPayload(sm, st, attrsMap);
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
            // 物品属性表：item -> attr -> value
            CompoundTag attrsTag = new CompoundTag();
            for (var e : msg.attributes().entrySet()) {
                CompoundTag itemTag = new CompoundTag();
                e.getValue().forEach(itemTag::putDouble);
                attrsTag.put(e.getKey(), itemTag);
            }
            root.put("attributes", attrsTag);
            buf.writeNbt(root);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigDefsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            DefsManager.setClientOverrides(payload.specialMechanics(), payload.shieldTypes());
            // 仅当携带物品属性表时重建客户端静态属性表——「应用」后的覆盖推送走两参构造
            // （attributes 为空表），无条件清空会破坏已填充的静态表
            if (!payload.attributes().isEmpty()) {
                AttributeManager.clearAllItemAttributes();
                payload.attributes().forEach((itemId, attrs) ->
                        AttributeManager.registerItemAttributes(itemId, attrs));
            }
        });
    }

    /**
     * 收集服务端当前的生效物品属性表（item_definitions 合并层），
     * 过滤 removed 与无 attributes 段的条目。
     */
    public static Map<String, Map<String, Double>> collectEffectiveItemAttributes() {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        DefsManager.getItemDefinitions().forEach((itemId, def) -> {
            if (def == null || def.removed() || def.attributes() == null || def.attributes().isEmpty()) {
                return;
            }
            Map<String, Double> attrs = new LinkedHashMap<>();
            def.attributes().forEach((name, pv) -> attrs.put(name, pv.value()));
            if (!attrs.isEmpty()) {
                result.put(itemId, attrs);
            }
        });
        return result;
    }

    /**
     * 把定义覆盖层与物品属性表推送给指定玩家（玩家登录进服时调用）。客户端收到后填充
     * AttributeManager 静态属性表，保证物品属性 tooltip 与依赖物品属性的客户端逻辑
     * 无需玩家手动打开一次配置界面即可生效。
     */
    public static void sendDefinitionsToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new ConfigDefsSyncPayload(
                DefsManager.getServerSpecialMechanicOverrides(),
                DefsManager.getServerShieldTypeOverrides(),
                collectEffectiveItemAttributes()));
    }
}
