package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.core.defs.DefsManager;
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
        shieldTypeSelection.addAll(DefsManager.clientItemShieldTypes(itemId));
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
            for (String set : DefsManager.clientAllMechanicSets()) {
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 应用护盾类型选择并发送到服务端 */
    private void applyShieldTypeSelection() {
        if (selectedItemIndex >= 0 && selectedItemIndex < itemConfigData.size()) {
            String itemId = itemConfigData.getCompound(selectedItemIndex).getString("itemId");
            NetworkHandler.INSTANCE.sendToServer(new ConfigShieldTypesMessage(itemId, new ArrayList<>(shieldTypeSelection), false));
        }
        isSelectingShieldTypes = false;
        shieldTypeSelection.clear();
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
            selectAttrScrollOffset = Math.max(0, Math.min(selectAttrScrollOffset - step, Math.max(0, allAttributeNames.size() - 10)));
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

        boolean hasOverlay = isSelectingAttr || isAddingItem || isSelectingShieldTypes || isSelectingMechanic;

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
        hoveredItemStack = ItemStack.EMPTY;

        lastMouseY = mouseY;

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

            boolean itemHovered = mouseX >= panelX + 5 && mouseX < panelX + panelWidth - 5
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
                if (mouseX >= panelX + 10 && mouseX < panelX + 26 && mouseY >= y + 1 && mouseY < y + 17) {
                    hoveredItemStack = itemStack;
                }
            } else {
                guiGraphics.drawString(font, itemId, panelX + 10, y + 3, renderer.getTextColor());
            }

            int delX = panelX + panelWidth - 22;
            boolean delHovered = mouseX >= delX && mouseX < delX + 16
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

                guiGraphics.drawString(font, displayStatus, attrX, attrY + 2, statusColor);

                // 护盾类型文本（悬停可点击打开选择器，无按钮样式）
                int btnH = ATTR_LINE_HEIGHT - 1;
                boolean typeBtnHovered = mouseX >= shieldTypeX - 2 && mouseX < shieldTypeX + shieldTextW + 2
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
                            boolean addHovered = mouseX >= btnX && mouseX < btnX + font.width(addText) + 6
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
                            boolean remHovered = mouseX >= remBtnX && mouseX < remBtnX + font.width(removeText) + 6
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

                        boolean attrHovered = mouseX >= attrX && mouseX < attrX + textWidth
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
                            boolean addHovered = mouseX >= btnX && mouseX < btnX + font.width(addText) + 6
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
                            boolean remHovered = mouseX >= remBtnX && mouseX < remBtnX + font.width(removeText) + 6
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

        for (int i = selectAttrScrollOffset; i < allAttributeNames.size() && listY < listBottom; i++) {
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

    /** 护盾类型选择器 overlay：多选，兼容类型可共存，不兼容类型独占（选中时清空其他选择） */
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
        int listBottom = overlayY + overlayH - 14;
        for (Map.Entry<String, Boolean> e : allTypes.entrySet()) {
            if (listY + 10 > listBottom) break;
            String typeName = e.getKey();
            boolean compatible = e.getValue();
            boolean selected = shieldTypeSelection.contains(typeName);
            boolean hovered = mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                    && mouseY >= listY && mouseY < listY + 10;

            String displayName = getShieldTypeDisplayName(typeName);
            String text = (selected ? "[√] " : "[ ] ") + displayName
                    + (compatible ? "" : Component.translatable("screen.gytrinket.shield_type_exclusive").getString());

            guiGraphics.drawString(font, text, overlayX + 8, listY,
                    hovered ? renderer.getValueColor() : (selected ? renderer.getValueColor() : renderer.getTextColor()));
            listY += 11;
        }
        if (allTypes.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_shield_types").getString(),
                    overlayX + 8, listY, renderer.getHintColor());
        }

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.shield_types_hint").getString(),
                overlayX + 8, overlayY + overlayH - 12, renderer.getHintColor());
    }

    private String getShieldTypeDisplayName(String typeName) {
        String key = "tooltip.gytrinket.shield_type." + typeName;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? typeName : translated;
    }

    /** 切换护盾类型选择：兼容类型可共存；不兼容类型独占（清空其他选择）；加入兼容类型时移除独占类型 */
    private void toggleShieldType(String typeName, boolean compatible) {
        if (compatible) {
            if (shieldTypeSelection.contains(typeName)) {
                shieldTypeSelection.remove(typeName);
                return;
            }
            // 保持"不兼容类型独占"不变量：选择兼容类型时，先移除当前独占的不兼容类型
            Map<String, Boolean> allTypes = DefsManager.clientShieldTypes();
            shieldTypeSelection.removeIf(t -> Boolean.FALSE.equals(allTypes.get(t)));
            shieldTypeSelection.add(typeName);
        } else {
            shieldTypeSelection.clear();
            shieldTypeSelection.add(typeName);
        }
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

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.mechanic_pick_hint").getString(),
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
            int listBottom = overlayY + overlayH - 14;
            Map<String, Boolean> allTypes = DefsManager.clientShieldTypes();
            for (Map.Entry<String, Boolean> e : allTypes.entrySet()) {
                if (listY + 10 > listBottom) break;
                if (mouseX >= overlayX + 5 && mouseX < overlayX + overlayW - 5
                        && mouseY >= listY && mouseY < listY + 10) {
                    toggleShieldType(e.getKey(), e.getValue());
                    return true;
                }
                listY += 11;
            }
            // 点击 overlay 外部：取消
            if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                isSelectingShieldTypes = false;
                shieldTypeSelection.clear();
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
        return ScreenUtils.formatValue(value);
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
