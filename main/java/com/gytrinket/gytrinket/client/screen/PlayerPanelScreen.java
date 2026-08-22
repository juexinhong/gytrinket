package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.core.attribute.AttributeDefinition;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.level.ModLevelData;
import com.gytrinket.gytrinket.network.packet.RequestConfigDataPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class PlayerPanelScreen extends AbstractPanelScreen {

    private static final int MAX_ITEMS_PER_COLUMN = 12;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_STEP = 20; // 格子间距（18px 格子 + 2px 间隙）

    private Map<String, Double> attributes;
    private List<ItemStack> equippedItems;
    private int slotCount;
    private CompoundTag upgradeDataTag;
    private ListTag upgradeTargets;
    private int modLevel;
    private int upgradeExp;
    private int upgradePoints;

    private List<Map.Entry<String, Double>> sortedAttrs = new ArrayList<>();
    private int attrVisibleLines;
    private final ScrollBarComponent scrollBar = new ScrollBarComponent();

    private ItemStack hoveredItem = ItemStack.EMPTY;
    private int hoveredSlotIndex = -1;

    public PlayerPanelScreen(Map<String, Double> attributes, ListTag items, int slotCount,
                              CompoundTag upgradeDataTag, ListTag upgradeTargets,
                              int modLevel, int upgradeExp, int upgradePoints) {
        super(Component.translatable("screen.gytrinket.player_panel"), null, SolidUIRenderer.PANEL);
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.slotCount = slotCount;
        this.upgradeDataTag = upgradeDataTag != null ? upgradeDataTag : new CompoundTag();
        this.upgradeTargets = upgradeTargets != null ? upgradeTargets : new ListTag();
        this.modLevel = modLevel;
        this.upgradeExp = upgradeExp;
        this.upgradePoints = upgradePoints;
        this.equippedItems = new ArrayList<>();
        parseItems(items);
        rebuildSortedAttrs();
    }

    // ===== 布局坐标 =====
    private int bodyTop() { return panelY + 20; }
    private int bodyBottom() { return panelY + panelHeight - 6; }
    private int equipColX() { return panelX + 8; }
    private int attrColX() { return panelX + 56; }
    private int attrColWidth() { return 250; }
    private int rightColX() { return panelX + 314; }
    private int rightColWidth() { return panelWidth - 322; }
    private int attrListTop() { return bodyTop() + 14; }

    private void parseItems(ListTag items) {
        this.equippedItems = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            equippedItems.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < items.size(); i++) {
            CompoundTag tag = items.getCompound(i);
            int slot = tag.contains("slot") ? tag.getInt("slot") : i;
            if (slot >= 0 && slot < slotCount && tag.contains("id")) {
                ItemStack stack = ItemStack.parse(Minecraft.getInstance().level.registryAccess(), tag).orElse(ItemStack.EMPTY);
                equippedItems.set(slot, stack);
            }
        }
    }

    public void updateData(Map<String, Double> attributes, ListTag items, int slotCount,
                            CompoundTag upgradeDataTag, ListTag upgradeTargets,
                            int modLevel, int upgradeExp, int upgradePoints) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.slotCount = slotCount;
        this.upgradeDataTag = upgradeDataTag != null ? upgradeDataTag : new CompoundTag();
        this.upgradeTargets = upgradeTargets != null ? upgradeTargets : new ListTag();
        this.modLevel = modLevel;
        this.upgradeExp = upgradeExp;
        this.upgradePoints = upgradePoints;
        parseItems(items);
        rebuildSortedAttrs();
    }

    private void rebuildSortedAttrs() {
        this.sortedAttrs = attributes.entrySet().stream()
                .filter(e -> {
                    AttributeDefinition def = AttributeManager.getAttributeDefinition(e.getKey());
                    double defaultValue = def != null ? def.getDefaultValue() : 0.0;
                    return Double.compare(e.getValue(), defaultValue) != 0;
                })
                .sorted(Comparator.comparing(e ->
                        Component.translatable("tooltip.gytrinket.attr." + e.getKey()).getString()))
                .toList();
        this.scrollBar.setScrollOffset(Math.min(scrollBar.getScrollOffset(), Math.max(0, sortedAttrs.size() - attrVisibleLines)));
        scrollBar.updateMaxScroll(sortedAttrs.size(), attrVisibleLines);
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(460, 300, 20, 20);

        // 顶部导航标签
        int tabH = 14;
        int tabY = panelY + 3;
        int tabW = 48;
        int configX = panelX + panelWidth - 8 - tabW;
        int upgradeX = configX - 4 - tabW;

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.upgrade_button"),
                button -> openUpgradeTargetScreen()
        ).bounds(upgradeX, tabY, tabW, tabH).renderer(renderer).build());

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.config_button"),
                button -> openConfigScreen()
        ).bounds(configX, tabY, tabW, tabH).renderer(renderer).build());
    }

    private void openUpgradeTargetScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new UpgradeTargetScreen(this, upgradeTargets));
        }
    }

    private void openConfigScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PacketDistributor.sendToServer(new RequestConfigDataPayload());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
        if (scrollBar.needsScrollbar()) {
            scrollBar.mouseScrolled(verticalScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (scrollBar.needsScrollbar()) {
            int scrollBarX = attrColX() + attrColWidth() + 2;
            int scrollBarY = attrListTop();
            int scrollBarHeight = bodyBottom() - scrollBarY;
            if (scrollBar.mouseClicked(mouseX, mouseY, scrollBarX, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size())) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollBar.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollBar.isDraggingScrollbar()) {
            int scrollBarY = attrListTop();
            int scrollBarHeight = bodyBottom() - scrollBarY;
            scrollBar.mouseDragged(mouseY, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderPanelBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 顶部标题栏
        renderer.drawPanelHeader(guiGraphics, panelX + 1, panelY + 1, panelWidth - 2, 18);
        drawText(guiGraphics, Component.translatable("screen.gytrinket.player_panel").getString(),
                panelX + 10, panelY + 5, renderer.getAccentColor());

        renderEquipment(guiGraphics, mouseX, mouseY);
        renderAttributes(guiGraphics);
        renderLevelInfo(guiGraphics);

        // 分栏分隔线
        guiGraphics.fill(attrColX() - 6, bodyTop(), attrColX() - 5, bodyBottom(), renderer.getDividerColor());
        guiGraphics.fill(rightColX() - 6, bodyTop(), rightColX() - 5, bodyBottom(), renderer.getDividerColor());

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderEquipment(GuiGraphics g, int mouseX, int mouseY) {
        int x = equipColX();
        int y = bodyTop();

        hoveredItem = ItemStack.EMPTY;
        hoveredSlotIndex = -1;

        for (int i = 0; i < slotCount; i++) {
            int col = i / MAX_ITEMS_PER_COLUMN;
            int row = i % MAX_ITEMS_PER_COLUMN;
            int sx = x + col * SLOT_STEP;
            int sy = y + row * SLOT_STEP;

            ItemStack stack = (i < equippedItems.size()) ? equippedItems.get(i) : ItemStack.EMPTY;
            boolean hovered = !stack.isEmpty()
                    && mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;

            // 只有非空物品才渲染底框，避免空槽显示过多框格
            if (!stack.isEmpty()) {
                renderer.drawSlot(g, sx, sy, SLOT_SIZE, SLOT_SIZE, hovered);
                g.renderItem(stack, sx + 1, sy + 1);
                if (hovered) {
                    hoveredItem = stack;
                    hoveredSlotIndex = i;
                }
            }
        }
    }

    private void renderAttributes(GuiGraphics g) {
        int colX = attrColX();
        int colWidth = attrColWidth();

        drawText(g, Component.translatable("screen.gytrinket.attributes").getString(),
                colX + 2, bodyTop(), renderer.getAccentColor());
        renderer.drawTitleUnderline(g, colX + 2, bodyTop() + 9, 56);

        int listTop = attrListTop();
        int listBottom = bodyBottom();
        attrVisibleLines = Math.max(0, (listBottom - listTop) / 10);
        scrollBar.updateMaxScroll(sortedAttrs.size(), attrVisibleLines);
        scrollBar.setScrollOffset(Math.min(scrollBar.getScrollOffset(), scrollBar.getMaxScrollOffset()));

        int ay = listTop;
        g.enableScissor(colX, listTop, colX + colWidth + 3, listBottom);
        for (int i = scrollBar.getScrollOffset(); i < sortedAttrs.size(); i++) {
            Map.Entry<String, Double> entry = sortedAttrs.get(i);
            String name = Component.translatable("tooltip.gytrinket.attr." + entry.getKey()).getString();
            String value = ScreenUtils.formatValue(entry.getValue());
            drawText(g, name, colX + 4, ay, renderer.getTextColor());
            drawText(g, value, colX + colWidth - 4 - font.width(value), ay, renderer.getValueColor());
            ay += 10;
        }
        g.disableScissor();

        if (scrollBar.needsScrollbar()) {
            int scrollBarX = colX + colWidth + 2;
            int scrollBarY = listTop;
            int scrollBarHeight = listBottom - scrollBarY;
            scrollBar.render(g, renderer, scrollBarX, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size());
        }
    }

    private void renderLevelInfo(GuiGraphics g) {
        int colX = rightColX();
        int colWidth = rightColWidth();

        // 光点等级信息（右下角紧凑区）
        int y = bodyBottom() - 32;

        String levelStr = "Lv." + modLevel;
        drawText(g, levelStr, colX + 4, y, renderer.getValueColor());

        int xpNeeded = ModLevelData.getXpNeededForNextLevel(modLevel);
        String expStr = upgradeExp + "/" + xpNeeded;
        drawText(g, expStr, colX + colWidth - 4 - font.width(expStr), y, renderer.getTextColor());

        // 经验进度条
        int barY = y + 10;
        float progress = xpNeeded > 0 ? (float) upgradeExp / xpNeeded : 0.0f;
        renderer.drawProgressBar(g, colX + 4, barY, colWidth - 8, 4, progress, renderer.getAccentColor());

        String pointsStr = Component.translatable("screen.gytrinket.upgrade_points").getString() + ": " + upgradePoints;
        drawText(g, pointsStr, colX + 4, barY + 8, renderer.getTextColor());
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!hoveredItem.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }
}
