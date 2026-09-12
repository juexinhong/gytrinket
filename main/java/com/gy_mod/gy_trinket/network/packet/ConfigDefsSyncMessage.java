package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * S->C 完整定义同步：护盾类型、特殊机制物品、物品->生效机制集合、提示规则、运行时覆盖层。
 * 客户端据此替代数据包读取（专用服务器下客户端无服务端数据包）。
 */
public class ConfigDefsSyncMessage {
    private final Map<String, Boolean> shieldTypes;
    private final List<String> specialMechanicItems;
    private final Map<String, List<String>> itemToSets;
    private final List<DefsManager.TooltipRuleDef> tooltipRules;
    private final Map<String, DefsManager.SpecialMechanicOverride> specialMechanicOverrides;
    private final Map<String, DefsManager.ShieldTypeOverride> shieldTypeOverrides;

    public ConfigDefsSyncMessage(Map<String, Boolean> shieldTypes,
                                 List<String> specialMechanicItems,
                                 Map<String, List<String>> itemToSets,
                                 List<DefsManager.TooltipRuleDef> tooltipRules,
                                 Map<String, DefsManager.SpecialMechanicOverride> specialMechanicOverrides,
                                 Map<String, DefsManager.ShieldTypeOverride> shieldTypeOverrides) {
        this.shieldTypes = shieldTypes;
        this.specialMechanicItems = specialMechanicItems;
        this.itemToSets = itemToSets;
        this.tooltipRules = tooltipRules;
        this.specialMechanicOverrides = specialMechanicOverrides;
        this.shieldTypeOverrides = shieldTypeOverrides;
    }

    public void toBytes(FriendlyByteBuf buf) {
        // shieldTypes
        buf.writeVarInt(shieldTypes.size());
        for (var e : shieldTypes.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeBoolean(e.getValue());
        }
        // specialMechanicItems
        buf.writeVarInt(specialMechanicItems.size());
        for (String id : specialMechanicItems) {
            buf.writeUtf(id);
        }
        // itemToSets
        buf.writeVarInt(itemToSets.size());
        for (var e : itemToSets.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (String s : e.getValue()) {
                buf.writeUtf(s);
            }
        }
        // tooltipRules
        buf.writeVarInt(tooltipRules.size());
        for (DefsManager.TooltipRuleDef rule : tooltipRules) {
            buf.writeUtf(rule.itemSet());
            buf.writeUtf(rule.titleKey());
            buf.writeUtf(rule.descriptionKey());
            buf.writeUtf(rule.color());
            buf.writeVarInt(rule.params().size());
            for (DefsManager.TooltipParam p : rule.params()) {
                buf.writeUtf(p.type());
                buf.writeUtf(p.source());
                buf.writeUtf(p.text());
                buf.writeBoolean(p.literal() != null);
                buf.writeDouble(p.literal() == null ? 0.0 : p.literal());
            }
        }
        // specialMechanicOverrides
        buf.writeVarInt(specialMechanicOverrides.size());
        for (var e : specialMechanicOverrides.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeBoolean(e.getValue().removed());
            buf.writeVarInt(e.getValue().sets().size());
            for (String s : e.getValue().sets()) {
                buf.writeUtf(s);
            }
            // values 段：set 名 -> (paramKey -> 覆盖值 + 是否可叠加)
            Map<String, Map<String, DefsManager.ParamValue>> values = e.getValue().values();
            int vn = values == null ? 0 : values.size();
            buf.writeVarInt(vn);
            if (values != null) {
                for (var ve : values.entrySet()) {
                    buf.writeUtf(ve.getKey());
                    Map<String, DefsManager.ParamValue> params = ve.getValue();
                    int pn = params == null ? 0 : params.size();
                    buf.writeVarInt(pn);
                    if (params != null) {
                        for (var pe : params.entrySet()) {
                            buf.writeUtf(pe.getKey());
                            buf.writeDouble(pe.getValue().value());
                            buf.writeBoolean(pe.getValue().stackable());
                        }
                    }
                }
            }
        }
        // shieldTypeOverrides
        buf.writeVarInt(shieldTypeOverrides.size());
        for (var e : shieldTypeOverrides.entrySet()) {
            buf.writeUtf(e.getKey());
            List<String> types = e.getValue().types();
            buf.writeVarInt(types.size());
            for (String s : types) {
                buf.writeUtf(s);
            }
            List<String> excl = e.getValue().exclusiveTypes();
            buf.writeVarInt(excl.size());
            for (String s : excl) {
                buf.writeUtf(s);
            }
            // values 段：护盾类型 -> (paramKey -> 覆盖值)；护盾类型数值为"每物品实例独立"，无叠/单语义
            Map<String, Map<String, Double>> values = e.getValue().values();
            int svn = values == null ? 0 : values.size();
            buf.writeVarInt(svn);
            if (values != null) {
                for (var ve : values.entrySet()) {
                    buf.writeUtf(ve.getKey());
                    Map<String, Double> params = ve.getValue();
                    int pn = params == null ? 0 : params.size();
                    buf.writeVarInt(pn);
                    if (params != null) {
                        for (var pe : params.entrySet()) {
                            buf.writeUtf(pe.getKey());
                            buf.writeDouble(pe.getValue());
                        }
                    }
                }
            }
        }
    }

    public ConfigDefsSyncMessage(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.shieldTypes = new HashMap<>();
        for (int i = 0; i < n; i++) {
            this.shieldTypes.put(buf.readUtf(), buf.readBoolean());
        }
        n = buf.readVarInt();
        this.specialMechanicItems = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            this.specialMechanicItems.add(buf.readUtf());
        }
        n = buf.readVarInt();
        this.itemToSets = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            int m = buf.readVarInt();
            List<String> v = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                v.add(buf.readUtf());
            }
            this.itemToSets.put(k, v);
        }
        n = buf.readVarInt();
        this.tooltipRules = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String itemSet = buf.readUtf();
            String titleKey = buf.readUtf();
            String descKey = buf.readUtf();
            String color = buf.readUtf();
            int m = buf.readVarInt();
            List<DefsManager.TooltipParam> params = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                String ptype = buf.readUtf();
                String psource = buf.readUtf();
                String ptext = buf.readUtf();
                Double pliteral = buf.readBoolean() ? buf.readDouble() : null;
                params.add(new DefsManager.TooltipParam(ptype, psource, ptext, pliteral));
            }
            this.tooltipRules.add(new DefsManager.TooltipRuleDef(itemSet, titleKey, descKey, color, params));
        }
        n = buf.readVarInt();
        this.specialMechanicOverrides = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            boolean removed = buf.readBoolean();
            int m = buf.readVarInt();
            List<String> sets = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                sets.add(buf.readUtf());
            }
            // values 段：set 名 -> (paramKey -> 覆盖值 + 是否可叠加)
            Map<String, Map<String, DefsManager.ParamValue>> values = new HashMap<>();
            int vn = buf.readVarInt();
            for (int j = 0; j < vn; j++) {
                String setName = buf.readUtf();
                int pn = buf.readVarInt();
                Map<String, DefsManager.ParamValue> params = new HashMap<>();
                for (int x = 0; x < pn; x++) {
                    String paramKey = buf.readUtf();
                    params.put(paramKey, new DefsManager.ParamValue(buf.readDouble(), buf.readBoolean()));
                }
                values.put(setName, params);
            }
            this.specialMechanicOverrides.put(k, removed
                    ? DefsManager.SpecialMechanicOverride.removedState()
                    : DefsManager.SpecialMechanicOverride.declared(sets, values));
        }
        n = buf.readVarInt();
        this.shieldTypeOverrides = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            int m = buf.readVarInt();
            List<String> types = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                types.add(buf.readUtf());
            }
            int x = buf.readVarInt();
            List<String> excl = new ArrayList<>();
            for (int j = 0; j < x; j++) {
                excl.add(buf.readUtf());
            }
            // values 段：护盾类型 -> (paramKey -> 覆盖值)；护盾类型数值为"每物品实例独立"，无叠/单语义
            Map<String, Map<String, Double>> shieldValues = new HashMap<>();
            int svn = buf.readVarInt();
            for (int j = 0; j < svn; j++) {
                String typeName = buf.readUtf();
                int pn = buf.readVarInt();
                Map<String, Double> params = new HashMap<>();
                for (int y = 0; y < pn; y++) {
                    params.put(buf.readUtf(), buf.readDouble());
                }
                shieldValues.put(typeName, params);
            }
            this.shieldTypeOverrides.put(k, new DefsManager.ShieldTypeOverride(types, excl, shieldValues));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getSender() == null) {
            // 客户端接收
            DefsManager.applyClientSync(shieldTypes, specialMechanicItems, itemToSets, tooltipRules, specialMechanicOverrides, shieldTypeOverrides);
        }
        context.setPacketHandled(true);
    }
}
