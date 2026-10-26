package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.core.defs.DefsTypes.ParamValue;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ShieldTypeOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinition;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 定义运行时覆盖层的文件读写与 JSON 序列化（P5-5c 从 DefsManager 拆出）。
 * <p>
 * 覆盖文件位于 {@code config/gytrinket/}：分层文件 {@code override_layer_数字.json}（数字越大优先级越高）
 * 先加载，UI 覆盖文件 {@code gytrinket_ui_overrides.json} 最后加载（编辑条目优先级最高）。
 * 解析失败只跳过该文件；条目级 errors 由调用方（DefsLoader）统一计数。
 */
public final class DefsOverrideIO {

    private DefsOverrideIO() {}

    // ===== 运行时覆盖层：文件读写与生效 =====

    /** 覆写定义文件路径：config/gytrinket/gytrinket_ui_overrides.json（全局持久化，所有世界共享） */
    static Path getOverridesFile(MinecraftServer server) {
        return FMLPaths.CONFIGDIR.get().resolve("gytrinket").resolve(DefsManager.OVERRIDES_FILE_NAME);
    }

    /** 覆盖目录：config/gytrinket（分层覆盖文件 override_layer_数字.json 与 UI 覆盖文件同目录） */
    static Path getOverridesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("gytrinket");
    }

    /**
     * 读取覆盖文件到服务端内存。
     * 读取顺序：分层文件 override_layer_数字.json 按数字升序加载（同条目定义时数字大的覆盖数字小的），
     * 最后读取 UI 覆盖文件（UI 编辑条目优先级最高，保证编辑保存后立即生效）。
     * 文件不存在时忽略。
     */
    static void loadOverridesFromFile(MinecraftServer server) {
        DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.clear();
        DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.clear();
        DefsManager.SERVER_ITEM_OVERRIDES.clear();
        // UI 拥有条目随内存重建：仅 UI 文件与 UI 编辑写入的条目会回存 UI 覆盖文件
        DefsManager.SPECIAL_MECHANIC_UI_OWNED.clear();
        DefsManager.SHIELD_TYPE_UI_OWNED.clear();
        DefsManager.ITEM_UI_OWNED.clear();
        Path dir = getOverridesDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        // 扫描分层覆盖文件并按数字升序排序：后加载条目覆盖先加载条目 → 数字越大优先级越高
        Map<Integer, Path> layeredFiles = new TreeMap<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                var m = DefsManager.OVERRIDE_LAYER_FILE_PATTERN.matcher(name);
                if (m.matches()) {
                    layeredFiles.put(Integer.parseInt(m.group(1)), p);
                }
            }
        } catch (Exception e) {
            gytrinket.LOGGER.error("扫描定义覆盖目录失败: {}", dir, e);
        }
        for (Path file : layeredFiles.values()) {
            loadSingleOverridesFile(file, false);
        }
        if (!layeredFiles.isEmpty()) {
        }
        // UI 覆盖文件最后加载（最高优先级）
        loadSingleOverridesFile(getOverridesFile(server), true);
    }

    /** 解析单个覆盖文件并写入服务端覆盖内存（不存在时忽略，解析失败跳过该文件并继续后续层）；UI 文件条目额外登记为 UI 拥有 */
    static void loadSingleOverridesFile(Path file, boolean isUiOverrideFile) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            // 物品级条目暂存：UI 文件条目（旧段升级 + items 直读，回存 UI 文件）与分层文件 items 条目（仅生效不回存）分开登记
            Map<String, ItemDefinition> upgraded = new LinkedHashMap<>();
            Map<String, ItemDefinition> layeredUpgraded = new LinkedHashMap<>();
            if (root.has("specialMechanics")) {
                for (var e : root.getAsJsonObject("specialMechanics").entrySet()) {
                    String itemId = e.getKey();
                    com.google.gson.JsonObject def = e.getValue().getAsJsonObject();
                    boolean removed = def.has("removed") && def.get("removed").getAsBoolean();
                    List<String> sets = new ArrayList<>();
                    if (def.has("sets") && def.get("sets").isJsonArray()) {
                        def.getAsJsonArray("sets").forEach(el -> sets.add(el.getAsString()));
                    }
                    Map<String, Map<String, ParamValue>> values = parseMechanicValues(def);
                    DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.put(itemId,
                            removed ? SpecialMechanicOverride.removedState() : SpecialMechanicOverride.declared(sets, values));
                    if (isUiOverrideFile) {
                        DefsManager.SPECIAL_MECHANIC_UI_OWNED.add(itemId);
                        // 旧段升级：removed=true 仅精确撤销机制（mechanic=false 而非 removed，不误伤护盾定义）
                        upgraded.merge(itemId,
                                removed ? new ItemDefinition(false, false, null, null, null, values, null)
                                        : new ItemDefinition(true, false, sets, null, null, values, null),
                                ItemDefinition::merge);
                    }
                }
            }
            if (root.has("shieldTypes")) {
                for (var e : root.getAsJsonObject("shieldTypes").entrySet()) {
                    String itemId = e.getKey();
                    ShieldTypeOverride ov = parseShieldTypeOverride(e.getValue());
                    DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.put(itemId, ov);
                    if (isUiOverrideFile) {
                        DefsManager.SHIELD_TYPE_UI_OWNED.add(itemId);
                        // 旧段升级：types 整体覆盖 + 各类型 values 拍平为 shieldValues（护盾物品均单类型无歧义）；独占标记弃用（多实例语义取代）
                        Map<String, ParamValue> shieldValues = new LinkedHashMap<>();
                        for (var typeEntry : ov.values().entrySet()) {
                            typeEntry.getValue().forEach((k, v) -> shieldValues.put(k, ParamValue.of(v)));
                        }
                        upgraded.merge(itemId,
                                new ItemDefinition(null, false, null, ov.types(), shieldValues, null, null),
                                ItemDefinition::merge);
                    }
                }
            }
            // items 段（统一权威结构）
            Map<String, ItemDefinition> itemsTarget = isUiOverrideFile ? upgraded : layeredUpgraded;
            if (root.has("items")) {
                for (var e : root.getAsJsonObject("items").entrySet()) {
                    ItemDefinition def = parseItemDefinition(e.getValue());
                    if (def != null) {
                        itemsTarget.merge(DefsLoader.normalizeItemId(e.getKey()), def, ItemDefinition::merge);
                    }
                }
            }
            for (var e : upgraded.entrySet()) {
                DefsManager.SERVER_ITEM_OVERRIDES.put(e.getKey(), e.getValue());
                DefsManager.ITEM_UI_OWNED.add(e.getKey());
            }
            for (var e : layeredUpgraded.entrySet()) {
                DefsManager.SERVER_ITEM_OVERRIDES.put(e.getKey(), e.getValue());
            }
        } catch (Exception e) {
            gytrinket.LOGGER.error("读取定义覆盖文件失败: {}", file, e);
        }
    }

    /** 解析 items 段物品定义为统一权威结构（全部字段可选；缺失 = 未覆盖，保留下层值） */
    static ItemDefinition parseItemDefinition(com.google.gson.JsonElement el) {
        if (!el.isJsonObject()) return null;
        com.google.gson.JsonObject def = el.getAsJsonObject();
        Boolean mechanic = def.has("mechanic") && def.get("mechanic").isJsonPrimitive() ? def.get("mechanic").getAsBoolean() : null;
        boolean removed = def.has("removed") && def.get("removed").getAsBoolean();
        Map<String, ParamValue> shieldValues = def.has("shieldValues") && def.get("shieldValues").isJsonObject()
                ? parseParamMap(def.getAsJsonObject("shieldValues")) : null;
        Map<String, Map<String, ParamValue>> values = null;
        if (def.has("values") && def.get("values").isJsonObject()) {
            values = new LinkedHashMap<>();
            for (var setEntry : def.getAsJsonObject("values").entrySet()) {
                if (setEntry.getValue().isJsonObject()) {
                    Map<String, ParamValue> params = parseParamMap(setEntry.getValue().getAsJsonObject());
                    if (!params.isEmpty()) {
                        values.put(setEntry.getKey(), params);
                    }
                }
            }
        }
        Map<String, ParamValue> attributes = def.has("attributes") && def.get("attributes").isJsonObject()
                ? parseParamMap(def.getAsJsonObject("attributes")) : null;
        return new ItemDefinition(mechanic, removed, parseStringList(def, "sets"), parseStringList(def, "shieldTypes"),
                shieldValues, values, attributes);
    }

    /** 解析 ParamValue 映射：值可为简写数值或 {value, stackable} 对象 */
    static Map<String, ParamValue> parseParamMap(com.google.gson.JsonObject obj) {
        Map<String, ParamValue> result = new LinkedHashMap<>();
        for (var e : obj.entrySet()) {
            try {
                if (e.getValue().isJsonPrimitive()) {
                    result.put(e.getKey(), ParamValue.of(e.getValue().getAsDouble()));
                } else if (e.getValue().isJsonObject()) {
                    com.google.gson.JsonObject pv = e.getValue().getAsJsonObject();
                    double value = pv.has("value") ? pv.get("value").getAsDouble() : 0.0;
                    boolean stackable = !pv.has("stackable") || pv.get("stackable").getAsBoolean();
                    result.put(e.getKey(), new ParamValue(value, stackable));
                }
            } catch (Exception ignored) {
                // 非法数值跳过
            }
        }
        return result;
    }

    /** 解析字符串数组字段（缺失或类型不符返回 null，表达"未覆盖"） */
    static List<String> parseStringList(com.google.gson.JsonObject def, String name) {
        if (def.has(name) && def.get(name).isJsonArray()) {
            List<String> result = new ArrayList<>();
            def.getAsJsonArray(name).forEach(el -> result.add(el.getAsString()));
            return result;
        }
        return null;
    }

    /** 解析机制数值覆盖段：values={set: {param: value}} + stackables={set: {param: bool}}；非法值跳过（回退默认），缺省叠加标记视为可叠加（兼容旧格式） */
    static Map<String, Map<String, ParamValue>> parseMechanicValues(com.google.gson.JsonObject def) {
        Map<String, Map<String, ParamValue>> values = new LinkedHashMap<>();
        if (!def.has("values") || !def.get("values").isJsonObject()) {
            return values;
        }
        com.google.gson.JsonObject stackables = def.has("stackables") && def.get("stackables").isJsonObject()
                ? def.getAsJsonObject("stackables") : new com.google.gson.JsonObject();
        for (var setEntry : def.getAsJsonObject("values").entrySet()) {
            if (!setEntry.getValue().isJsonObject()) continue;
            Map<String, ParamValue> params = new LinkedHashMap<>();
            com.google.gson.JsonObject setStackables = stackables.has(setEntry.getKey())
                    && stackables.get(setEntry.getKey()).isJsonObject()
                    ? stackables.getAsJsonObject(setEntry.getKey()) : new com.google.gson.JsonObject();
            for (var paramEntry : setEntry.getValue().getAsJsonObject().entrySet()) {
                try {
                    boolean stackable = setStackables.has(paramEntry.getKey())
                            && setStackables.get(paramEntry.getKey()).isJsonPrimitive()
                            && setStackables.get(paramEntry.getKey()).getAsBoolean();
                    params.put(paramEntry.getKey(), new ParamValue(paramEntry.getValue().getAsDouble(), stackable));
                } catch (Exception ignored) {
                    // 非法数值：跳过该参数（运行时回退 Config 默认值）
                }
            }
            if (!params.isEmpty()) {
                values.put(setEntry.getKey(), params);
            }
        }
        return values;
    }

    /** 解析护盾类型覆盖条目：兼容旧格式（字符串数组）与新格式（{types:[], values:{}} 对象）；exclusiveTypes 字段已废弃，读取时忽略 */
    static ShieldTypeOverride parseShieldTypeOverride(com.google.gson.JsonElement el) {
        if (el.isJsonArray()) {
            List<String> types = new ArrayList<>();
            el.getAsJsonArray().forEach(t -> types.add(t.getAsString()));
            return new ShieldTypeOverride(types, Map.of());
        }
        com.google.gson.JsonObject def = el.getAsJsonObject();
        List<String> types = new ArrayList<>();
        if (def.has("types") && def.get("types").isJsonArray()) {
            def.getAsJsonArray("types").forEach(t -> types.add(t.getAsString()));
        }
        return new ShieldTypeOverride(types, parseShieldValues(def));
    }

    /** 解析护盾数值覆盖段：values={type: {param: value}}；非法值跳过（回退 Config 默认值） */
    static Map<String, Map<String, Double>> parseShieldValues(com.google.gson.JsonObject def) {
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        if (!def.has("values") || !def.get("values").isJsonObject()) {
            return values;
        }
        for (var typeEntry : def.getAsJsonObject("values").entrySet()) {
            if (!typeEntry.getValue().isJsonObject()) continue;
            Map<String, Double> params = new LinkedHashMap<>();
            for (var paramEntry : typeEntry.getValue().getAsJsonObject().entrySet()) {
                try {
                    params.put(paramEntry.getKey(), paramEntry.getValue().getAsDouble());
                } catch (Exception ignored) {
                    // 非法数值：跳过该参数（运行时回退 Config 默认值）
                }
            }
            if (!params.isEmpty()) {
                values.put(typeEntry.getKey(), params);
            }
        }
        return values;
    }

    /** 将统一物品定义序列化到条目 JSON：仅写非 null 字段；ParamValue 全可叠加时省略 stackable */
    static void writeItemDefinitionToJson(com.google.gson.JsonObject def, ItemDefinition d) {
        if (d.mechanic() != null) {
            def.addProperty("mechanic", d.mechanic());
        }
        if (d.removed()) {
            def.addProperty("removed", true);
        }
        if (d.sets() != null) {
            com.google.gson.JsonArray sets = new com.google.gson.JsonArray();
            d.sets().forEach(sets::add);
            def.add("sets", sets);
        }
        if (d.shieldTypes() != null) {
            com.google.gson.JsonArray types = new com.google.gson.JsonArray();
            d.shieldTypes().forEach(types::add);
            def.add("shieldTypes", types);
        }
        if (d.shieldValues() != null && !d.shieldValues().isEmpty()) {
            def.add("shieldValues", writeParamMap(d.shieldValues()));
        }
        if (d.values() != null && !d.values().isEmpty()) {
            com.google.gson.JsonObject valuesJson = new com.google.gson.JsonObject();
            for (var setEntry : d.values().entrySet()) {
                if (!setEntry.getValue().isEmpty()) {
                    valuesJson.add(setEntry.getKey(), writeParamMap(setEntry.getValue()));
                }
            }
            def.add("values", valuesJson);
        }
        if (d.attributes() != null && !d.attributes().isEmpty()) {
            def.add("attributes", writeParamMap(d.attributes()));
        }
    }

    /** 序列化 ParamValue 映射：可叠加写简写数值，不可叠加写 {value, stackable:false} */
    static com.google.gson.JsonObject writeParamMap(Map<String, ParamValue> params) {
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        for (var e : params.entrySet()) {
            if (e.getValue().stackable()) {
                result.addProperty(e.getKey(), e.getValue().value());
            } else {
                com.google.gson.JsonObject pv = new com.google.gson.JsonObject();
                pv.addProperty("value", e.getValue().value());
                pv.addProperty("stackable", false);
                result.add(e.getKey(), pv);
            }
        }
        return result;
    }

    /** 将服务端内存中的覆盖数据写入覆盖文件（编辑操作持久化，不触发重载）；仅写 items 段（统一权威结构，仅 UI 拥有条目） */
    static void saveOverridesToFile(MinecraftServer server) {
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            com.google.gson.JsonObject items = new com.google.gson.JsonObject();
            for (var e : DefsManager.SERVER_ITEM_OVERRIDES.entrySet()) {
                if (!DefsManager.ITEM_UI_OWNED.contains(e.getKey())) {
                    continue;
                }
                com.google.gson.JsonObject defJson = new com.google.gson.JsonObject();
                writeItemDefinitionToJson(defJson, e.getValue());
                items.add(e.getKey(), defJson);
            }
            root.add("items", items);
            Path file = getOverridesFile(server);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            gytrinket.LOGGER.error("保存定义覆盖文件失败", e);
        }
    }
}
