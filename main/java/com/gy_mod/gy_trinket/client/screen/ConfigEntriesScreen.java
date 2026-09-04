package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.config.ClientConfig;
import com.gy_mod.gy_trinket.config.ConfigValueRegistry;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.ConfigValueUpdateMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置项界面：在线调整特殊机制（屏障/光环等）、自然恢复系统与客户端 HUD 配置。
 * <p>
 * 两个分组 tab（通用配置/客户端配置）；布尔项点击直接切换，数值项点击弹出编辑框；
 * 服务端项经网络修改并由服务端广播同步，客户端专属项（HUD）本地直接生效。
 */
public class ConfigEntriesScreen extends AbstractPanelScreen {

    private static final int ROW_HEIGHT = 14;

    /** 数值编辑 overlay 布局常量 */
    private static final int OVERLAY_W = 240;
    private static final int OVERLAY_H = 84;
    private static final int OVERLAY_BTN_W = 50;
    private static final int OVERLAY_BTN_H = 14;

    /** 当前分组：false=通用配置，true=客户端配置 */
    private boolean clientGroup = false;

    private final ScrollBarComponent scrollBar = new ScrollBarComponent();
    private int hoveredRowIndex = -1;

    /** 数值编辑 overlay 状态 */
    private boolean isEditing = false;
    private ConfigValueRegistry.Entry editingEntry = null;
    private EditBox valueEditBox = null;
    private boolean hoveredConfirm = false;
    private boolean hoveredCancel = false;
    /** overlay 按钮区域（openEditor 时计算，渲染与点击共用） */
    private int confirmX;
    private int confirmY;
    private int cancelX;
    private int cancelY;

    public ConfigEntriesScreen(Screen parentScreen) {
        super(Component.translatable("screen.gytrinket.config_entries_title"),
                resolveParent(parentScreen), SolidUIRenderer.CONFIG);
    }

    private static Screen resolveParent(Screen parentScreen) {
        Screen actualParent = parentScreen;
        while (actualParent instanceof ConfigEntriesScreen ces) {
            actualParent = ces.getParentScreen();
        }
        return actualParent;
    }

    /** 内容区顶部（标题 + 分组 tab 下方） */
    private int contentY() {
        return panelY + 34;
    }

    /** 内容区底部（面板底边留 6px） */
    private int contentBottom() {
        return panelY + panelHeight - 6;
    }

    /** 当前分组的配置项列表 */
    private List<ConfigValueRegistry.Entry> currentRows() {
        List<ConfigValueRegistry.Entry> rows = new ArrayList<>();
        for (ConfigValueRegistry.Entry e : ConfigValueRegistry.entries()) {
            if (e.clientOnly == clientGroup) {
                rows.add(e);
            }
        }
        return rows;
    }

    /** 服务端广播同步后刷新（值实时读取，仅需重置悬停状态） */
    public void refreshRows() {
        hoveredRowIndex = -1;
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(400, 300, 20, 40);

        int btnY = panelY + panelHeight + 5;
        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ).bounds(panelX + panelWidth - 85, btnY, 80, 16).renderer(renderer).build());
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        renderPanelBackground(guiGraphics, mouseX, mouseY, partialTick);
        drawText(guiGraphics, this.title.getString(), panelX + 8, panelY + 6, renderer.getAccentColor());

        renderTabs(guiGraphics, mouseX, mouseY);

        if (!isEditing) {
            renderContent(guiGraphics, mouseX, mouseY, partialTick);
        }

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (isEditing) {
            renderEditOverlay(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /** 分组 tab（当前组高亮，其余可点击切换） */
    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String[] keys = {
                "config.gytrinket.group.general",
                "config.gytrinket.group.client"
        };
        int tabW = (panelWidth - 20) / 2;
        int tabY = panelY + 17;
        int tabH = 13;
        for (int k = 0; k < keys.length; k++) {
            int tx = panelX + 5 + k * (tabW + 5);
            boolean active = (k == 1) == clientGroup;
            boolean hovered = !isEditing
                    && mouseX >= tx && mouseX < tx + tabW
                    && mouseY >= tabY && mouseY < tabY + tabH;
            if (active) {
                renderer.drawSelectedRow(guiGraphics, tx, tabY, tabW, tabH);
            } else {
                renderer.drawSlot(guiGraphics, tx, tabY, tabW, tabH, hovered);
            }
            String text = Component.translatable(keys[k]).getString();
            guiGraphics.drawString(font, text,
                    tx + (tabW - font.width(text)) / 2, tabY + (tabH - 8) / 2,
                    active ? renderer.getAccentColor()
                            : (hovered ? renderer.getValueColor() : renderer.getTextColor()),
                    false);
        }
    }

    /** 配置项列表（名称左对齐 + 值右对齐，带滚动裁剪） */
    private void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        List<ConfigValueRegistry.Entry> rows = currentRows();
        int top = contentY();
        int bottom = contentBottom();
        int rowX = panelX + 5;
        int rowW = panelWidth - 16;
        int visibleHeight = bottom - top;
        int totalHeight = rows.size() * ROW_HEIGHT;
        scrollBar.updateMaxScroll(totalHeight, visibleHeight);
        int scrollOffset = scrollBar.getScrollOffset();

        hoveredRowIndex = -1;
        boolean mouseInClip = mouseX >= rowX && mouseX < rowX + rowW
                && mouseY >= top && mouseY < bottom;

        guiGraphics.enableScissor(panelX + 1, top, panelX + panelWidth - 1, bottom);
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW_HEIGHT - scrollOffset;
            if (y + ROW_HEIGHT <= top || y >= bottom) {
                continue;
            }
            ConfigValueRegistry.Entry entry = rows.get(i);

            boolean hovered = mouseInClip && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                hoveredRowIndex = i;
                renderer.drawSlot(guiGraphics, rowX, y, rowW, ROW_HEIGHT, true);
            }

            // 名称（左，超宽截断）
            String name = Component.translatable("config.gytrinket.entry." + entry.id).getString();
            name = font.plainSubstrByWidth(name, panelWidth - 130);
            guiGraphics.drawString(font, name, rowX + 4, y + 3,
                    hovered ? renderer.getAccentColor() : renderer.getTextColor(), false);

            // 值（右对齐；客户端专属项带 * 标记）
            String valueText = entry.bool
                    ? Component.translatable(entry.getter.getAsDouble() != 0.0
                            ? "config.gytrinket.value_on" : "config.gytrinket.value_off").getString()
                    : formatDouble(entry.getter.getAsDouble());
            if (entry.clientOnly) {
                valueText = valueText + " *";
            }
            guiGraphics.drawString(font, valueText, rowX + rowW - 4 - font.width(valueText), y + 3,
                    renderer.getValueColor(), false);
        }
        guiGraphics.disableScissor();

        // 悬停行提示：名称 + 注释（desc 键存在时）
        if (hoveredRowIndex >= 0) {
            ConfigValueRegistry.Entry hovered = rows.get(hoveredRowIndex);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("config.gytrinket.entry." + hovered.id));
            String descKey = "config.gytrinket.entry." + hovered.id + ".desc";
            if (I18n.exists(descKey)) {
                tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
            }
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }

        scrollBar.render(guiGraphics, renderer, panelX + panelWidth - 8, top,
                visibleHeight, visibleHeight, totalHeight);
    }

    // ==================== 编辑 overlay ====================

    private void renderEditOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int overlayX = panelX + (panelWidth - OVERLAY_W) / 2;
        int overlayY = panelY + (panelHeight - OVERLAY_H) / 2;
        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, OVERLAY_W, OVERLAY_H);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, OVERLAY_W, OVERLAY_H);

        String name = Component.translatable("config.gytrinket.entry." + editingEntry.id).getString();
        name = font.plainSubstrByWidth(name, OVERLAY_W - 16);
        guiGraphics.drawString(font, name, overlayX + 8, overlayY + 6, renderer.getAccentColor(), false);

        if (valueEditBox != null) {
            valueEditBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        String range = Component.translatable("config.gytrinket.range",
                formatDouble(editingEntry.min), formatDouble(editingEntry.max)).getString();
        range = font.plainSubstrByWidth(range, OVERLAY_W - 16);
        guiGraphics.drawString(font, range, overlayX + 8, overlayY + 33, renderer.getHintColor(), false);

        hoveredConfirm = mouseX >= confirmX && mouseX < confirmX + OVERLAY_BTN_W
                && mouseY >= confirmY && mouseY < confirmY + OVERLAY_BTN_H;
        hoveredCancel = mouseX >= cancelX && mouseX < cancelX + OVERLAY_BTN_W
                && mouseY >= cancelY && mouseY < cancelY + OVERLAY_BTN_H;
        drawOverlayButton(guiGraphics, confirmX, confirmY, OVERLAY_BTN_W, OVERLAY_BTN_H,
                Component.translatable("config.gytrinket.confirm").getString(), hoveredConfirm);
        drawOverlayButton(guiGraphics, cancelX, cancelY, OVERLAY_BTN_W, OVERLAY_BTN_H,
                Component.translatable("config.gytrinket.cancel").getString(), hoveredCancel);

        String hint = Component.translatable("config.gytrinket.edit_hint").getString();
        hint = font.plainSubstrByWidth(hint, OVERLAY_W - 16);
        guiGraphics.drawString(font, hint,
                overlayX + (OVERLAY_W - font.width(hint)) / 2, overlayY + OVERLAY_H - 10,
                renderer.getHintColor(), false);
    }

    /** overlay 按钮（复刻 SciFiButton 样式：扁平底色 + 切角描边 + 居中文字） */
    private void drawOverlayButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String text, boolean hovered) {
        guiGraphics.fill(x, y, x + w, y + h,
                hovered ? ThemeColors.BUTTON_HOVER_COLOR : ThemeColors.BUTTON_COLOR);
        ScreenUtils.drawChamferRect(guiGraphics, x, y, w, h, 4, renderer.getAccentColor());
        guiGraphics.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2,
                ThemeColors.BUTTON_TEXT_COLOR, false);
    }

    // ==================== 编辑逻辑 ====================

    /** 打开数值编辑 overlay（手动创建 EditBox，不加入 renderables） */
    private void openEditor(ConfigValueRegistry.Entry entry) {
        isEditing = true;
        editingEntry = entry;
        int overlayX = panelX + (panelWidth - OVERLAY_W) / 2;
        int overlayY = panelY + (panelHeight - OVERLAY_H) / 2;
        valueEditBox = new EditBox(font, overlayX + 8, overlayY + 18, OVERLAY_W - 16, 12,
                Component.translatable("config.gytrinket.edit_hint"));
        valueEditBox.setMaxLength(32);
        valueEditBox.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
        valueEditBox.setValue(formatDouble(entry.getter.getAsDouble()));
        valueEditBox.setFocused(true);
        confirmX = overlayX + 8;
        confirmY = overlayY + 46;
        cancelX = overlayX + OVERLAY_W - OVERLAY_BTN_W - 8;
        cancelY = confirmY;
    }

    /** 确认编辑：客户端专属项本地生效，服务端项发往服务端（服务端校验/落盘/广播） */
    private void finishEditing() {
        if (editingEntry != null && valueEditBox != null) {
            try {
                applyEntry(editingEntry, Double.parseDouble(valueEditBox.getValue().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        closeEditor();
    }

    private void closeEditor() {
        isEditing = false;
        editingEntry = null;
        valueEditBox = null;
    }

    /** 应用配置值：布尔/数值统一入口 */
    private void applyEntry(ConfigValueRegistry.Entry entry, double rawValue) {
        if (entry.clientOnly) {
            entry.applier.accept(entry.clamp(rawValue));
            ClientConfig.SPEC.save();
        } else {
            NetworkHandler.INSTANCE.sendToServer(new ConfigValueUpdateMessage(entry.id, rawValue));
        }
    }

    /** double 转显示文本（整数值去小数尾零） */
    private static String formatDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    // ==================== 输入交互 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isEditing) {
            if (keyCode == 257 || keyCode == 335) { // Enter：确认
                finishEditing();
                return true;
            } else if (keyCode == 256) { // Esc：取消
                closeEditor();
                return true;
            }
            // 其余键交给 EditBox（Backspace/粘贴/方向键等），overlay 期间保持焦点
            if (valueEditBox != null) {
                valueEditBox.setFocused(true);
                valueEditBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isEditing) {
            if (valueEditBox != null) {
                valueEditBox.charTyped(codePoint, modifiers);
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (isEditing) {
            // overlay 打开期间拦截底层控件；输入框优先处理
            if (valueEditBox != null) {
                valueEditBox.mouseClicked(mouseX, mouseY, button);
                valueEditBox.setFocused(true);
            }
            if (mouseX >= confirmX && mouseX < confirmX + OVERLAY_BTN_W
                    && mouseY >= confirmY && mouseY < confirmY + OVERLAY_BTN_H) {
                finishEditing();
                return true;
            }
            if (mouseX >= cancelX && mouseX < cancelX + OVERLAY_BTN_W
                    && mouseY >= cancelY && mouseY < cancelY + OVERLAY_BTN_H) {
                closeEditor();
                return true;
            }
            return true;
        }

        // 分组 tab 点击
        int tabW = (panelWidth - 20) / 2;
        int tabY = panelY + 17;
        int tabH = 13;
        if (mouseY >= tabY && mouseY < tabY + tabH) {
            for (int k = 0; k < 2; k++) {
                int tx = panelX + 5 + k * (tabW + 5);
                if (mouseX >= tx && mouseX < tx + tabW) {
                    if ((k == 1) != clientGroup) {
                        clientGroup = k == 1;
                        scrollBar.setScrollOffset(0);
                    }
                    return true;
                }
            }
        }

        List<ConfigValueRegistry.Entry> rows = currentRows();
        int top = contentY();
        int bottom = contentBottom();
        int rowX = panelX + 5;
        int rowW = panelWidth - 16;
        int totalHeight = rows.size() * ROW_HEIGHT;
        int scrollOffset = scrollBar.getScrollOffset();

        // 行点击：布尔项直接切换，数值项打开编辑 overlay
        if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= top && mouseY < bottom) {
            int index = (int) ((mouseY - top + scrollOffset) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                int rowY = top + index * ROW_HEIGHT - scrollOffset;
                if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    ConfigValueRegistry.Entry entry = rows.get(index);
                    if (entry.bool) {
                        applyEntry(entry, entry.getter.getAsDouble() != 0.0 ? 0.0 : 1.0);
                    } else {
                        openEditor(entry);
                    }
                    return true;
                }
            }
        }

        // 滚动条点击
        scrollBar.mouseClicked(mouseX, mouseY, panelX + panelWidth - 8, top,
                bottom - top, bottom - top, totalHeight);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isEditing) {
            scrollBar.mouseScrolled(delta);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isEditing) {
            int top = contentY();
            int bottom = contentBottom();
            scrollBar.mouseDragged(mouseY, top, bottom - top, bottom - top,
                    currentRows().size() * ROW_HEIGHT);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
