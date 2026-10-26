package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.defs.DefsTypes;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * S->C 完整定义同步：护盾类型、特殊机制物品、物品->生效机制集合、提示规则、运行时覆盖层、护盾类型参数默认值、物品属性。
 * 客户端据此替代数据包读取（专用服务器下客户端无服务端数据包）。
 */
public class ConfigDefsSyncMessage {
    private final Map<String, Boolean> shieldTypes;
    private final List<String> specialMechanicItems;
    private final Map<String, List<String>> itemToSets;
    private final List<DefsTypes.TooltipRuleDef> tooltipRules;
    private final Map<String, DefsTypes.SpecialMechanicOverride> specialMechanicOverrides;
    private final Map<String, DefsTypes.ShieldTypeOverride> shieldTypeOverrides;
    private final Map<String, Map<String, Double>> shieldTypeParamDefaults;
    private final Map<String, Map<String, Double>> attributes;
    /**
     * 属性定义（名称/计算方式/分组）。客户端注册后 `AttributeManager.getAttributeDefinition`
     * 才能拿到类型默认值，玩家面板才能把 PERCENT/INDEPENDENT 的默认 1.0 正确过滤掉。
     * （专用服联机时客户端不会跑数据包重载，之前该表在客户端一直是空的。）
     */
    private final List<DefsTypes.AttributeEntry> attributeDefs;
    /** 服务端定义快照 revision（内容哈希）；客户端只保存用于诊断对齐。 */
    private final long revision;

    public ConfigDefsSyncMessage(Map<String, Boolean> shieldTypes,
                                 List<String> specialMechanicItems,
                                 Map<String, List<String>> itemToSets,
                                 List<DefsTypes.TooltipRuleDef> tooltipRules,
                                 Map<String, DefsTypes.SpecialMechanicOverride> specialMechanicOverrides,
                                 Map<String, DefsTypes.ShieldTypeOverride> shieldTypeOverrides,
                                 Map<String, Map<String, Double>> shieldTypeParamDefaults,
                                 Map<String, Map<String, Double>> attributes,
                                 List<DefsTypes.AttributeEntry> attributeDefs,
                                 long revision) {
        this.shieldTypes = shieldTypes;
        this.specialMechanicItems = specialMechanicItems;
        this.itemToSets = itemToSets;
        this.tooltipRules = tooltipRules;
        this.specialMechanicOverrides = specialMechanicOverrides;
        this.shieldTypeOverrides = shieldTypeOverrides;
        this.shieldTypeParamDefaults = shieldTypeParamDefaults;
        this.attributes = attributes;
        this.attributeDefs = attributeDefs;
        this.revision = revision;
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
        for (DefsTypes.TooltipRuleDef rule : tooltipRules) {
            buf.writeUtf(rule.itemSet());
            buf.writeUtf(rule.titleKey());
            buf.writeUtf(rule.descriptionKey());
            buf.writeUtf(rule.color());
            buf.writeVarInt(rule.params().size());
            for (DefsTypes.TooltipParam p : rule.params()) {
                buf.writeUtf(p.type());
                buf.writeUtf(p.source());
                buf.writeUtf(p.text());
                buf.writeBoolean(p.literal() != null);
                buf.writeDouble(p.literal() == null ? 0.0 : p.literal());
            }
            buf.writeVarInt(rule.branches().size());
            for (DefsTypes.TooltipBranch b : rule.branches()) {
                buf.writeUtf(b.paramName());
                buf.writeDouble(b.value());
                buf.writeUtf(b.descriptionKey());
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
            Map<String, Map<String, DefsTypes.ParamValue>> values = e.getValue().values();
            int vn = values == null ? 0 : values.size();
            buf.writeVarInt(vn);
            if (values != null) {
                for (var ve : values.entrySet()) {
                    buf.writeUtf(ve.getKey());
                    Map<String, DefsTypes.ParamValue> params = ve.getValue();
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
        // shieldTypeParamDefaults：护盾类型 -> (paramKey -> 默认值)（shield_types/*.json 的 shieldValues 段）
        buf.writeVarInt(shieldTypeParamDefaults.size());
        for (var e : shieldTypeParamDefaults.entrySet()) {
            buf.writeUtf(e.getKey());
            Map<String, Double> defaults = e.getValue();
            int dn = defaults == null ? 0 : defaults.size();
            buf.writeVarInt(dn);
            if (defaults != null) {
                for (var de : defaults.entrySet()) {
                    buf.writeUtf(de.getKey());
                    buf.writeDouble(de.getValue());
                }
            }
        }
        // attributes：物品 id -> (属性名 -> 数值)（覆写层/权威 item_definitions 生效属性）
        buf.writeVarInt(attributes.size());
        for (var e : attributes.entrySet()) {
            buf.writeUtf(e.getKey());
            Map<String, Double> attrs = e.getValue();
            int an = attrs == null ? 0 : attrs.size();
            buf.writeVarInt(an);
            if (attrs != null) {
                for (var ae : attrs.entrySet()) {
                    buf.writeUtf(ae.getKey());
                    buf.writeDouble(ae.getValue());
                }
            }
        }
        // attributeDefs：属性定义表（追加在末尾，保持与旧客户端的字段顺序兼容）
        buf.writeVarInt(attributeDefs.size());
        for (DefsTypes.AttributeEntry def : attributeDefs) {
            buf.writeUtf(def.name());
            buf.writeUtf(def.combine());
            buf.writeUtf(def.group());
        }
        buf.writeLong(revision);
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
            List<DefsTypes.TooltipParam> params = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                String ptype = buf.readUtf();
                String psource = buf.readUtf();
                String ptext = buf.readUtf();
                Double pliteral = buf.readBoolean() ? buf.readDouble() : null;
                params.add(new DefsTypes.TooltipParam(ptype, psource, ptext, pliteral));
            }
            int bn = buf.readVarInt();
            List<DefsTypes.TooltipBranch> branches = new ArrayList<>();
            for (int j = 0; j < bn; j++) {
                String bparam = buf.readUtf();
                double bvalue = buf.readDouble();
                String bdesc = buf.readUtf();
                branches.add(new DefsTypes.TooltipBranch(bparam, bvalue, bdesc));
            }
            this.tooltipRules.add(new DefsTypes.TooltipRuleDef(itemSet, titleKey, descKey, color, params, branches));
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
            Map<String, Map<String, DefsTypes.ParamValue>> values = new HashMap<>();
            int vn = buf.readVarInt();
            for (int j = 0; j < vn; j++) {
                String setName = buf.readUtf();
                int pn = buf.readVarInt();
                Map<String, DefsTypes.ParamValue> params = new HashMap<>();
                for (int x = 0; x < pn; x++) {
                    String paramKey = buf.readUtf();
                    params.put(paramKey, new DefsTypes.ParamValue(buf.readDouble(), buf.readBoolean()));
                }
                values.put(setName, params);
            }
            this.specialMechanicOverrides.put(k, removed
                    ? DefsTypes.SpecialMechanicOverride.removedState()
                    : DefsTypes.SpecialMechanicOverride.declared(sets, values));
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
            this.shieldTypeOverrides.put(k, new DefsTypes.ShieldTypeOverride(types, shieldValues));
        }
        n = buf.readVarInt();
        this.shieldTypeParamDefaults = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String typeName = buf.readUtf();
            int pn = buf.readVarInt();
            Map<String, Double> params = new HashMap<>();
            for (int y = 0; y < pn; y++) {
                params.put(buf.readUtf(), buf.readDouble());
            }
            this.shieldTypeParamDefaults.put(typeName, params);
        }
        n = buf.readVarInt();
        this.attributes = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String itemId = buf.readUtf();
            int an = buf.readVarInt();
            Map<String, Double> attrs = new HashMap<>();
            for (int y = 0; y < an; y++) {
                attrs.put(buf.readUtf(), buf.readDouble());
            }
            this.attributes.put(itemId, attrs);
        }
        n = buf.readVarInt();
        this.attributeDefs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            this.attributeDefs.add(new DefsTypes.AttributeEntry(buf.readUtf(), buf.readUtf(), buf.readUtf()));
        }
        this.revision = buf.readLong();
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getSender() == null) {
            // 客户端接收
            DefsManager.applyClientSync(shieldTypes, specialMechanicItems, itemToSets, tooltipRules, specialMechanicOverrides, shieldTypeOverrides, shieldTypeParamDefaults, revision);
            // 专用服联机时客户端不跑 datapack 重载，必须在这里重建 Config 的模块物品集合，
            // 否则 isAssaultItem/isChargedAttackItem 等客户端判定永远为 false（强袭/充能攻击失效）
            com.gytrinket.gytrinket.config.Config.applyClientDefs();
            // 属性定义：注册后客户端 getAttributeDefinition 才拿得到类型默认值，
            // 玩家面板才能过滤掉默认值（之前专用服联机时这表为空，PERCENT 默认 1.0 会全部显示出来）
            if (attributeDefs != null) {
                for (DefsTypes.AttributeEntry def : attributeDefs) {
                    try {
                        com.gytrinket.gytrinket.engine.math.AttributeType type =
                                com.gytrinket.gytrinket.engine.math.AttributeType.valueOf(def.combine());
                        AttributeManager.registerAttribute(def.name(), type,
                                def.group().isEmpty() ? null : def.group());
                    } catch (IllegalArgumentException ignored) {
                        // 非法组合方式忽略（与服务端 applyDefs 的行为一致）
                    }
                }
            }
            if (attributes != null && !attributes.isEmpty()) {
                // 属性静态表：仅在有数据时重建，避免空推送清空配置界面回包已填充的表
                AttributeManager.clearAllItemAttributes();
                attributes.forEach((itemId, attrs) -> AttributeManager.registerItemAttributes(itemId, attrs));
            }
        }
        context.setPacketHandled(true);
    }
}
