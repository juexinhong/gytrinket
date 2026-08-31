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
    private final Map<String, List<String>> shieldTypeOverrides;

    public ConfigDefsSyncMessage(Map<String, Boolean> shieldTypes,
                                 List<String> specialMechanicItems,
                                 Map<String, List<String>> itemToSets,
                                 List<DefsManager.TooltipRuleDef> tooltipRules,
                                 Map<String, DefsManager.SpecialMechanicOverride> specialMechanicOverrides,
                                 Map<String, List<String>> shieldTypeOverrides) {
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
        }
        // shieldTypeOverrides
        buf.writeVarInt(shieldTypeOverrides.size());
        for (var e : shieldTypeOverrides.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (String s : e.getValue()) {
                buf.writeUtf(s);
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
            this.specialMechanicOverrides.put(k, removed ? DefsManager.SpecialMechanicOverride.removedState() : DefsManager.SpecialMechanicOverride.declared(sets));
        }
        n = buf.readVarInt();
        this.shieldTypeOverrides = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = buf.readUtf();
            int m = buf.readVarInt();
            List<String> v = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                v.add(buf.readUtf());
            }
            this.shieldTypeOverrides.put(k, v);
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
