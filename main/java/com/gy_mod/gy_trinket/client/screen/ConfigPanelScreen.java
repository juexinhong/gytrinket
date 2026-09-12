package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.defs.MechanicValueDefs;
import com.gy_mod.gy_trinket.core.defs.ShieldValueDefs;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

public class ConfigPanelScreen extends AbstractPanelScreen {

    private static final int BASE_ROW_HEIGHT = 18;
    private static final int ATTR_LINE_HEIGHT = 12;

    /** 特殊机制选择器 overlay 布局常量 */
    private static final int MECHANIC_OVERLAY_W = 220;
    private static final int MECHANIC_OVERLAY_H = 170;
    private static final int MECHANIC_LIST_TOP = 18;
    /** 列表底边距 overlay 底边的距离（给底部提示文本留空间，避免重叠） */
    private static final int MECHANIC_LIST_BOTTOM_MARGIN = 18;
    private static final int MECHANIC_ROW_HEIGHT = 11;

    /** 特殊机制数值编辑器 overlay 布局常量 */
    private static final int MECHANIC_VALUES_OVERLAY_W = 250;
    private static final int MECHANIC_VALUES_OVERLAY_H = 180;
    private static final int MECHANIC_VALUES_TAB_Y = 16;
    private static final int MECHANIC_VALUES_PARAMS_TOP = 30;
    private static final int MECHANIC_VALUES_ROW_H = 12;
    /** 保存按钮距 overlay 底边的距离 */
    private static final int MECHANIC_VALUES_SAVE_FROM_BOTTOM = 26;

    private final ListTag itemConfigData;
    private final List<String> allAttributeNames;

    private final ScrollBarComponent scrollBar = new ScrollBarComponent();

    private int hoveredItemIndex = -1;
    private int hoveredAttrIndex = -1;
    private boolean hoveredDelete = false;
    private boolean hoveredAddBtn = false;
    private boolean hoveredRemoveBtn = false;
    private ItemStack hoveredItemStack = ItemStack.EMPTY;

    private int selectedItemIndex = -1;
    private String editingAttrName = null;
    private String editingValue = "";
    private boolean isEditing = false;
    private boolean isNewAttribute = false;

    private boolean isSelectingAttr = false;
    private int selectAttrScrollOffset = 0;

    private boolean isDeletingAttr = false;

    private boolean isAddingItem = false;
    private String addingItemId = "";
    /** 添加物品输入框（复用原版 EditBox：光标/选择/粘贴/IME） */
    private net.minecraft.client.gui.components.EditBox addingItemEditBox = null;
    /** 添加物品输入补全建议列表（物品注册名实时匹配） */
    private final List<String> addingSuggestions = new ArrayList<>();
    /** 当前高亮的建议项索引 */
    private int addingSuggestionIndex = 0;
    /** 物品注册名缓存（首次使用后构建，避免每次按键全量遍历注册表） */
    private List<String> cachedItemIds = null;

    /** 护盾类型选择器 overlay 状态 */
    private boolean isSelectingShieldTypes = false;
    private final List<String> shieldTypeSelection = new ArrayList<>();
    /** 已选护盾类型的兼容开关（true=兼容可共存，false=独占） */
    private final Map<String, Boolean> shieldTypeCompat = new HashMap<>();
    /** 选中行状态行上的护盾类型文本悬停标记 */
    private boolean hoveredShieldTypeBtn = false;
    /** 特殊机制选择器 overlay 状态（true=添加列表，false=移除列表） */
    private boolean isSelectingMechanic = false;
    private boolean selectingMechanicAdd = true;
    private final List<String> mechanicPickList = new ArrayList<>();
    private final List<String> mechanicPickNames = new ArrayList<>();
    /** 特殊机制选择器滚轮偏移（行数） */
    private int mechanicScrollOffset = 0;
    /** 特殊机制选择器滑块（像素单位，与 mechanicScrollOffset 同步） */
    private final ScrollBarComponent mechanicScrollBar = new ScrollBarComponent();

    /** 特殊机制数值编辑器 overlay 状态 */
    private boolean isEditingMechanicValues = false;
    /** 正在编辑数值的物品 ID */
    private String mechanicValuesItemId = "";
    /** 当前编辑的机制集合（物品声明多个机制时通过 tab 行切换） */
    private String mechanicValuesSet = "";
    /** 当前机制集合的参数键顺序（与 MechanicValueDefs 注册顺序一致） */
    private final List<String> mechanicValuesParamKeys = new ArrayList<>();
    /** 参数草稿文本（paramKey → 用户输入；仅在覆盖时非空） */
    private final Map<String, String> mechanicValuesDraft = new LinkedHashMap<>();
    /** 已覆盖参数集合（仅这些参数会随保存发送覆盖值） */
    private final Set<String> mechanicValuesOverridden = new HashSet<>();
    private final Map<String, Boolean> mechanicValuesStackable = new LinkedHashMap<>();
    /** 当前正在输入的参数行索引（-1 = 无） */
    private int mechanicValueEditIndex = -1;
    /** 当前输入缓冲 */
    private String mechanicValueEditBuffer = "";
    /** 机制数值编辑器 overlay 的参数行悬停标记（渲染与点击共用） */
    private int hoveredMechanicValueRow = -1;
    /** 状态行机制文本悬停标记（点击打开数值编辑器） */
    private boolean hoveredMechanicValuesBtn = false;

    /** 护盾类型数值编辑器 overlay 状态（布局复用 MECHANIC_VALUES_* 常量） */
    private boolean isEditingShieldValues = false;
    /** 正在编辑数值的物品 ID */
    private String shieldValuesItemId = "";
    /** 当前编辑的护盾类型（物品声明多个类型时通过 tab 行切换） */
    private String shieldValuesType = "";
    /** 当前护盾类型的参数键顺序（与 ShieldValueDefs 注册顺序一致） */
    private final List<String> shieldValuesParamKeys = new ArrayList<>();
    /** 参数草稿文本（paramKey → 用户输入；仅在覆盖时非空） */
    private final Map<String, String> shieldValuesDraft = new LinkedHashMap<>();
    /** 已覆盖参数集合（仅这些参数会随保存发送覆盖值） */
    private final Set<String> shieldValuesOverridden = new HashSet<>();
    /** 穿盾开关：护盾耗尽时溢出伤害是否作用于玩家（pierce_through 覆盖，默认不穿盾） */
    private boolean shieldValuesPierce = false;
    /** 当前正在输入的参数行索引（-1 = 无） */
    private int shieldValueEditIndex = -1;
    /** 当前输入缓冲 */
    private String shieldValueEditBuffer = "";
    /** 护盾数值编辑器 overlay 的参数行悬停标记（渲染与点击共用） */
    private int hoveredShieldValueRow = -1;
    /** 护盾类型选择器底部"编辑数值"按钮悬停标记 */
    private boolean hoveredShieldValuesBtn = false;
    /** 护盾类型选择器底部"保存"按钮悬停标记（应用类型/兼容选择，与数值编辑器的保存操作统一） */
    private boolean hoveredShieldTypeSaveBtn = false;
    /** 类型 tab 优先列表（从类型选择器"编辑数值"进入时携带刚保存的本地选择，避免服务端同步未到时 tab 缺失） */
    private final List<String> shieldValuesPreferredTypes = new ArrayList<>();

    private boolean isDraggingItem = false;
    private int dragFromIndex = -1;
    private int dragTargetIndex = -1;
    private int lastMouseY = 0;

    public ConfigPanelScreen(Screen parentScreen, ListTag itemConfigData, List<String> allAttributeNames) {
        super(Component.translatable("screen.gytrinket.config_panel"), resolveParent(parentScreen), SolidUIRenderer.CONFIG);
        this.itemConfigData = itemConfigData != null ? itemConfigData : new ListTag();
        this.allAttributeNames = allAttributeNames != null ? allAttributeNames : new ArrayList<>();
    }

    private static Screen resolveParent(Screen parentScreen) {
        Screen actualParent = parentScreen;
        while (actualParent instanceof ConfigPanelScreen cps) {
            actualParent = cps.getParentScreen();
        }
        return actualParent;
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(400, 300, 20, 40);

        int btnY = panelY + panelHeight + 5;
        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.add_item"),
                button -> openAddItemInput()
        ).bounds(panelX + 5, btnY, 80, 16).renderer(renderer).build());

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.reset_defaults"),
                button -> NetworkHandler.INSTANCE.sendToServer(new ConfigResetMessage())
        ).bounds(panelX + 90, btnY, 80, 16).renderer(renderer).build());

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.config_entries"),
                button -> NetworkHandler.INSTANCE.sendToServer(new ConfigValuesRequestMessage())
        ).bounds(panelX + 175, btnY, 80, 16).renderer(renderer).build());

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ).bounds(panelX + panelWidth - 85, btnY, 80, 16).renderer(renderer).build());
    }

    /** 打开护盾类型选择器：以当前物品的护盾类型为初始选择 */
    private void openShieldTypeSelector() {
        if (selectedItemIndex < 0 || selectedItemIndex >= itemConfigData.size()) {
            return;
        }
        String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
        shieldTypeSelection.clear();
        shieldTypeCompat.clear();
        Map<String, Boolean> typeDefaults = DefsManager.clientShieldTypes();
        shieldTypeSelection.addAll(DefsManager.clientItemShieldTypes(itemId));
        // 兼容开关初值：物品级覆盖条目存在则以显式值为准，否则回退类型级默认
        DefsManager.ShieldTypeOverride ov = DefsManager.getClientShieldTypeOverride(itemId);
        for (String t : shieldTypeSelection) {
            shieldTypeCompat.put(t, ov != null ? !ov.exclusiveTypes().contains(t)
                    : typeDefaults.getOrDefault(t, Boolean.TRUE));
        }
        isSelectingShieldTypes = true;
    }

    /** 打开特殊机制选择器：add=true 列出可添加的机制，add=false 列出该物品当前的机制 */
    private void openMechanicSelector(boolean add) {
        if (selectedItemIndex < 0 || selectedItemIndex >= itemConfigData.size()) {
            return;
        }
        String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
        mechanicPickList.clear();
        mechanicPickNames.clear();
        if (add) {
            List<String> currentSets = DefsManager.clientSpecialMechanicSets(itemId);
            // 数据包声明的集合之外，并入已注册数值参数的机制集合（如环绕阵列无数据包声明，也允许为物品添加）
            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>(DefsManager.clientAllMechanicSets());
            candidates.addAll(MechanicValueDefs.allSets());
            for (String set : candidates) {
                if (!currentSets.contains(set)) {
                    mechanicPickList.add(set);
                    mechanicPickNames.add(DefsManager.clientMechanicDisplayName(set));
                }
            }
        } else {
            for (String set : DefsManager.clientSpecialMechanicSets(itemId)) {
                mechanicPickList.add(set);
                mechanicPickNames.add(DefsManager.clientMechanicDisplayName(set));
            }
        }
        selectingMechanicAdd = add;
        mechanicScrollOffset = 0;
        mechanicScrollBar.setScrollOffset(0);
        isSelectingMechanic = true;
    }

    /** 特殊机制选择器一屏可见行数 */
    private int mechanicVisibleRows() {
        int listBottom = MECHANIC_OVERLAY_H - MECHANIC_LIST_BOTTOM_MARGIN;
        return Math.max(1, (listBottom - MECHANIC_LIST_TOP) / MECHANIC_ROW_HEIGHT);
    }

    /** 打开特殊机制数值编辑器：默认选中物品声明的第一个机制集合 */
    private void openMechanicValuesEditor(String itemId) {
        List<String> sets = DefsManager.clientSpecialMechanicSets(itemId);
        if (sets.isEmpty()) {
            return;
        }
        mechanicValuesItemId = itemId;
        isEditingMechanicValues = true;
        rebuildMechanicValuesDraft(sets.get(0));
    }

    /** 切换/重建当前机制集合的参数草稿：既有覆盖值填入草稿，未覆盖参数显示默认值 */
    private void rebuildMechanicValuesDraft(String set) {
        cancelMechanicValueEdit();
        mechanicValuesSet = set;
        mechanicValuesParamKeys.clear();
        mechanicValuesDraft.clear();
        mechanicValuesOverridden.clear();
        mechanicValuesStackable.clear();
        for (MechanicValueDefs.ParamDef def : MechanicValueDefs.getParams(set)) {
            mechanicValuesParamKeys.add(def.key());
        }
        DefsManager.SpecialMechanicOverride ov = DefsManager.getClientSpecialMechanicOverride(mechanicValuesItemId);
        if (ov != null && !ov.removed() && ov.values() != null) {
            Map<String, DefsManager.ParamValue> existing = ov.values().get(set);
            if (existing != null) {
                for (Map.Entry<String, DefsManager.ParamValue> e : existing.entrySet()) {
                    if (mechanicValuesParamKeys.contains(e.getKey()) && e.getValue() != null) {
                        mechanicValuesDraft.put(e.getKey(), formatValue(e.getValue().value()));
                        mechanicValuesOverridden.add(e.getKey());
                        mechanicValuesStackable.put(e.getKey(), e.getValue().stackable());
                    }
                }
            }
        }
    }

    /** 确认当前参数行输入：合法数值写入草稿并标记覆盖；空/非法输入放弃本次编辑 */
    private void confirmMechanicValueEdit() {
        if (mechanicValueEditIndex < 0 || mechanicValueEditIndex >= mechanicValuesParamKeys.size()) {
            mechanicValueEditIndex = -1;
            mechanicValueEditBuffer = "";
            return;
        }
        String key = mechanicValuesParamKeys.get(mechanicValueEditIndex);
        String buffer = mechanicValueEditBuffer.trim();
        if (!buffer.isEmpty()) {
            try {
                double v = Double.parseDouble(buffer);
                mechanicValuesDraft.put(key, formatValue(v));
                mechanicValuesOverridden.add(key);
                mechanicValuesStackable.putIfAbsent(key, Boolean.TRUE);
            } catch (NumberFormatException ignored) {
                // 非法数值：保持原状
            }
        }
        cancelMechanicValueEdit();
    }

    /** 切换参数的叠加标志：true = 多物品求和（叠），false = 各物品独立比对取最大（单） */
    private void toggleMechanicValueStackable(String paramKey) {
        if (!mechanicValuesOverridden.contains(paramKey)) {
            return;
        }
        mechanicValuesStackable.put(paramKey, !mechanicValuesStackable.getOrDefault(paramKey, Boolean.TRUE));
    }

    /** 取消当前参数行的输入（不动草稿） */
    private void cancelMechanicValueEdit() {
        mechanicValueEditIndex = -1;
        mechanicValueEditBuffer = "";
    }

    /** 重置参数：移除覆盖标记，回退 Config 默认值显示 */
    private void resetMechanicValue(String paramKey) {
        if (mechanicValueEditIndex >= 0 && mechanicValueEditIndex < mechanicValuesParamKeys.size()
                && mechanicValuesParamKeys.get(mechanicValueEditIndex).equals(paramKey)) {
            cancelMechanicValueEdit();
        }
        mechanicValuesDraft.remove(paramKey);
        mechanicValuesOverridden.remove(paramKey);
    }

    /** 保存当前机制集合的数值覆盖并发送到服务端（无覆盖 = 清除该机制的物品级数值） */
    private void saveMechanicValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Boolean> stackables = new LinkedHashMap<>();
        for (String key : mechanicValuesOverridden) {
            String draft = mechanicValuesDraft.get(key);
            if (draft == null || draft.isEmpty()) continue;
            try {
                values.put(key, Double.parseDouble(draft));
                stackables.put(key, mechanicValuesStackable.getOrDefault(key, Boolean.TRUE));
            } catch (NumberFormatException ignored) {
            }
        }
        if (!mechanicValuesItemId.isEmpty() && !mechanicValuesSet.isEmpty()) {
            NetworkHandler.INSTANCE.sendToServer(new ConfigMechanicValuesMessage(
                    mechanicValuesItemId, mechanicValuesSet, values, stackables));
        }
        closeMechanicValuesEditor();
    }

    /** 关闭机制数值编辑器并清空状态 */
    private void closeMechanicValuesEditor() {
        isEditingMechanicValues = false;
        mechanicValuesItemId = "";
        mechanicValuesSet = "";
        mechanicValuesParamKeys.clear();
        mechanicValuesDraft.clear();
        mechanicValuesOverridden.clear();
        mechanicValuesStackable.clear();
        cancelMechanicValueEdit();
        hoveredMechanicValueRow = -1;
    }

    /** 机制参数显示名（翻译缺失时回退参数键） */
    private String mechanicValueDisplayName(MechanicValueDefs.ParamDef def) {
        String translated = Component.translatable(def.nameKey()).getString();
        return translated.equals(def.nameKey()) ? def.key() : translated;
    }

    /** 打开护盾类型数值编辑器：默认选中物品声明的第一个护盾类型 */
    private void openShieldValuesEditor(String itemId) {
        openShieldValuesEditor(itemId, null);
    }

    /** 打开护盾类型数值编辑器：preferredTypes 非空时优先作为 tab 来源（类型选择器进入时携带刚保存的本地选择） */
    private void openShieldValuesEditor(String itemId, List<String> preferredTypes) {
        List<String> types = (preferredTypes != null && !preferredTypes.isEmpty())
                ? preferredTypes : DefsManager.clientItemShieldTypes(itemId);
        if (types.isEmpty()) {
            return;
        }
        shieldValuesPreferredTypes.clear();
        if (preferredTypes != null) {
            shieldValuesPreferredTypes.addAll(preferredTypes);
        }
        shieldValuesItemId = itemId;
        isEditingShieldValues = true;
        rebuildShieldValuesDraft(types.get(0));
    }

    /** 数值编辑器 tab 类型来源：优先用本地选择列表，否则回退物品声明 */
    private List<String> shieldValuesTabTypes() {
        return !shieldValuesPreferredTypes.isEmpty()
                ? shieldValuesPreferredTypes : DefsManager.clientItemShieldTypes(shieldValuesItemId);
    }

    /** 切换/重建当前护盾类型的参数草稿：既有覆盖值填入草稿，未覆盖参数显示默认值 */
    private void rebuildShieldValuesDraft(String type) {
        cancelShieldValueEdit();
        shieldValuesType = type;
        shieldValuesParamKeys.clear();
        shieldValuesDraft.clear();
        shieldValuesOverridden.clear();
        shieldValuesPierce = false;
        for (ShieldValueDefs.ParamDef def : ShieldValueDefs.getParams(type)) {
            shieldValuesParamKeys.add(def.key());
        }
        DefsManager.ShieldTypeOverride ov = DefsManager.getClientShieldTypeOverride(shieldValuesItemId);
        if (ov != null && ov.values() != null) {
            Map<String, Double> existing = ov.values().get(type);
            if (existing != null) {
                for (Map.Entry<String, Double> e : existing.entrySet()) {
                    if (shieldValuesParamKeys.contains(e.getKey()) && e.getValue() != null) {
                        shieldValuesDraft.put(e.getKey(), formatValue(e.getValue()));
                        shieldValuesOverridden.add(e.getKey());
                    }
                }
                // 穿盾开关独立于数值参数行（pierce_through 不在 ShieldValueDefs 注册表中）
                Double pierce = existing.get("pierce_through");
                shieldValuesPierce = pierce != null && pierce >= 0.5;
            }
        }
    }

    /** 确认当前参数行输入：合法数值写入草稿并标记覆盖；空/非法输入放弃本次编辑 */
    private void confirmShieldValueEdit() {
        if (shieldValueEditIndex < 0 || shieldValueEditIndex >= shieldValuesParamKeys.size()) {
            shieldValueEditIndex = -1;
            shieldValueEditBuffer = "";
            return;
        }
        String key = shieldValuesParamKeys.get(shieldValueEditIndex);
        String buffer = shieldValueEditBuffer.trim();
        if (!buffer.isEmpty()) {
            try {
                double v = Double.parseDouble(buffer);
                shieldValuesDraft.put(key, formatValue(v));
                shieldValuesOverridden.add(key);
            } catch (NumberFormatException ignored) {
                // 非法数值：保持原状
            }
        }
        cancelShieldValueEdit();
    }

    /** 取消当前参数行的输入（不动草稿） */
    private void cancelShieldValueEdit() {
        shieldValueEditIndex = -1;
        shieldValueEditBuffer = "";
    }

    /** 重置参数：移除覆盖标记，回退 Config 默认值显示 */
    private void resetShieldValue(String paramKey) {
        if (shieldValueEditIndex >= 0 && shieldValueEditIndex < shieldValuesParamKeys.size()
                && shieldValuesParamKeys.get(shieldValueEditIndex).equals(paramKey)) {
            cancelShieldValueEdit();
        }
        shieldValuesDraft.remove(paramKey);
        shieldValuesOverridden.remove(paramKey);
    }

    /** 保存当前护盾类型的数值覆盖并发送到服务端（无覆盖 = 清除该护盾类型的物品级数值） */
    private void saveShieldValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : shieldValuesOverridden) {
            String draft = shieldValuesDraft.get(key);
            if (draft == null || draft.isEmpty()) continue;
            try {
                values.put(key, Double.parseDouble(draft));
            } catch (NumberFormatException ignored) {
            }
        }
        // 穿盾开启时附带覆盖标记；关闭时不放键（回退默认不穿盾）
        if (shieldValuesPierce) {
            values.put("pierce_through", 1.0);
        }
        if (!shieldValuesItemId.isEmpty() && !shieldValuesType.isEmpty()) {
            NetworkHandler.INSTANCE.sendToServer(new ConfigShieldValuesMessage(
                    shieldValuesItemId, shieldValuesType, values));
        }
        closeShieldValuesEditor();
    }

    /** 关闭护盾数值编辑器并清空状态 */
    private void closeShieldValuesEditor() {
        isEditingShieldValues = false;
        shieldValuesItemId = "";
        shieldValuesType = "";
        shieldValuesPierce = false;
        shieldValuesParamKeys.clear();
        shieldValuesDraft.clear();
        shieldValuesOverridden.clear();
        shieldValuesPreferredTypes.clear();
        cancelShieldValueEdit();
        hoveredShieldValueRow = -1;
    }

    /** 护盾参数显示名（翻译缺失时回退参数键） */
    private String shieldValueDisplayName(ShieldValueDefs.ParamDef def) {
        String translated = Component.translatable(def.nameKey()).getString();
        return translated.equals(def.nameKey()) ? def.key() : translated;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isAddingItem) {
            if (keyCode == 257 || keyCode == 335) { // Enter：确认添加
                finishAddingItem();
                return true;
            } else if (keyCode == 256) { // Esc：取消
                closeAddItemInput();
                return true;
            } else if (keyCode == 258) { // Tab：补全当前高亮建议
                if (!addingSuggestions.isEmpty() && addingSuggestionIndex < addingSuggestions.size()) {
                    addingItemId = addingSuggestions.get(addingSuggestionIndex);
                    addingItemEditBox.setValue(addingItemId);
                    addingItemEditBox.moveCursorToEnd();
                    updateAddingSuggestions();
                }
                return true;
            } else if (keyCode == 264) { // 下箭头：下一个建议
                if (!addingSuggestions.isEmpty()) {
                    addingSuggestionIndex = Math.min(addingSuggestions.size() - 1, addingSuggestionIndex + 1);
                }
                return true;
            } else if (keyCode == 265) { // 上箭头：上一个建议
                if (!addingSuggestions.isEmpty()) {
                    addingSuggestionIndex = Math.max(0, addingSuggestionIndex - 1);
                }
                return true;
            } else if (keyCode == 86 && Screen.hasControlDown() && !Screen.hasShiftDown() && !Screen.hasAltDown()) {
                // 【粘贴修复】原版 EditBox 的 filter 是整串语义：insertText 把剪贴板文本拼进
                // 完整内容后调用 filter.test(整串)，不通过则静默跳过赋值。本界面的 filter 是
                // 「不允许空格」，剪贴板文本拼入后只要含一个空格（如复制时带尾随空格/换行），
                // 整个粘贴就被拒绝——表现为「打字正常、Ctrl+V 无效」。
                // 改为自管粘贴：先清理空格/不可见字符再交给 EditBox，保留「物品ID无空格」的原意。
                String clipboard = this.minecraft != null ? this.minecraft.keyboardHandler.getClipboard() : "";
                if (clipboard != null && addingItemEditBox != null) {
                    String cleaned = clipboard.replace(" ", "").replace("\t", "")
                            .replace("\n", "").replace("\r", "");
                    if (!cleaned.isEmpty()) {
                        addingItemEditBox.insertText(cleaned);
                    }
                }
                return true;
            }
            // 其余键（字符/Backspace/Delete/方向/Home/End 等）交给原版 EditBox 处理
            if (addingItemEditBox != null) {
                // 兜底：输入框打开期间强制保持焦点，防止点击 overlay 空白导致 EditBox
                // 静默失焦后 keyPressed/charTyped 全部拒绝处理（粘贴/打字失效）
                addingItemEditBox.setFocused(true);
                addingItemEditBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (isEditing) {
            if (keyCode == 257 || keyCode == 335) {
                finishEditing();
                return true;
            } else if (keyCode == 256) {
                cancelEditing();
                return true;
            } else if (keyCode == 259) {
                if (!editingValue.isEmpty()) {
                    editingValue = editingValue.substring(0, editingValue.length() - 1);
                }
                return true;
            }
            return true;
        }
        if (isSelectingAttr) {
            if (keyCode == 256) {
                isSelectingAttr = false;
                return true;
            }
            return true;
        }
        if (isDeletingAttr) {
            if (keyCode == 256) {
                isDeletingAttr = false;
                return true;
            }
            return true;
        }
        if (isSelectingShieldTypes) {
            if (keyCode == 256) { // Esc：取消
                isSelectingShieldTypes = false;
                shieldTypeSelection.clear();
                shieldTypeCompat.clear();
                return true;
            } else if (keyCode == 257 || keyCode == 335) { // Enter：应用
                applyShieldTypeSelection();
                return true;
            }
            return true;
        }
        if (isSelectingMechanic) {
            if (keyCode == 256) { // Esc：取消
                isSelectingMechanic = false;
                return true;
            }
            return true;
        }
        if (isEditingMechanicValues) {
            if (keyCode == 257 || keyCode == 335) { // Enter：确认当前参数行输入
                confirmMechanicValueEdit();
                return true;
            } else if (keyCode == 256) { // Esc：编辑中先取消该行，否则关闭编辑器
                if (mechanicValueEditIndex >= 0) {
                    cancelMechanicValueEdit();
                } else {
                    closeMechanicValuesEditor();
                }
                return true;
            } else if (keyCode == 259 && mechanicValueEditIndex >= 0) { // Backspace：删尾字符
                if (!mechanicValueEditBuffer.isEmpty()) {
                    mechanicValueEditBuffer = mechanicValueEditBuffer.substring(0, mechanicValueEditBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 应用护盾类型选择并发送到服务端（独占类型集合 = 选中且开关为不兼容的子集） */
    private void applyShieldTypeSelection() {
        if (selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
            String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
            List<String> exclusiveTypes = new ArrayList<>();
            for (String t : shieldTypeSelection) {
                if (!shieldTypeCompat.getOrDefault(t, Boolean.TRUE)) {
                    exclusiveTypes.add(t);
                }
            }
            NetworkHandler.INSTANCE.sendToServer(new ConfigShieldTypesMessage(itemId, new ArrayList<>(shieldTypeSelection), exclusiveTypes, false));
        }
        isSelectingShieldTypes = false;
        shieldTypeSelection.clear();
        shieldTypeCompat.clear();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isAddingItem) {
            if (addingItemEditBox != null) {
                addingItemEditBox.charTyped(codePoint, modifiers);
            }
            return true;
        }
        if (isEditing) {
            if (codePoint == '-' || codePoint == '.' || (codePoint >= '0' && codePoint <= '9')) {
                editingValue += codePoint;
            }
            return true;
        }
        if (isEditingMechanicValues) {
            if (mechanicValueEditIndex >= 0
                    && (codePoint == '-' || codePoint == '.' || (codePoint >= '0' && codePoint <= '9'))) {
                mechanicValueEditBuffer += codePoint;
            }
            return true;
        }
        if (isEditingShieldValues) {
            if (shieldValueEditIndex >= 0
                    && (codePoint == '-' || codePoint == '.' || (codePoint >= '0' && codePoint <= '9'))) {
                shieldValueEditBuffer += codePoint;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void finishAddingItem() {
        if (!addingItemId.isEmpty() && !addingItemId.equals("minecraft:air")) {
            boolean alreadyExists = false;
            for (int i = 0; i < itemConfigData.size(); i++) {
                if (itemConfigData.getCompound(i).getString("itemId").equals(addingItemId)) {
                    alreadyExists = true;
                    break;
                }
            }
            if (!alreadyExists) {
                CompoundTag newItem = new CompoundTag();
                newItem.putString("itemId", addingItemId);
                newItem.put("attributes", new ListTag());
                itemConfigData.add(newItem);

                NetworkHandler.INSTANCE.sendToServer(
                    new ConfigAddItemMessage(addingItemId));
            }
        }
        closeAddItemInput();
    }

    /** 打开添加物品输入框（复用原版 EditBox，实时匹配物品注册名补全） */
    private void openAddItemInput() {
        isAddingItem = true;
        addingItemId = "";
        int overlayW = 240;
        int overlayH = 150;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;
        addingItemEditBox = new net.minecraft.client.gui.components.EditBox(font,
                overlayX + 8, overlayY + 26, overlayW - 16, 14,
                Component.translatable("screen.gytrinket.add_item_prompt"));
        addingItemEditBox.setMaxLength(64);
        // 物品注册名不含空格，阻止空格输入
        addingItemEditBox.setFilter(text -> !text.contains(" "));
        addingItemEditBox.setResponder(text -> {
            addingItemId = text.trim();
            updateAddingSuggestions();
        });
        addingItemEditBox.setValue("");
        addingItemEditBox.setFocused(true);
        updateAddingSuggestions();
    }

    /** 关闭添加物品输入框 */
    private void closeAddItemInput() {
        isAddingItem = false;
        addingItemId = "";
        if (addingItemEditBox != null) {
            addingItemEditBox.setFocused(false);
        }
        addingItemEditBox = null;
        addingSuggestions.clear();
        addingSuggestionIndex = 0;
    }

    /** 全部物品注册名（惰性缓存；注册表在进入世界后稳定） */
    private List<String> allItemIds() {
        if (cachedItemIds == null) {
            cachedItemIds = new ArrayList<>();
            for (net.minecraft.resources.ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                cachedItemIds.add(key.toString());
            }
        }
        return cachedItemIds;
    }

    /** 根据当前输入实时匹配物品注册名（BuiltInRegistries 原版物品表） */
    private void updateAddingSuggestions() {
        addingSuggestions.clear();
        String input = addingItemId.toLowerCase(java.util.Locale.ROOT);
        for (String id : allItemIds()) {
            if (input.isEmpty() || id.toLowerCase(java.util.Locale.ROOT).contains(input)) {
                addingSuggestions.add(id);
                if (addingSuggestions.size() >= 10) {
                    break;
                }
            }
        }
        addingSuggestionIndex = 0;
    }

    private void finishEditing() {
        if (selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size() && editingAttrName != null) {
            CompoundTag itemTag = itemConfigData.getCompound(selectedItemIndex);
            ListTag attrs = itemTag.getList("attributes", 10);
            int editingAttrIndex = findAttrIndex(attrs, editingAttrName);

            if (editingAttrIndex >= 0 && editingAttrIndex < attrs.size()) {
                try {
                    double val = editingValue.isEmpty() ? 0 : Double.parseDouble(editingValue);
                    CompoundTag attr = attrs.getCompound(editingAttrIndex);
                    attr.putDouble("value", val);
                    NetworkHandler.INSTANCE.sendToServer(
                        new ConfigUpdateMessage(itemTag.getString("itemId"), attr.getString("name"), val));
                } catch (NumberFormatException ignored) {}
            }
        }
        isEditing = false;
        isNewAttribute = false;
        editingAttrName = null;
        editingValue = "";
    }

    private void cancelEditing() {
        if (isNewAttribute && editingAttrName != null && selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
            CompoundTag itemTag = itemConfigData.getCompound(selectedItemIndex);
            ListTag attrs = itemTag.getList("attributes", 10);
            int editingAttrIndex = findAttrIndex(attrs, editingAttrName);
            if (editingAttrIndex >= 0 && editingAttrIndex < attrs.size()) {
                String itemId = itemTag.getString("itemId");
                attrs.remove(editingAttrIndex);
                itemTag.put("attributes", attrs);
                NetworkHandler.INSTANCE.sendToServer(
                    new ConfigRemoveAttrMessage(itemId, editingAttrName));
            }
        }
        isEditing = false;
        isNewAttribute = false;
        editingAttrName = null;
        editingValue = "";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isSelectingMechanic) {
            int step = (int) (delta * 8);
            int maxOffset = Math.max(0, mechanicPickList.size() - mechanicVisibleRows());
            mechanicScrollOffset = Math.max(0, Math.min(mechanicScrollOffset - step, maxOffset));
            mechanicScrollBar.setScrollOffset(mechanicScrollOffset * MECHANIC_ROW_HEIGHT);
        } else if (isSelectingAttr) {
            int step = (int) (delta * 8);
            // 可视完整行数 = (140 - 18 - 10) / 10 = 11，滚动上限按 11 行计算，保证末尾属性能完整显示
            selectAttrScrollOffset = Math.max(0, Math.min(selectAttrScrollOffset - step, Math.max(0, allAttributeNames.size() - 11)));
        } else {
            scrollBar.mouseScrolled(delta);
        }
        return true;
    }

    private Set<String> getExistingAttrs(int itemIndex) {
        Set<String> existing = new HashSet<>();
        if (itemIndex >= 0 && itemIndex < itemConfigData.size()) {
            ListTag attrs = itemConfigData.getCompound(itemIndex).getList("attributes", 10);
            for (int j = 0; j < attrs.size(); j++) {
                existing.add(attrs.getCompound(j).getString("name"));
            }
        }
        return existing;
    }

    /** 状态行特殊机制文本（多个机制名排列；未声明时返回"未声明"文案） */
    private String buildStatusMechanicText(String itemId) {
        List<String> mechanicNames = DefsManager.clientSpecialMechanicNames(itemId);
        boolean isMechanic = DefsManager.clientIsSpecialMechanic(itemId);
        if (isMechanic && !mechanicNames.isEmpty()) {
            return String.join("  ", mechanicNames);
        } else if (isMechanic) {
            return Component.translatable("screen.gytrinket.status_mechanic_declared").getString();
        } else {
            return Component.translatable("screen.gytrinket.status_mechanic_undeclared").getString();
        }
    }

    /** 状态行护盾类型文本（"护盾类型:xxx,yyy"） */
    private String buildShieldTypeText(String itemId) {
        String text = Component.translatable("screen.gytrinket.shield_types").getString();
        List<String> currentShieldTypes = DefsManager.clientItemShieldTypes(itemId);
        if (!currentShieldTypes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String t : currentShieldTypes) {
                if (sb.length() > 0) sb.append(",");
                sb.append(getShieldTypeDisplayName(t));
            }
            text += ":" + sb;
        }
        return text;
    }

    /** 状态行可用右边界（面板右边距 - 滑块占位，滚动条显示时预留放大 4 倍） */
    private int statusLineAvailX() {
        return panelX + panelWidth - 8 - (scrollBar.needsScrollbar() ? 28 : 0);
    }

    /** 状态行是否换行：特殊机制文本 + 护盾类型按钮超出面板可用宽度（含右侧滑块预留）时，护盾类型按钮换到下一行 */
    private boolean isStatusWrap(String statusText, String shieldTypeText) {
        int attrX = panelX + 28;
        return attrX + font.width(statusText) + 12 + font.width(shieldTypeText) > statusLineAvailX();
    }

    /** 文本超宽时按宽度截断并追加省略号（防止机制文本自身溢出到滑块区域） */
    private String truncateToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String trimmed = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return trimmed + "…";
    }

    private int calcRowHeight(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemConfigData.size()) return BASE_ROW_HEIGHT;
        boolean isSelected = (itemIndex == selectedItemIndex);

        if (!isSelected) return BASE_ROW_HEIGHT;

        CompoundTag itemTag = itemConfigData.getCompound(itemIndex);
        ListTag attrs = itemTag.getList("attributes", 10);

        // 状态行可能换行：特殊机制 + 护盾类型超宽时占 2 行
        String itemId = itemTag.getString("itemId");
        int statusLines = isStatusWrap(buildStatusMechanicText(itemId), buildShieldTypeText(itemId)) ? 2 : 1;

        // 选中行额外显示：特殊机制/护盾类型状态行 + 属性编辑区
        if (attrs.isEmpty()) {
            return BASE_ROW_HEIGHT + statusLines * ATTR_LINE_HEIGHT + ATTR_LINE_HEIGHT + ATTR_LINE_HEIGHT;
        }

        int attrCellMaxWidth = panelWidth - 55;
        int attrX = 0;
        int attrLines = 1;
        for (int j = 0; j < attrs.size(); j++) {
            CompoundTag attr = attrs.getCompound(j);
            String attrName = attr.getString("name");
            double attrValue = attr.getDouble("value");
            String attrText = Component.translatable("tooltip.gytrinket.attr." + attrName).getString()
                    + "=" + formatValue(attrValue);
            int textWidth = font.width(attrText) + 8;
            if (attrX + textWidth > attrCellMaxWidth) {
                attrX = textWidth;
                attrLines++;
            } else {
                attrX += textWidth;
            }
        }
        return BASE_ROW_HEIGHT + statusLines * ATTR_LINE_HEIGHT + attrLines * ATTR_LINE_HEIGHT + ATTR_LINE_HEIGHT;
    }

    private int calcTotalHeight() {
        int total = 0;
        for (int i = 0; i < itemConfigData.size(); i++) {
            total += calcRowHeight(i);
        }
        return total;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        renderPanelBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.config_panel_title").getString(),
                panelX + 8, panelY + 6, renderer.getAccentColor());

        if (isDeletingAttr) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.delete_attr_hint").getString(),
                    panelX + panelWidth / 2 + 5, panelY + 6, renderer.getDeleteColor());
        }

        if (isDraggingItem) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.reorder_hint").getString(),
                    panelX + panelWidth / 2 + 5, panelY + 6, renderer.getAccentColor());
        }

        boolean hasOverlay = isSelectingAttr || isAddingItem || isSelectingShieldTypes || isSelectingMechanic || isEditingMechanicValues;

        if (!hasOverlay) {
            renderContent(guiGraphics, mouseX, mouseY);
        }

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (isSelectingAttr) {
            renderSelectAttrOverlay(guiGraphics, mouseX, mouseY);
        }

        if (isAddingItem) {
            renderAddItemOverlay(guiGraphics);
        }

        if (isSelectingShieldTypes) {
            renderShieldTypeOverlay(guiGraphics, mouseX, mouseY);
        }

        if (isSelectingMechanic) {
            renderMechanicOverlay(guiGraphics, mouseX, mouseY);
        }

        if (isEditingMechanicValues) {
            renderMechanicValuesOverlay(guiGraphics, mouseX, mouseY);
        }

        if (isEditingShieldValues) {
            renderShieldValuesOverlay(guiGraphics, mouseX, mouseY);
        }

        if (!hoveredItemStack.isEmpty() && !hasOverlay) {
            guiGraphics.renderTooltip(font, hoveredItemStack, mouseX, mouseY);
        }
    }

    private void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentY = panelY + 20;
        int contentBottom = panelY + panelHeight - 6;
        int totalHeight = calcTotalHeight();
        int visibleHeight = contentBottom - contentY;
        scrollBar.updateMaxScroll(totalHeight, visibleHeight);

        hoveredItemIndex = -1;
        hoveredAttrIndex = -1;
        hoveredDelete = false;
        hoveredAddBtn = false;
        hoveredRemoveBtn = false;
        hoveredShieldTypeBtn = false;
        hoveredMechanicValuesBtn = false;
        hoveredMechanicValueRow = -1;
        hoveredItemStack = ItemStack.EMPTY;

        lastMouseY = mouseY;

        // 鼠标是否在内容裁剪区内：列表渲染有 scissor 截断，但 hover/点击判定不会自动裁剪——
        // 部分可见的展开行会延伸到面板外底部按钮区域（恢复默认等），不裁剪会误触发列表交互
        boolean mouseInClip = mouseY >= contentY && mouseY < contentBottom;

        if (isDraggingItem && dragFromIndex >= 0 && dragFromIndex < itemConfigData.size()) {
            int dragRowHeight = calcRowHeight(dragFromIndex);
            dragTargetIndex = calcDragTargetIndex(mouseY, contentY);
            int adjustedTotal = totalHeight;
            scrollBar.updateMaxScroll(adjustedTotal, visibleHeight);
        }

        guiGraphics.enableScissor(panelX + 1, contentY, panelX + panelWidth - 1, contentBottom);

        int y = contentY - scrollBar.getScrollOffset();
        for (int i = 0; i < itemConfigData.size(); i++) {
            if (isDraggingItem && i == dragFromIndex) {
                y += calcRowHeight(i);
                continue;
            }

            if (isDraggingItem && i == dragTargetIndex) {
                int dragRowHeight = calcRowHeight(dragFromIndex);
                guiGraphics.fill(panelX + 5, y, panelX + panelWidth - 5, y + 2, renderer.getAccentColor());
                y += dragRowHeight;
            }

            int rowHeight = calcRowHeight(i);
            if (y + rowHeight < contentY) { y += rowHeight; continue; }
            if (y >= contentBottom) break;

            CompoundTag itemTag = itemConfigData.getCompound(i);
            String itemId = itemTag.getString("itemId");
            ListTag attrs = itemTag.getList("attributes", 10);
            boolean isSelected = (i == selectedItemIndex);

            boolean itemHovered = mouseInClip
                    && mouseX >= panelX + 5 && mouseX < panelX + panelWidth - 5
                    && mouseY >= y && mouseY < y + rowHeight;

            if (isSelected) {
                renderer.drawSelectedRow(guiGraphics, panelX + 5, y, panelWidth - 10, rowHeight);
            } else if (itemHovered && !isDraggingItem) {
                renderer.drawSlot(guiGraphics, panelX + 5, y, panelWidth - 10, rowHeight, true);
                hoveredItemIndex = i;
            }

            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
            if (item != null) {
                ItemStack itemStack = new ItemStack(item);
                guiGraphics.renderItem(itemStack, panelX + 10, y + 1);
                String itemName = itemStack.getHoverName().getString();
                guiGraphics.drawString(font, itemName, panelX + 28, y + 3, renderer.getTextColor());
                if (mouseInClip && mouseX >= panelX + 10 && mouseX < panelX + 26 && mouseY >= y + 1 && mouseY < y + 17) {
                    hoveredItemStack = itemStack;
                }
            } else {
                guiGraphics.drawString(font, itemId, panelX + 10, y + 3, renderer.getTextColor());
            }

            int delX = panelX + panelWidth - 22;
            boolean delHovered = mouseInClip && mouseX >= delX && mouseX < delX + 16
                    && mouseY >= y + 1 && mouseY < y + 13;
            if (delHovered && !isDraggingItem) {
                hoveredDelete = true;
                hoveredItemIndex = i;
            }
            guiGraphics.drawString(font, "X", delX + 4, y + 3, delHovered ? renderer.getDeleteColor() : renderer.getHintColor());

            if (isSelected) {
                int attrX = panelX + 28;
                int attrY = y + BASE_ROW_HEIGHT;
                int attrCellMaxWidth = panelWidth - 55;

                // 状态行：左侧特殊机制名称（多个排列），右侧护盾类型按钮（显示当前类型，可点击编辑）
                // 超宽时自动换行：护盾类型按钮移到状态行下一行
                String itemIdForStatus = itemTag.getString("itemId");
                boolean isMechanic = DefsManager.clientIsSpecialMechanic(itemIdForStatus);
                String statusText = buildStatusMechanicText(itemIdForStatus);
                int statusColor = isMechanic ? renderer.getValueColor() : renderer.getHintColor();
                String shieldTypeText = buildShieldTypeText(itemIdForStatus);
                int shieldTextW = font.width(shieldTypeText);
                int statusW = font.width(statusText);

                boolean statusWrap = isStatusWrap(statusText, shieldTypeText);
                int shieldTypeX;
                int shieldTypeY = attrY;
                if (statusWrap) {
                    shieldTypeX = attrX;
                    shieldTypeY = attrY + ATTR_LINE_HEIGHT;
                } else {
                    shieldTypeX = attrX + statusW + 12;
                }

                // 换行时机制文本可能仍超宽（单独顶到滑块），按可用宽度截断加省略号
                String displayStatus = statusWrap
                        ? truncateToWidth(statusText, Math.max(10, statusLineAvailX() - attrX))
                        : statusText;

                // 机制文本悬停可点击（打开该物品的机制数值编辑器）
                boolean mechBtnHovered = isMechanic && mouseInClip
                        && mouseX >= attrX - 2 && mouseX < attrX + font.width(displayStatus) + 2
                        && mouseY >= attrY && mouseY < attrY + ATTR_LINE_HEIGHT - 1;
                guiGraphics.drawString(font, displayStatus, attrX, attrY + 2,
                        mechBtnHovered ? renderer.getAccentColor() : statusColor);
                if (mechBtnHovered) {
                    hoveredMechanicValuesBtn = true;
                    hoveredItemIndex = i;
                }

                // 护盾类型文本（悬停可点击打开选择器，无按钮样式）
                int btnH = ATTR_LINE_HEIGHT - 1;
                boolean typeBtnHovered = mouseInClip
                        && mouseX >= shieldTypeX - 2 && mouseX < shieldTypeX + shieldTextW + 2
                        && mouseY >= shieldTypeY && mouseY < shieldTypeY + btnH;
                guiGraphics.drawString(font, shieldTypeText, shieldTypeX, shieldTypeY + 2,
                        typeBtnHovered ? renderer.getAccentColor() : renderer.getHintColor());
                if (typeBtnHovered) {
                    hoveredShieldTypeBtn = true;
                    hoveredItemIndex = i;
                }

                // 状态行占位：换行时占 2 行（护盾类型按钮在下一行），后续属性区从状态行之后开始
                attrY += statusWrap ? 2 * ATTR_LINE_HEIGHT : ATTR_LINE_HEIGHT;

                if (attrs.isEmpty()) {
                    if (!isSelectingAttr && !isEditing && !isDeletingAttr) {
                        guiGraphics.drawString(font,
                                Component.translatable("screen.gytrinket.no_attributes").getString(),
                                attrX, attrY + 2, renderer.getHintColor());

                        String addText = hasShiftDown()
                                ? Component.translatable("screen.gytrinket.add_mechanic").getString() : "[+]";
                        int hintWidth = font.width(Component.translatable("screen.gytrinket.no_attributes").getString());
                        int btnX = attrX + hintWidth + 8;
                        int btnY = attrY;
                        if (btnY < contentBottom) {
                            boolean addHovered = mouseInClip && mouseX >= btnX && mouseX < btnX + font.width(addText) + 6
                                    && mouseY >= btnY && mouseY < btnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, addText, btnX + 3, btnY + 2, addHovered ? renderer.getAccentColor() : renderer.getHintColor());
                            if (addHovered) {
                                hoveredAddBtn = true;
                                hoveredItemIndex = i;
                            }
                        }

                        String removeText = hasShiftDown()
                                ? Component.translatable("screen.gytrinket.remove_mechanic").getString() : "[-]";
                        int remBtnX = btnX + font.width(addText) + 8;
                        int remBtnY = attrY;
                        if (remBtnY < contentBottom) {
                            boolean remHovered = mouseInClip && mouseX >= remBtnX && mouseX < remBtnX + font.width(removeText) + 6
                                    && mouseY >= remBtnY && mouseY < remBtnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, removeText, remBtnX + 3, remBtnY + 2, remHovered ? renderer.getDeleteColor() : renderer.getHintColor());
                            if (remHovered) {
                                hoveredRemoveBtn = true;
                                hoveredItemIndex = i;
                            }
                        }
                    }
                } else {
                    for (int j = 0; j < attrs.size(); j++) {
                        if (attrY >= contentBottom) break;
                        CompoundTag attr = attrs.getCompound(j);
                        String attrName = attr.getString("name");
                        double attrValue = attr.getDouble("value");
                        String attrText = Component.translatable("tooltip.gytrinket.attr." + attrName).getString()
                                + "=" + formatValue(attrValue);
                        int textWidth = font.width(attrText) + 8;

                        if (isEditing && j == findAttrIndex(attrs, editingAttrName)) {
                            String displayName = Component.translatable("tooltip.gytrinket.attr." + attrName).getString();
                            String editText = displayName + "=" + editingValue + "_";
                            int editTextWidth = font.width(editText) + 8;
                            if (editTextWidth > textWidth) {
                                textWidth = editTextWidth;
                            }
                        }

                        if (attrX + textWidth > panelX + attrCellMaxWidth + 28) {
                            attrX = panelX + 28;
                            attrY += ATTR_LINE_HEIGHT;
                            if (attrY >= contentBottom) break;
                        }

                        boolean attrHovered = mouseInClip
                                && mouseX >= attrX && mouseX < attrX + textWidth
                                && mouseY >= attrY && mouseY < attrY + ATTR_LINE_HEIGHT - 1;

                        renderer.drawAttrCell(guiGraphics, attrX, attrY, textWidth, ATTR_LINE_HEIGHT - 1, attrHovered, isDeletingAttr);

                        if (attrHovered) {
                            hoveredAttrIndex = j;
                            hoveredItemIndex = i;
                        }

                        if (isEditing && j == findAttrIndex(attrs, editingAttrName)) {
                            String displayName = Component.translatable("tooltip.gytrinket.attr." + attrName).getString();
                            String editText = displayName + "=" + editingValue + "_";
                            guiGraphics.drawString(font, editText, attrX + 4, attrY + 2, renderer.getValueColor());
                        } else if (isDeletingAttr) {
                            guiGraphics.drawString(font, attrText, attrX + 4, attrY + 2,
                                    attrHovered ? renderer.getDeleteColor() : renderer.getHintColor());
                        } else {
                            guiGraphics.drawString(font, attrText, attrX + 4, attrY + 2, renderer.getValueColor());
                        }

                        attrX += textWidth + 2;
                    }

                    if (!isSelectingAttr && !isEditing && !isDeletingAttr) {
                        String addText = hasShiftDown()
                                ? Component.translatable("screen.gytrinket.add_mechanic").getString() : "[+]";
                        int btnX = attrX + 2;
                        int btnY = attrY;
                        if (btnX + font.width(addText) + 6 > panelX + panelWidth - 25) {
                            btnX = panelX + 28;
                            btnY += ATTR_LINE_HEIGHT;
                        }
                        if (btnY < contentBottom) {
                            boolean addHovered = mouseInClip && mouseX >= btnX && mouseX < btnX + font.width(addText) + 6
                                    && mouseY >= btnY && mouseY < btnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, addText, btnX + 3, btnY + 2, addHovered ? renderer.getAccentColor() : renderer.getHintColor());
                            if (addHovered) {
                                hoveredAddBtn = true;
                                hoveredItemIndex = i;
                            }
                        }

                        String removeText = hasShiftDown()
                                ? Component.translatable("screen.gytrinket.remove_mechanic").getString() : "[-]";
                        int remBtnX = btnX + font.width(addText) + 8;
                        int remBtnY = btnY;
                        if (remBtnX + font.width(removeText) + 6 > panelX + panelWidth - 25) {
                            remBtnX = panelX + 28;
                            remBtnY += ATTR_LINE_HEIGHT;
                        }
                        if (remBtnY < contentBottom) {
                            boolean remHovered = mouseInClip && mouseX >= remBtnX && mouseX < remBtnX + font.width(removeText) + 6
                                    && mouseY >= remBtnY && mouseY < remBtnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, removeText, remBtnX + 3, remBtnY + 2, remHovered ? renderer.getDeleteColor() : renderer.getHintColor());
                            if (remHovered) {
                                hoveredRemoveBtn = true;
                                hoveredItemIndex = i;
                            }
                        }
                    }
                }
            }

            y += rowHeight;
        }

        if (isDraggingItem && dragTargetIndex >= itemConfigData.size()) {
            int dragRowHeight = calcRowHeight(dragFromIndex);
            guiGraphics.fill(panelX + 5, y, panelX + panelWidth - 5, y + 2, renderer.getAccentColor());
        }

        if (itemConfigData.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_config_items").getString(),
                    panelX + 15, contentY, renderer.getHintColor());
        }

        guiGraphics.disableScissor();

        if (scrollBar.needsScrollbar()) {
            int scrollBarX = panelX + panelWidth - 6;
            int scrollBarHeight = contentBottom - contentY;
            scrollBar.render(guiGraphics, renderer, scrollBarX, contentY, scrollBarHeight, visibleHeight, totalHeight);
        }

        if (isDraggingItem && dragFromIndex >= 0 && dragFromIndex < itemConfigData.size()) {
            renderDraggedRow(guiGraphics, mouseX, mouseY, contentY, contentBottom);
        }
    }

    private int calcDragTargetIndex(int mouseY, int contentY) {
        int y = contentY - scrollBar.getScrollOffset();
        int targetIdx = itemConfigData.size();
        for (int i = 0; i < itemConfigData.size(); i++) {
            int rowHeight = (i == dragFromIndex) ? 0 : calcRowHeight(i);
            int midY = y + rowHeight / 2;
            if (mouseY < midY) {
                targetIdx = i;
                break;
            }
            y += rowHeight;
        }
        if (targetIdx > dragFromIndex) targetIdx--;
        return Math.max(0, Math.min(targetIdx, itemConfigData.size() - 1));
    }

    private void renderDraggedRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int contentY, int contentBottom) {
        CompoundTag itemTag = itemConfigData.getCompound(dragFromIndex);
        String itemId = itemTag.getString("itemId");
        int rowHeight = calcRowHeight(dragFromIndex);

        int dragY = mouseY - rowHeight / 2;
        dragY = Math.max(contentY, Math.min(dragY, contentBottom - rowHeight));

        guiGraphics.fill(panelX + 5, dragY, panelX + panelWidth - 5, dragY + rowHeight, 0xE6283D66);

        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        if (item != null) {
            ItemStack itemStack = new ItemStack(item);
            guiGraphics.renderItem(itemStack, panelX + 10, dragY + 1);
            String itemName = itemStack.getHoverName().getString();
            guiGraphics.drawString(font, itemName, panelX + 28, dragY + 3, renderer.getTextColor());
        } else {
            guiGraphics.drawString(font, itemId, panelX + 10, dragY + 3, renderer.getTextColor());
        }

        guiGraphics.fill(panelX + 5, dragY, panelX + panelWidth - 5, dragY + 2, renderer.getAccentColor());
        guiGraphics.fill(panelX + 5, dragY + rowHeight - 2, panelX + panelWidth - 5, dragY + rowHeight, renderer.getAccentColor());
    }

    private void renderSelectAttrOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int overlayW = 180;
        int overlayH = 140;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, overlayW, overlayH);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, overlayW, overlayH);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.select_attribute").getString(),
                overlayX + 5, overlayY + 5, renderer.getAccentColor());

        Set<String> existingAttrs = getExistingAttrs(selectedItemIndex);

        int listY = overlayY + 18;
        int listBottom = overlayY + overlayH - 10;

        // 只绘制完整行：与点击判定（y+10 <= listBottom）保持一致，
        // 否则底部被裁剪的半行画得出但点不到（点击会直接关闭 overlay 且不添加属性）
        for (int i = selectAttrScrollOffset; i < allAttributeNames.size() && listY + 10 <= listBottom; i++) {
            String attrName = allAttributeNames.get(i);
            String displayName = Component.translatable("tooltip.gytrinket.attr." + attrName).getString();
            boolean alreadyHas = existingAttrs.contains(attrName);
            boolean hovered = mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                    && mouseY >= listY && mouseY < listY + 10;

            if (alreadyHas) {
                guiGraphics.drawString(font, displayName, overlayX + 8, listY, renderer.getHintColor());
                if (hovered) {
                    guiGraphics.drawString(font, " *", overlayX + 8 + font.width(displayName), listY, renderer.getHintColor());
                }
            } else {
                guiGraphics.drawString(font, displayName, overlayX + 8, listY, hovered ? renderer.getValueColor() : renderer.getTextColor());
            }
            listY += 10;
        }

        if (allAttributeNames.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_attributes_registered").getString(), overlayX + 8, listY, renderer.getHintColor());
        }
    }

    private void renderAddItemOverlay(GuiGraphics guiGraphics) {
        int overlayW = 240;
        int overlayH = 150;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, overlayW, overlayH);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, overlayW, overlayH);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.add_item_prompt").getString(),
                overlayX + 8, overlayY + 8, renderer.getAccentColor());
        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.add_item_hint").getString(),
                overlayX + 8, overlayY + overlayH - 10, renderer.getHintColor());

        if (addingItemEditBox != null) {
            addingItemEditBox.render(guiGraphics, 0, 0, 0);
        }

        // 物品注册名实时匹配建议列表（参考原版命令补全交互：Tab/↑↓/点击）
        int listX = overlayX + 8;
        int listY = overlayY + 44;
        for (int i = 0; i < addingSuggestions.size(); i++) {
            boolean selected = i == addingSuggestionIndex;
            guiGraphics.fill(listX - 2, listY + i * 9 - 1, listX + overlayW - 18, listY + i * 9 + 8,
                    selected ? 0xFF2A4A8A : 0x80121A2E);
            guiGraphics.drawString(font, addingSuggestions.get(i), listX, listY + i * 9,
                    selected ? renderer.getValueColor() : renderer.getTextColor());
        }
    }

    /** 护盾类型选择器 overlay：多选；兼容类型可共存，独占（不兼容）类型独占选中；已选行右侧按钮切换兼容/独占 */
    private void renderShieldTypeOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int overlayW = 250;
        int overlayH = 160;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, overlayW, overlayH);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, overlayW, overlayH);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.select_shield_types").getString(),
                overlayX + 5, overlayY + 5, renderer.getAccentColor());

        Map<String, Boolean> allTypes = DefsManager.clientShieldTypes();
        int listY = overlayY + 18;
        int listBottom = overlayY + overlayH - 26; // 底部给"编辑数值"/"保存"按钮留空间
        for (Map.Entry<String, Boolean> e : allTypes.entrySet()) {
            if (listY + 10 > listBottom) break;
            String typeName = e.getKey();
            boolean selected = shieldTypeSelection.contains(typeName);
            boolean hovered = mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                    && mouseY >= listY && mouseY < listY + 10;

            String displayName = getShieldTypeDisplayName(typeName);
            String text = (selected ? "[√] " : "[ ] ") + displayName;

            guiGraphics.drawString(font, text, overlayX + 8, listY,
                    hovered ? renderer.getValueColor() : (selected ? renderer.getValueColor() : renderer.getTextColor()));

            // 已选行右侧的兼容/独占切换按钮
            if (selected) {
                int[] btn = shieldTypeCompatBtnBounds(overlayX, overlayW, typeName);
                boolean btnHovered = mouseX >= btn[0] - 1 && mouseX < btn[0] + btn[1]
                        && mouseY >= listY && mouseY < listY + 10;
                if (btnHovered) {
                    guiGraphics.fill(btn[0] - 1, listY - 1, btn[0] + btn[1], listY + 9, 0xFF2A4A8A);
                }
                guiGraphics.drawString(font, shieldTypeCompatLabel(typeName), btn[0], listY,
                        btnHovered ? renderer.getAccentColor() : renderer.getHintColor());
            }
            listY += 11;
        }
        if (allTypes.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_shield_types").getString(),
                    overlayX + 8, listY, renderer.getHintColor());
        }

        // 底部"编辑数值"按钮：进入所选护盾类型的物品级数值编辑器（未选中任何类型时置灰）
        String editText = Component.translatable("screen.gytrinket.shield_types_edit_values").getString();
        int editX = overlayX + 8;
        int editY = overlayY + overlayH - 24;
        boolean canEdit = !shieldTypeSelection.isEmpty();
        hoveredShieldValuesBtn = canEdit && mouseX >= editX - 2 && mouseX < editX + font.width(editText) + 2
                && mouseY >= editY - 2 && mouseY < editY + 11;
        guiGraphics.drawString(font, editText, editX, editY,
                canEdit ? (hoveredShieldValuesBtn ? renderer.getAccentColor() : renderer.getValueColor())
                        : renderer.getHintColor());

        // 底部"保存"按钮：应用类型/兼容选择（与数值编辑器的保存操作统一；Enter 等效）
        String saveText = Component.translatable("screen.gytrinket.shield_types_save").getString();
        int saveX = overlayX + overlayW - 8 - font.width(saveText);
        hoveredShieldTypeSaveBtn = mouseX >= saveX - 2 && mouseX < saveX + font.width(saveText) + 2
                && mouseY >= editY - 2 && mouseY < editY + 11;
        guiGraphics.drawString(font, saveText, saveX, editY,
                hoveredShieldTypeSaveBtn ? renderer.getAccentColor() : renderer.getValueColor());

        guiGraphics.drawString(font, truncateToWidth(
                Component.translatable("screen.gytrinket.shield_types_hint").getString(), overlayW - 16),
                overlayX + 8, overlayY + overlayH - 12, renderer.getHintColor());
    }

    /** 特殊机制数值编辑器 overlay：机制集合 tab 切换 + 参数行点击编辑 + 重置/保存 */
    /** 特殊机制数值编辑器 overlay 宽度：随机制 tab 总宽自适应（多个机制时加宽，避免 tab 超出边框） */
    private int mechanicValuesOverlayWidth() {
        List<String> sets = DefsManager.clientSpecialMechanicSets(mechanicValuesItemId);
        int tabsWidth = 10; // 左缘 5 + 留白
        for (String set : sets) {
            String label = "[" + DefsManager.clientMechanicDisplayName(set) + "]";
            tabsWidth += font.width(label) + 4;
        }
        // 至少保持基础宽，最多撑满可用屏幕宽（overlay 相对面板居中，超出面板宽度也可完整显示）
        int cap = Math.max(MECHANIC_VALUES_OVERLAY_W, this.width - 12);
        return Math.max(MECHANIC_VALUES_OVERLAY_W, Math.min(tabsWidth + 8, cap));
    }

    private void renderMechanicValuesOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int overlayW = mechanicValuesOverlayWidth();
        int overlayH = MECHANIC_VALUES_OVERLAY_H;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, overlayW, overlayH);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, overlayW, overlayH);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.mechanic_values_title").getString(),
                overlayX + 5, overlayY + 5, renderer.getAccentColor());

        // 机制集合 tab 行：物品声明的多个机制，点击切换编辑目标（各机制的数值互相独立）
        List<String> sets = DefsManager.clientSpecialMechanicSets(mechanicValuesItemId);
        int tabX = overlayX + 5;
        for (String set : sets) {
            String label = "[" + DefsManager.clientMechanicDisplayName(set) + "]";
            boolean active = set.equals(mechanicValuesSet);
            boolean tabHovered = mouseX >= tabX && mouseX < tabX + font.width(label)
                    && mouseY >= overlayY + MECHANIC_VALUES_TAB_Y && mouseY < overlayY + MECHANIC_VALUES_TAB_Y + 11;
            guiGraphics.drawString(font, label, tabX, overlayY + MECHANIC_VALUES_TAB_Y,
                    active ? renderer.getValueColor() : (tabHovered ? renderer.getAccentColor() : renderer.getHintColor()));
            tabX += font.width(label) + 4;
        }

        // 参数行：覆盖值高亮；未覆盖显示 Config 默认值（带"默认"标记）
        List<MechanicValueDefs.ParamDef> defs = MechanicValueDefs.getParams(mechanicValuesSet);
        int rowY = overlayY + MECHANIC_VALUES_PARAMS_TOP;
        if (defs.isEmpty()) {
            // 该机制未注册数值参数：明确提示，避免误以为点击无效
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.mechanic_values_no_params").getString(),
                    overlayX + 8, rowY + 4, renderer.getHintColor());
        }
        for (int idx = 0; idx < defs.size(); idx++) {
            MechanicValueDefs.ParamDef def = defs.get(idx);
            String name = mechanicValueDisplayName(def);
            String valueText;
            int valueColor;
            if (idx == mechanicValueEditIndex) {
                valueText = mechanicValueEditBuffer + "_";
                valueColor = renderer.getAccentColor();
            } else if (mechanicValuesOverridden.contains(def.key())) {
                valueText = mechanicValuesDraft.getOrDefault(def.key(), "");
                valueColor = renderer.getValueColor();
            } else {
                valueText = formatValue(def.defaultValue())
                        + Component.translatable("screen.gytrinket.mechanic_value_default_tag").getString();
                valueColor = renderer.getHintColor();
            }

            boolean rowHovered = mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                    && mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H;
            if (rowHovered) {
                hoveredMechanicValueRow = idx;
                guiGraphics.fill(overlayX + 4, rowY - 1, overlayX + overlayW - 4,
                        rowY + MECHANIC_VALUES_ROW_H - 1, 0xFF2A4A8A);
            }
            guiGraphics.drawString(font, name + "=", overlayX + 8, rowY + 2, renderer.getTextColor());
            guiGraphics.drawString(font, valueText, overlayX + 10 + font.width(name + "="), rowY + 2, valueColor);

            // 行尾 [叠/单] 切换 + [重置]：仅已覆盖参数显示（叠 = 多物品求和，单 = 各物品独立比对取最大）
            // 实例化机制（反射护盾/无人机模块）参数按物品独立解析，叠/单无意义，仅显示 [重置]
            if (mechanicValuesOverridden.contains(def.key())) {
                String resetText = Component.translatable("screen.gytrinket.mechanic_value_reset_btn").getString();
                int resetW = font.width(resetText);
                int resetX = overlayX + overlayW - 8 - resetW;
                if (!MechanicValueDefs.isInstanceSet(mechanicValuesSet)) {
                    boolean stackable = mechanicValuesStackable.getOrDefault(def.key(), Boolean.TRUE);
                    String stackText = Component.translatable(stackable
                            ? "screen.gytrinket.mechanic_value_stackable" : "screen.gytrinket.mechanic_value_non_stackable").getString();
                    int stackX = resetX - font.width(stackText) - 6;
                    boolean stackHovered = mouseX >= stackX - 1 && mouseX < stackX + font.width(stackText) + 1
                            && mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H;
                    guiGraphics.drawString(font, stackText, stackX, rowY + 2,
                            stackHovered ? renderer.getAccentColor() : (stackable ? renderer.getValueColor() : renderer.getHintColor()));
                }
                boolean resetHovered = mouseX >= resetX - 1 && mouseX < resetX + resetW + 1
                        && mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H;
                guiGraphics.drawString(font, resetText, resetX, rowY + 2,
                        resetHovered ? renderer.getDeleteColor() : renderer.getHintColor());
            }
            rowY += MECHANIC_VALUES_ROW_H;
        }

        // 底部保存按钮 + 提示
        String saveText = Component.translatable("screen.gytrinket.mechanic_values_save").getString();
        int saveX = overlayX + 8;
        int saveY = overlayY + overlayH - MECHANIC_VALUES_SAVE_FROM_BOTTOM;
        boolean saveHovered = mouseX >= saveX - 2 && mouseX < saveX + font.width(saveText) + 2
                && mouseY >= saveY - 2 && mouseY < saveY + 11;
        guiGraphics.drawString(font, saveText, saveX, saveY,
                saveHovered ? renderer.getAccentColor() : renderer.getValueColor());

        // 提示按机制类型区分：实例化机制无叠/单切换
        String hintKey = MechanicValueDefs.isInstanceSet(mechanicValuesSet)
                ? "screen.gytrinket.mechanic_values_hint_instance" : "screen.gytrinket.mechanic_values_hint";
        guiGraphics.drawString(font, truncateToWidth(
                Component.translatable(hintKey).getString(), overlayW - 16),
                overlayX + 8, overlayY + overlayH - 12, renderer.getHintColor());
    }

    /** 护盾类型数值编辑器 overlay：护盾类型 tab 切换 + 参数行点击编辑 + 重置/保存（布局与机制数值编辑器一致） */
    private void renderShieldValuesOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int overlayW = MECHANIC_VALUES_OVERLAY_W;
        int overlayH = MECHANIC_VALUES_OVERLAY_H;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, overlayW, overlayH);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, overlayW, overlayH);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.shield_values_title").getString(),
                overlayX + 5, overlayY + 5, renderer.getAccentColor());

        // 护盾类型 tab 行：物品声明的多个护盾类型，点击切换编辑目标（各类型的数值互相独立）
        List<String> types = shieldValuesTabTypes();
        int tabX = overlayX + 5;
        for (String type : types) {
            String label = "[" + getShieldTypeDisplayName(type) + "]";
            boolean active = type.equals(shieldValuesType);
            boolean tabHovered = mouseX >= tabX && mouseX < tabX + font.width(label)
                    && mouseY >= overlayY + MECHANIC_VALUES_TAB_Y && mouseY < overlayY + MECHANIC_VALUES_TAB_Y + 11;
            guiGraphics.drawString(font, label, tabX, overlayY + MECHANIC_VALUES_TAB_Y,
                    active ? renderer.getValueColor() : (tabHovered ? renderer.getAccentColor() : renderer.getHintColor()));
            tabX += font.width(label) + 4;
        }

        // 参数行：覆盖值高亮；未覆盖显示 Config 默认值（带"默认"标记）
        List<ShieldValueDefs.ParamDef> defs = ShieldValueDefs.getParams(shieldValuesType);
        int rowY = overlayY + MECHANIC_VALUES_PARAMS_TOP;
        if (defs.isEmpty()) {
            // 该类型未注册数值参数：明确提示，避免误以为点击无效（占一行，让穿盾行落到下一行，不与提示叠字）
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.shield_values_no_params").getString(),
                    overlayX + 8, rowY + 4, renderer.getHintColor());
            rowY += MECHANIC_VALUES_ROW_H;
        }
        for (int idx = 0; idx < defs.size(); idx++) {
            ShieldValueDefs.ParamDef def = defs.get(idx);
            String name = shieldValueDisplayName(def);
            String valueText;
            int valueColor;
            if (idx == shieldValueEditIndex) {
                valueText = shieldValueEditBuffer + "_";
                valueColor = renderer.getAccentColor();
            } else if (shieldValuesOverridden.contains(def.key())) {
                valueText = shieldValuesDraft.getOrDefault(def.key(), "");
                valueColor = renderer.getValueColor();
            } else {
                valueText = formatValue(def.defaultValue())
                        + Component.translatable("screen.gytrinket.shield_value_default_tag").getString();
                valueColor = renderer.getHintColor();
            }

            boolean rowHovered = mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                    && mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H;
            if (rowHovered) {
                hoveredShieldValueRow = idx;
                guiGraphics.fill(overlayX + 4, rowY - 1, overlayX + overlayW - 4,
                        rowY + MECHANIC_VALUES_ROW_H - 1, 0xFF2A4A8A);
            }
            guiGraphics.drawString(font, name + "=", overlayX + 8, rowY + 2, renderer.getTextColor());
            guiGraphics.drawString(font, valueText, overlayX + 10 + font.width(name + "="), rowY + 2, valueColor);

            // 行尾 [重置]：仅已覆盖参数显示
            if (shieldValuesOverridden.contains(def.key())) {
                String resetText = Component.translatable("screen.gytrinket.shield_value_reset_btn").getString();
                int resetW = font.width(resetText);
                int resetX = overlayX + overlayW - 8 - resetW;
                boolean resetHovered = mouseX >= resetX - 1 && mouseX < resetX + resetW + 1
                        && mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H;
                guiGraphics.drawString(font, resetText, resetX, rowY + 2,
                        resetHovered ? renderer.getDeleteColor() : renderer.getHintColor());
            }
            rowY += MECHANIC_VALUES_ROW_H;
        }

        // 穿盾开关行：护盾耗尽时溢出伤害是否作用于玩家（多类型实际生效时任一不穿盾则全部不穿盾）
        String pierceLabel = Component.translatable("screen.gytrinket.shield_pierce_toggle").getString()
                + "=" + Component.translatable(shieldValuesPierce
                        ? "screen.gytrinket.shield_pierce_on" : "screen.gytrinket.shield_pierce_off").getString();
        boolean pierceHovered = mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                && mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H;
        if (pierceHovered) {
            guiGraphics.fill(overlayX + 4, rowY - 1, overlayX + overlayW - 4,
                    rowY + MECHANIC_VALUES_ROW_H - 1, 0xFF2A4A8A);
        }
        guiGraphics.drawString(font, pierceLabel, overlayX + 8, rowY + 2,
                shieldValuesPierce ? renderer.getValueColor() : renderer.getHintColor());

        // 底部保存按钮 + 提示
        String saveText = Component.translatable("screen.gytrinket.shield_values_save").getString();
        int saveX = overlayX + 8;
        int saveY = overlayY + overlayH - MECHANIC_VALUES_SAVE_FROM_BOTTOM;
        boolean saveHovered = mouseX >= saveX - 2 && mouseX < saveX + font.width(saveText) + 2
                && mouseY >= saveY - 2 && mouseY < saveY + 11;
        guiGraphics.drawString(font, saveText, saveX, saveY,
                saveHovered ? renderer.getAccentColor() : renderer.getValueColor());

        guiGraphics.drawString(font, truncateToWidth(
                Component.translatable("screen.gytrinket.shield_values_hint").getString(), overlayW - 16),
                overlayX + 8, overlayY + overlayH - 12, renderer.getHintColor());
    }

    private String getShieldTypeDisplayName(String typeName) {
        String key = "tooltip.gytrinket.shield_type." + typeName;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? typeName : translated;
    }

    /** 兼容/独占切换按钮文案（随该类型在本物品上的开关状态变化） */
    private String shieldTypeCompatLabel(String typeName) {
        return Component.translatable(shieldTypeCompat.getOrDefault(typeName, Boolean.TRUE)
                ? "screen.gytrinket.shield_type_compat_btn"
                : "screen.gytrinket.shield_type_exclusive_btn").getString();
    }

    /** 已选中类型行右侧兼容/独占按钮的区域（渲染与点击共用）；返回 {x, width}，右缘距 overlay 右侧 8px */
    private int[] shieldTypeCompatBtnBounds(int overlayX, int overlayW, String typeName) {
        int width = font.width(shieldTypeCompatLabel(typeName)) + 4;
        return new int[]{overlayX + overlayW - 8 - width, width};
    }

    /** 切换护盾类型选择：兼容类型可共存；独占类型独占选中（清空其他选择） */
    private void toggleShieldType(String typeName) {
        if (shieldTypeSelection.contains(typeName)) {
            shieldTypeSelection.remove(typeName);
            shieldTypeCompat.remove(typeName);
            return;
        }
        // 保持"独占类型独占选中"不变量：选择新类型时，先移除当前开关为不兼容的类型
        shieldTypeSelection.removeIf(t -> !shieldTypeCompat.getOrDefault(t, Boolean.TRUE));
        shieldTypeSelection.add(typeName);
        // 新选类型的开关初值取类型级默认（此前被翻转过的沿用已存值）
        shieldTypeCompat.putIfAbsent(typeName, defaultShieldTypeCompat(typeName));
    }

    /** 切换已选类型的兼容开关；翻为独占时保持"独占类型独占选中"不变量 */
    private void toggleShieldTypeCompat(String typeName) {
        if (!shieldTypeSelection.contains(typeName)) {
            return;
        }
        boolean nowCompatible = !shieldTypeCompat.getOrDefault(typeName, Boolean.TRUE);
        shieldTypeCompat.put(typeName, nowCompatible);
        if (!nowCompatible) {
            shieldTypeSelection.removeIf(t -> !t.equals(typeName));
            shieldTypeCompat.keySet().removeIf(t -> !t.equals(typeName));
            shieldTypeCompat.put(typeName, false);
        }
    }

    /** 类型级兼容默认值（datapack 定义，缺省视为兼容） */
    private boolean defaultShieldTypeCompat(String typeName) {
        return DefsManager.clientShieldTypes().getOrDefault(typeName, Boolean.TRUE);
    }

    /** 特殊机制选择器 overlay：单击选择即发送（添加/移除指定机制），支持鼠标滚轮 */
    private void renderMechanicOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int overlayX = panelX + panelWidth / 2 - MECHANIC_OVERLAY_W / 2;
        int overlayY = panelY + panelHeight / 2 - MECHANIC_OVERLAY_H / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, MECHANIC_OVERLAY_W, MECHANIC_OVERLAY_H);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, MECHANIC_OVERLAY_W, MECHANIC_OVERLAY_H);

        String titleKey = selectingMechanicAdd
                ? "screen.gytrinket.select_mechanic_add" : "screen.gytrinket.select_mechanic_remove";
        guiGraphics.drawString(font, Component.translatable(titleKey).getString(),
                overlayX + 5, overlayY + 5, renderer.getAccentColor());

        int listY = overlayY + MECHANIC_LIST_TOP;
        int listBottom = overlayY + MECHANIC_OVERLAY_H - MECHANIC_LIST_BOTTOM_MARGIN;

        // 右侧滑块（像素单位，与 mechanicScrollOffset 同步；可拖动滚动列表）
        int visibleRows = mechanicVisibleRows();
        int listHeight = listBottom - listY;
        int totalPx = mechanicPickNames.size() * MECHANIC_ROW_HEIGHT;
        int visiblePx = visibleRows * MECHANIC_ROW_HEIGHT;
        mechanicScrollBar.setScrollOffset(mechanicScrollOffset * MECHANIC_ROW_HEIGHT);
        mechanicScrollBar.updateMaxScroll(totalPx, visiblePx);
        mechanicScrollBar.render(guiGraphics, renderer,
                overlayX + MECHANIC_OVERLAY_W - 6, listY, listHeight, visiblePx, totalPx);

        int drawn = 0;
        for (int i = mechanicScrollOffset; i < mechanicPickNames.size(); i++) {
            if (listY + MECHANIC_ROW_HEIGHT > listBottom) break;
            boolean hovered = mouseX >= overlayX + 5 && mouseX < overlayX + MECHANIC_OVERLAY_W - 5
                    && mouseY >= listY && mouseY < listY + 10;
            guiGraphics.drawString(font, mechanicPickNames.get(i), overlayX + 8, listY,
                    hovered ? renderer.getValueColor() : renderer.getTextColor());
            listY += MECHANIC_ROW_HEIGHT;
            drawn++;
        }

        if (mechanicPickNames.isEmpty()) {
            String emptyKey = selectingMechanicAdd
                    ? "screen.gytrinket.no_mechanic_to_add" : "screen.gytrinket.no_mechanic_to_remove";
            guiGraphics.drawString(font, Component.translatable(emptyKey).getString(),
                    overlayX + 8, overlayY + MECHANIC_LIST_TOP, renderer.getHintColor());
        } else if (mechanicPickNames.size() > mechanicScrollOffset + drawn) {
            // 下方还有未显示的条目：提示可用滚轮
            guiGraphics.drawString(font, "▼ " + (mechanicPickNames.size() - (mechanicScrollOffset + drawn)) + " ▼",
                    overlayX + 8, listY + 2, renderer.getHintColor());
        }

        guiGraphics.drawString(font, truncateToWidth(
                Component.translatable("screen.gytrinket.mechanic_pick_hint").getString(), MECHANIC_OVERLAY_W - 16),
                overlayX + 8, overlayY + MECHANIC_OVERLAY_H - 12, renderer.getHintColor());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (scrollBar.needsScrollbar()) {
            int contentY = panelY + 20;
            int contentBottom = panelY + panelHeight - 6;
            int scrollBarX = panelX + panelWidth - 6;
            int scrollBarHeight = contentBottom - contentY;
            int totalHeight = calcTotalHeight();
            int visibleHeight = contentBottom - contentY;
            if (scrollBar.mouseClicked(mouseX, mouseY, scrollBarX, contentY, scrollBarHeight, visibleHeight, totalHeight)) {
                return true;
            }
        }

        if (isAddingItem) {
            int overlayW = 240;
            int overlayH = 150;
            int overlayX = panelX + panelWidth / 2 - overlayW / 2;
            int overlayY = panelY + panelHeight / 2 - overlayH / 2;
            // 点击建议项：填入该物品注册名
            int listX = overlayX + 8;
            int listY = overlayY + 44;
            for (int i = 0; i < addingSuggestions.size(); i++) {
                if (mouseX >= listX - 2 && mouseX < listX + overlayW - 16
                        && mouseY >= listY + i * 9 - 1 && mouseY < listY + i * 9 + 8) {
                    addingItemId = addingSuggestions.get(i);
                    if (addingItemEditBox != null) {
                        addingItemEditBox.setValue(addingItemId);
                    }
                    updateAddingSuggestions();
                    return true;
                }
            }
            // 仅点击输入框本身时才转发给 EditBox（光标定位）；
            // 框外点击不转发，避免原版 EditBox 检测到「点击不在自身范围」而静默失焦，
            // 导致后续键盘输入（含 Ctrl+V 粘贴）全部失效
            if (addingItemEditBox != null) {
                boolean inBox = mouseX >= addingItemEditBox.getX()
                        && mouseX < addingItemEditBox.getX() + addingItemEditBox.getWidth()
                        && mouseY >= addingItemEditBox.getY()
                        && mouseY < addingItemEditBox.getY() + addingItemEditBox.getHeight();
                if (inBox) {
                    addingItemEditBox.setFocused(true);
                    addingItemEditBox.mouseClicked(mouseX, mouseY, button);
                }
            }
            // 点击 overlay 外部：取消
            if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                closeAddItemInput();
            }
            return true;
        }

        if (isSelectingAttr) {
            int overlayW = 180;
            int overlayH = 140;
            int overlayX = panelX + panelWidth / 2 - overlayW / 2;
            int overlayY = panelY + panelHeight / 2 - overlayH / 2;
            int listY = overlayY + 18;
            int listBottom = overlayY + overlayH - 10;

            if (mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                    && mouseY >= listY && mouseY < listBottom) {
                Set<String> existingAttrs = getExistingAttrs(selectedItemIndex);
                int idx = selectAttrScrollOffset;
                int y = listY;
                while (y + 10 <= listBottom && idx < allAttributeNames.size()) {
                    if (mouseY >= y && mouseY < y + 10) {
                        String attrName = allAttributeNames.get(idx);
                        if (!existingAttrs.contains(attrName)) {
                            addAttributeLocally(attrName, 0);
                            isSelectingAttr = false;

                            editingAttrName = attrName;
                            editingValue = "0";
                            isEditing = true;
                            isNewAttribute = true;
                            return true;
                        }
                        break;
                    }
                    y += 10;
                    idx++;
                }
            }
            isSelectingAttr = false;
            return true;
        }

        if (isEditing) {
            finishEditing();
            return true;
        }

        if (isDeletingAttr) {
            if (hoveredAttrIndex >= 0 && hoveredItemIndex >= 0 && hoveredItemIndex == selectedItemIndex) {
                CompoundTag itemTag = itemConfigData.getCompound(selectedItemIndex);
                String itemId = itemTag.getString("itemId");
                ListTag attrs = itemTag.getList("attributes", 10);
                if (hoveredAttrIndex < attrs.size()) {
                    String attrName = attrs.getCompound(hoveredAttrIndex).getString("name");
                    attrs.remove(hoveredAttrIndex);
                    itemTag.put("attributes", attrs);
                    NetworkHandler.INSTANCE.sendToServer(
                        new ConfigRemoveAttrMessage(itemId, attrName));
                }
                isDeletingAttr = false;
                return true;
            }
            isDeletingAttr = false;
            return true;
        }

        if (isSelectingShieldTypes) {
            int overlayW = 250;
            int overlayH = 160;
            int overlayX = panelX + panelWidth / 2 - overlayW / 2;
            int overlayY = panelY + panelHeight / 2 - overlayH / 2;
            int listY = overlayY + 18;
            int listBottom = overlayY + overlayH - 26;
            Map<String, Boolean> allTypes = DefsManager.clientShieldTypes();
            for (Map.Entry<String, Boolean> e : allTypes.entrySet()) {
                if (listY + 10 > listBottom) break;
                if (mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                        && mouseY >= listY && mouseY < listY + 10) {
                    String typeName = e.getKey();
                    // 已选行右侧命中兼容/独占按钮时切换开关，否则切换选择
                    if (shieldTypeSelection.contains(typeName)) {
                        int[] btn = shieldTypeCompatBtnBounds(overlayX, overlayW, typeName);
                        if (mouseX >= btn[0] - 1) {
                            toggleShieldTypeCompat(typeName);
                        } else {
                            toggleShieldType(typeName);
                        }
                    } else {
                        toggleShieldType(typeName);
                    }
                    return true;
                }
                listY += 11;
            }
            // 底部"保存"按钮：应用类型/兼容选择（与数值编辑器的保存操作统一）
            if (hoveredShieldTypeSaveBtn) {
                applyShieldTypeSelection();
                return true;
            }
            // 底部"编辑数值"按钮：先自动保存当前类型/兼容选择（含未按过保存的修改），
            // 再进入所选护盾类型的物品级数值编辑器（tab 用本地选择，避免等待服务端同步）
            if (hoveredShieldValuesBtn && selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
                String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
                List<String> chosen = new ArrayList<>(shieldTypeSelection);
                applyShieldTypeSelection();
                openShieldValuesEditor(itemId, chosen);
                return true;
            }
            // 点击 overlay 外部：取消
            if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                isSelectingShieldTypes = false;
                shieldTypeSelection.clear();
                shieldTypeCompat.clear();
            }
            return true;
        }

        if (isSelectingMechanic) {
            int overlayX = panelX + panelWidth / 2 - MECHANIC_OVERLAY_W / 2;
            int overlayY = panelY + panelHeight / 2 - MECHANIC_OVERLAY_H / 2;
            int listY = overlayY + MECHANIC_LIST_TOP;
            int listBottom = overlayY + MECHANIC_OVERLAY_H - MECHANIC_LIST_BOTTOM_MARGIN;
            int visibleRows = mechanicVisibleRows();
            int listHeight = listBottom - listY;
            int totalPx = mechanicPickList.size() * MECHANIC_ROW_HEIGHT;
            int visiblePx = visibleRows * MECHANIC_ROW_HEIGHT;
            // 点击滑块：开始拖动
            mechanicScrollBar.setScrollOffset(mechanicScrollOffset * MECHANIC_ROW_HEIGHT);
            mechanicScrollBar.updateMaxScroll(totalPx, visiblePx);
            if (mechanicScrollBar.mouseClicked(mouseX, mouseY,
                    overlayX + MECHANIC_OVERLAY_W - 6, listY, listHeight, visiblePx, totalPx)) {
                return true;
            }
            for (int i = mechanicScrollOffset; i < mechanicPickList.size(); i++) {
                if (listY + MECHANIC_ROW_HEIGHT > listBottom) break;
                if (mouseX >= overlayX + 5 && mouseX < overlayX + MECHANIC_OVERLAY_W - 5
                        && mouseY >= listY && mouseY < listY + 10) {
                    // 单击选择：发送添加/移除该机制
                    if (selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
                        String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
                        NetworkHandler.INSTANCE.sendToServer(new ConfigSpecialMechanicMessage(
                                selectingMechanicAdd ? "set" : "remove", itemId, mechanicPickList.get(i)));
                    }
                    isSelectingMechanic = false;
                    return true;
                }
                listY += MECHANIC_ROW_HEIGHT;
            }
            // 点击 overlay 外部：取消
            if (mouseX < overlayX || mouseX >= overlayX + MECHANIC_OVERLAY_W || mouseY < overlayY || mouseY >= overlayY + MECHANIC_OVERLAY_H) {
                isSelectingMechanic = false;
            }
            return true;
        }

        if (isEditingMechanicValues) {
            int overlayW = mechanicValuesOverlayWidth();
            int overlayH = MECHANIC_VALUES_OVERLAY_H;
            int overlayX = panelX + panelWidth / 2 - overlayW / 2;
            int overlayY = panelY + panelHeight / 2 - overlayH / 2;

            // 点击 overlay 外部：关闭（不保存）
            if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                closeMechanicValuesEditor();
                return true;
            }

            // 机制集合 tab 切换
            List<String> sets = DefsManager.clientSpecialMechanicSets(mechanicValuesItemId);
            int tabX = overlayX + 5;
            for (String set : sets) {
                String label = "[" + DefsManager.clientMechanicDisplayName(set) + "]";
                if (mouseX >= tabX && mouseX < tabX + font.width(label)
                        && mouseY >= overlayY + MECHANIC_VALUES_TAB_Y && mouseY < overlayY + MECHANIC_VALUES_TAB_Y + 11) {
                    if (!set.equals(mechanicValuesSet)) {
                        rebuildMechanicValuesDraft(set);
                    }
                    return true;
                }
                tabX += font.width(label) + 4;
            }

            // 保存按钮
            String saveText = Component.translatable("screen.gytrinket.mechanic_values_save").getString();
            int saveX = overlayX + 8;
            int saveY = overlayY + overlayH - MECHANIC_VALUES_SAVE_FROM_BOTTOM;
            if (mouseX >= saveX - 2 && mouseX < saveX + font.width(saveText) + 2
                    && mouseY >= saveY - 2 && mouseY < saveY + 11) {
                saveMechanicValues();
                return true;
            }

            // 参数行：先判行尾 [叠/单]，再判 [重置]，最后判行（进入编辑）
            List<MechanicValueDefs.ParamDef> defs = MechanicValueDefs.getParams(mechanicValuesSet);
            int rowY = overlayY + MECHANIC_VALUES_PARAMS_TOP;
            for (int idx = 0; idx < defs.size(); idx++) {
                MechanicValueDefs.ParamDef def = defs.get(idx);
                if (mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H) {
                    if (mechanicValuesOverridden.contains(def.key())) {
                        String resetText = Component.translatable("screen.gytrinket.mechanic_value_reset_btn").getString();
                        int resetX = overlayX + overlayW - 8 - font.width(resetText);
                        if (!MechanicValueDefs.isInstanceSet(mechanicValuesSet)) {
                            boolean stackable = mechanicValuesStackable.getOrDefault(def.key(), Boolean.TRUE);
                            String stackText = Component.translatable(stackable
                                    ? "screen.gytrinket.mechanic_value_stackable" : "screen.gytrinket.mechanic_value_non_stackable").getString();
                            int stackX = resetX - font.width(stackText) - 6;
                            if (mouseX >= stackX - 1 && mouseX < stackX + font.width(stackText) + 1) {
                                toggleMechanicValueStackable(def.key());
                                return true;
                            }
                        }
                        if (mouseX >= resetX - 1) {
                            resetMechanicValue(def.key());
                            return true;
                        }
                    }
                    if (mechanicValueEditIndex != idx) {
                        confirmMechanicValueEdit();
                        mechanicValueEditIndex = idx;
                        mechanicValueEditBuffer = mechanicValuesDraft.getOrDefault(def.key(), "");
                    }
                    return true;
                }
                rowY += MECHANIC_VALUES_ROW_H;
            }
            return true;
        }

        if (isEditingShieldValues) {
            int overlayW = MECHANIC_VALUES_OVERLAY_W;
            int overlayH = MECHANIC_VALUES_OVERLAY_H;
            int overlayX = panelX + panelWidth / 2 - overlayW / 2;
            int overlayY = panelY + panelHeight / 2 - overlayH / 2;

            // 点击 overlay 外部：关闭（不保存）
            if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                closeShieldValuesEditor();
                return true;
            }

            // 护盾类型 tab 切换
            List<String> types = shieldValuesTabTypes();
            int tabX = overlayX + 5;
            for (String type : types) {
                String label = "[" + getShieldTypeDisplayName(type) + "]";
                if (mouseX >= tabX && mouseX < tabX + font.width(label)
                        && mouseY >= overlayY + MECHANIC_VALUES_TAB_Y && mouseY < overlayY + MECHANIC_VALUES_TAB_Y + 11) {
                    if (!type.equals(shieldValuesType)) {
                        rebuildShieldValuesDraft(type);
                    }
                    return true;
                }
                tabX += font.width(label) + 4;
            }

            // 保存按钮
            String saveText = Component.translatable("screen.gytrinket.shield_values_save").getString();
            int saveX = overlayX + 8;
            int saveY = overlayY + overlayH - MECHANIC_VALUES_SAVE_FROM_BOTTOM;
            if (mouseX >= saveX - 2 && mouseX < saveX + font.width(saveText) + 2
                    && mouseY >= saveY - 2 && mouseY < saveY + 11) {
                saveShieldValues();
                return true;
            }

            // 参数行：先判行尾 [重置]，再判行（进入编辑）
            List<ShieldValueDefs.ParamDef> defs = ShieldValueDefs.getParams(shieldValuesType);
            int rowY = overlayY + MECHANIC_VALUES_PARAMS_TOP;
            if (defs.isEmpty()) {
                // 与渲染一致：无参数类型提示占一行，穿盾行落到下一行
                rowY += MECHANIC_VALUES_ROW_H;
            }
            for (int idx = 0; idx < defs.size(); idx++) {
                ShieldValueDefs.ParamDef def = defs.get(idx);
                if (mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H) {
                    if (shieldValuesOverridden.contains(def.key())) {
                        String resetText = Component.translatable("screen.gytrinket.shield_value_reset_btn").getString();
                        int resetX = overlayX + overlayW - 8 - font.width(resetText);
                        if (mouseX >= resetX - 1) {
                            resetShieldValue(def.key());
                            return true;
                        }
                    }
                    if (shieldValueEditIndex != idx) {
                        confirmShieldValueEdit();
                        shieldValueEditIndex = idx;
                        shieldValueEditBuffer = shieldValuesDraft.getOrDefault(def.key(), "");
                    }
                    return true;
                }
                rowY += MECHANIC_VALUES_ROW_H;
            }

            // 穿盾开关行
            if (mouseY >= rowY && mouseY < rowY + MECHANIC_VALUES_ROW_H) {
                shieldValuesPierce = !shieldValuesPierce;
            }
            return true;
        }

        // 状态行机制文本（点击打开该物品的特殊机制数值编辑器）
        if (hoveredMechanicValuesBtn && selectedItemIndex >= 0) {
            String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
            openMechanicValuesEditor(itemId);
            return true;
        }

        // 状态行右侧护盾类型按钮（在列表内点击，不会脱离选中）
        if (hoveredShieldTypeBtn && selectedItemIndex >= 0) {
            openShieldTypeSelector();
            return true;
        }

        if (hoveredDelete && hoveredItemIndex >= 0) {
            CompoundTag itemTag = itemConfigData.getCompound(hoveredItemIndex);
            String itemId = itemTag.getString("itemId");
            NetworkHandler.INSTANCE.sendToServer(new ConfigDeleteItemMessage(itemId));
            itemConfigData.remove(hoveredItemIndex);
            if (selectedItemIndex == hoveredItemIndex) selectedItemIndex = -1;
            else if (selectedItemIndex > hoveredItemIndex) selectedItemIndex--;
            return true;
        }

        // Shift+[+]：打开特殊机制添加选择器
        if (hoveredAddBtn && hoveredItemIndex >= 0 && hasShiftDown()) {
            selectedItemIndex = hoveredItemIndex;
            openMechanicSelector(true);
            return true;
        }

        if (hoveredAddBtn && hoveredItemIndex >= 0) {
            selectedItemIndex = hoveredItemIndex;
            isSelectingAttr = true;
            selectAttrScrollOffset = 0;
            return true;
        }

        // Shift+[-]：打开特殊机制移除选择器
        if (hoveredRemoveBtn && hoveredItemIndex >= 0 && hasShiftDown()) {
            selectedItemIndex = hoveredItemIndex;
            openMechanicSelector(false);
            return true;
        }

        if (hoveredRemoveBtn && hoveredItemIndex >= 0) {
            selectedItemIndex = hoveredItemIndex;
            isDeletingAttr = true;
            return true;
        }

        if (hoveredItemIndex >= 0) {
            if (hasShiftDown()) {
                isDraggingItem = true;
                dragFromIndex = hoveredItemIndex;
                return true;
            }
            if (hoveredAttrIndex >= 0) {
                selectedItemIndex = hoveredItemIndex;
                CompoundTag itemTag = itemConfigData.getCompound(hoveredItemIndex);
                ListTag attrs = itemTag.getList("attributes", 10);
                if (hoveredAttrIndex < attrs.size()) {
                    CompoundTag attr = attrs.getCompound(hoveredAttrIndex);
                    editingAttrName = attr.getString("name");
                    editingValue = formatValue(attr.getDouble("value"));
                    isEditing = true;
                    isNewAttribute = false;
                }
            } else {
                selectedItemIndex = (selectedItemIndex == hoveredItemIndex) ? -1 : hoveredItemIndex;
            }
            return true;
        }

        // 仅在点击面板内部空白处时清除选中（点击面板外/底部按钮不清除，避免按钮回调时已无选中）
        if (mouseX >= panelX && mouseX < panelX + panelWidth
                && mouseY >= panelY && mouseY < panelY + panelHeight) {
            selectedItemIndex = -1;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingItem) {
            if (dragTargetIndex >= 0 && dragTargetIndex < itemConfigData.size() && dragTargetIndex != dragFromIndex) {
                CompoundTag fromTag = (CompoundTag) itemConfigData.get(dragFromIndex);
                itemConfigData.remove(dragFromIndex);
                int insertIdx = dragTargetIndex;
                if (dragFromIndex < dragTargetIndex) insertIdx--;
                itemConfigData.add(insertIdx, fromTag);

                if (selectedItemIndex == dragFromIndex) selectedItemIndex = insertIdx;
                else if (selectedItemIndex > dragFromIndex && selectedItemIndex <= insertIdx) selectedItemIndex--;
                else if (selectedItemIndex < dragFromIndex && selectedItemIndex >= insertIdx) selectedItemIndex++;

                NetworkHandler.INSTANCE.sendToServer(
                    new ConfigReorderMessage(dragFromIndex, dragTargetIndex));
            }
            isDraggingItem = false;
            dragFromIndex = -1;
            dragTargetIndex = -1;
            return true;
        }
        mechanicScrollBar.mouseReleased();
        scrollBar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mechanicScrollBar.isDraggingScrollbar() && isSelectingMechanic) {
            int overlayY = panelY + panelHeight / 2 - MECHANIC_OVERLAY_H / 2;
            int listY = overlayY + MECHANIC_LIST_TOP;
            int listBottom = overlayY + MECHANIC_OVERLAY_H - MECHANIC_LIST_BOTTOM_MARGIN;
            int visibleRows = mechanicVisibleRows();
            int listHeight = listBottom - listY;
            mechanicScrollBar.mouseDragged(mouseY, listY, listHeight,
                    visibleRows * MECHANIC_ROW_HEIGHT, mechanicPickList.size() * MECHANIC_ROW_HEIGHT);
            mechanicScrollOffset = mechanicScrollBar.getScrollOffset() / MECHANIC_ROW_HEIGHT;
            return true;
        }
        if (scrollBar.isDraggingScrollbar()) {
            int contentY = panelY + 20;
            int contentBottom = panelY + panelHeight - 6;
            int scrollBarHeight = contentBottom - contentY;
            int totalHeight = calcTotalHeight();
            int visibleHeight = contentBottom - contentY;
            scrollBar.mouseDragged(mouseY, contentY, scrollBarHeight, visibleHeight, totalHeight);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void addAttributeLocally(String attrName, double value) {
        if (selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
            CompoundTag itemTag = itemConfigData.getCompound(selectedItemIndex);
            ListTag attrs = itemTag.getList("attributes", 10);
            CompoundTag newAttr = new CompoundTag();
            newAttr.putString("name", attrName);
            newAttr.putDouble("value", value);
            attrs.add(newAttr);
            itemTag.put("attributes", attrs);

            String itemId = itemTag.getString("itemId");
            NetworkHandler.INSTANCE.sendToServer(
                new ConfigUpdateMessage(itemId, attrName, value));
        }
    }

    private String formatValue(double value) {
        // 物品级机制/护盾数值允许输入最多 3 位小数：显示时保留 3 位并去掉末尾多余的 0，整数不带小数点
        if (value == (long) value) return String.valueOf((long) value);
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value)
                .setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private int findAttrIndex(ListTag attrs, String attrName) {
        for (int i = 0; i < attrs.size(); i++) {
            if (attrs.getCompound(i).getString("name").equals(attrName)) {
                return i;
            }
        }
        return -1;
    }

    public void updateData(ListTag newItemConfigData, List<String> newAllAttributeNames) {
        itemConfigData.clear();
        itemConfigData.addAll(newItemConfigData);
        allAttributeNames.clear();
        allAttributeNames.addAll(newAllAttributeNames);
        if (selectedItemIndex < 0 || selectedItemIndex >= itemConfigData.size()) {
            selectedItemIndex = -1;
            isEditing = false;
            isNewAttribute = false;
            editingAttrName = null;
            editingValue = "";
            isSelectingAttr = false;
            isDeletingAttr = false;
            isSelectingShieldTypes = false;
            shieldTypeSelection.clear();
            shieldTypeCompat.clear();
            isSelectingMechanic = false;
        }
        if (isEditing && editingAttrName != null && selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
            CompoundTag itemTag = itemConfigData.getCompound(selectedItemIndex);
            ListTag attrs = itemTag.getList("attributes", 10);
            if (findAttrIndex(attrs, editingAttrName) < 0) {
                isEditing = false;
                isNewAttribute = false;
                editingAttrName = null;
                editingValue = "";
            }
        }
    }
}
