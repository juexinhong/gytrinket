package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.core.level.ModLevelData;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class PlayerPanelScreen extends AbstractPanelScreen {

    private Map<String, Double> attributes;
    private List<ItemStack> equippedItems;
    private int slotCount;
    private CompoundTag upgradeDataTag;
    private ListTag upgradeTargets;
    private int modLevel;
    private int upgradeExp;
    private int upgradePoints;

    private List<Map.Entry<String, Double>> sortedAttrs = new ArrayList<>();
    private int attrStartY;
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
                .filter(e -> e.getValue() != 0.0)
                .sorted(Comparator.comparing(e ->
                        Component.translatable("tooltip.gytrinket.attr." + e.getKey()).getString()))
                .toList();
        this.scrollBar.setScrollOffset(Math.min(scrollBar.getScrollOffset(), Math.max(0, sortedAttrs.size() - attrVisibleLines)));
        scrollBar.updateMaxScroll(sortedAttrs.size(), attrVisibleLines);
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(380, 280, 20, 20);

        int rightColX = panelX + panelWidth / 2 + 5;
        int rightColWidth = panelWidth / 2 - 10;
        int btnWidth = (rightColWidth - 6) / 2;
        int btnY = panelY + panelHeight - 22;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.upgrade_button"),
                button -> openUpgradeTargetScreen()
        ).bounds(rightColX, btnY, btnWidth, 16).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.config_button"),
                button -> openConfigScreen()
        ).bounds(rightColX + btnWidth + 6, btnY, btnWidth, 16).build());
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
            PacketDistributor.sendToServer(new NetworkHandler.RequestConfigDataPayload());
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
            int colX = panelX + 5;
            int colWidth = panelWidth / 2 - 10;
            int attrBottomY = panelY + panelHeight - 6;
            int scrollBarX = colX + colWidth + 2;
            int scrollBarY = attrStartY + 11;
            int scrollBarHeight = attrBottomY - scrollBarY;
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
            int colX = panelX + 5;
            int colWidth = panelWidth / 2 - 10;
            int attrBottomY = panelY + panelHeight - 6;
            int scrollBarY = attrStartY + 11;
            int scrollBarHeight = attrBottomY - scrollBarY;
            scrollBar.mouseDragged(mouseY, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 先渲染背景模糊（只调用一次）
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        // 渲染面板背景
        renderPanelBackground(guiGraphics, mouseX, mouseY, partialTick);

        int divX = panelX + panelWidth / 2;
        int divTop = panelY + 5;
        int divBottom = panelY + panelHeight - 5;
        guiGraphics.fill(divX, divTop, divX + 1, divBottom, renderer.getDividerColor());

        renderLeftColumn(guiGraphics, mouseX, mouseY);
        renderRightColumn(guiGraphics);

        // 渲染按钮等widgets（不再调用renderBackground）
        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderLeftColumn(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int colX = panelX + 5;
        int colWidth = panelWidth / 2 - 10;
        int y = panelY + 6;

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.equipped_items").getString(),
                colX + 2, y, renderer.getAccentColor());

        int cols = 9;
        int slotSize = 16;
        int startX = colX + (colWidth - cols * slotSize) / 2;
        int itemY = y + 12;

        hoveredItem = ItemStack.EMPTY;
        hoveredSlotIndex = -1;

        for (int i = 0; i < slotCount; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * slotSize;
            int iy = itemY + row * slotSize;

            boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= iy && mouseY < iy + slotSize;
            renderer.drawSlot(guiGraphics, x, iy, slotSize - 1, slotSize - 1, hovered);

            if (i < equippedItems.size() && !equippedItems.get(i).isEmpty()) {
                guiGraphics.renderItem(equippedItems.get(i), x, iy);
                if (hovered) {
                    hoveredItem = equippedItems.get(i);
                    hoveredSlotIndex = i;
                }
            }
        }

        attrStartY = itemY + ((slotCount + cols - 1) / cols) * slotSize + 8;
        int attrBottomY = panelY + panelHeight - 6;
        attrVisibleLines = Math.max(0, (attrBottomY - attrStartY - 11) / 10);
        scrollBar.updateMaxScroll(sortedAttrs.size(), attrVisibleLines);
        scrollBar.setScrollOffset(Math.min(scrollBar.getScrollOffset(), scrollBar.getMaxScrollOffset()));

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.attributes").getString(),
                colX + 2, attrStartY, renderer.getAccentColor());

        int ay = attrStartY + 11;
        guiGraphics.enableScissor(colX, attrStartY + 11, colX + colWidth + 3, attrBottomY);
        for (int i = scrollBar.getScrollOffset(); i < sortedAttrs.size(); i++) {
            Map.Entry<String, Double> entry = sortedAttrs.get(i);
            String name = Component.translatable("tooltip.gytrinket.attr." + entry.getKey()).getString();
            String value = ScreenUtils.formatValue(entry.getValue());
            guiGraphics.drawString(font, name, colX + 5, ay, renderer.getTextColor());
            guiGraphics.drawString(font, value, colX + colWidth - 5 - font.width(value), ay, renderer.getValueColor());
            ay += 10;
        }
        guiGraphics.disableScissor();

        if (scrollBar.needsScrollbar()) {
            int scrollBarX = colX + colWidth + 2;
            int scrollBarY = attrStartY + 11;
            int scrollBarHeight = attrBottomY - scrollBarY;
            scrollBar.render(guiGraphics, renderer, scrollBarX, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size());
        }
    }

    private void renderRightColumn(GuiGraphics guiGraphics) {
        int colX = panelX + panelWidth / 2 + 5;
        int colWidth = panelWidth / 2 - 10;
        int y = panelY + 6;

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.upgrade_info").getString(),
                colX + 2, y, renderer.getAccentColor());

        y += 14;
        int upgradeBottomY = panelY + panelHeight / 2;

        for (String pathKey : upgradeDataTag.getAllKeys()) {
            if (y > upgradeBottomY - 10) break;
            String displayKey = pathKey;
            if (pathKey.contains("->")) {
                String[] parts = pathKey.split("->");
                if (parts.length == 2) {
                    try {
                        net.minecraft.world.item.Item baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[0]));
                        net.minecraft.world.item.Item upgradedItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[1]));
                        if (baseItem != null && upgradedItem != null) {
                            String baseName = new net.minecraft.world.item.ItemStack(baseItem).getHoverName().getString();
                            String upgradedName = new net.minecraft.world.item.ItemStack(upgradedItem).getHoverName().getString();
                            displayKey = baseName + " -> " + upgradedName;
                        }
                    } catch (Exception ignored) {}
                }
            }
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.upgrade_materials_for",
                    displayKey).getString(), colX + 5, y, renderer.getTextColor());
            y += 10;

            ListTag materials = upgradeDataTag.getList(pathKey, 10);
            for (int i = 0; i < materials.size(); i++) {
                if (y > upgradeBottomY - 10) break;
                CompoundTag itemTag = materials.getCompound(i);
                ItemStack stack = ItemStack.parse(Minecraft.getInstance().level.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    String itemName = stack.getHoverName().getString();
                    String count = "x" + stack.getCount();
                    guiGraphics.drawString(font, "  " + itemName, colX + 10, y, 0xFFAAAAAA);
                    guiGraphics.drawString(font, count, colX + colWidth - 10 - font.width(count), y, renderer.getValueColor());
                    y += 10;
                }
            }
            y += 4;
        }

        if (upgradeDataTag.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_upgrade_materials").getString(),
                    colX + 5, y, renderer.getHintColor());
        }

        int dividerY = panelY + panelHeight / 2;
        renderer.drawDivider(guiGraphics, colX, dividerY, colWidth);

        // 光点等级信息
        int levelY = dividerY + 6;
        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.light_point_level").getString(),
                colX + 2, levelY, renderer.getAccentColor());

        levelY += 12;
        String levelStr = String.valueOf(modLevel);
        guiGraphics.drawString(font, levelStr, colX + 5, levelY, renderer.getValueColor());

        // 光点经验进度条
        int xpNeeded = ModLevelData.getXpNeededForNextLevel(modLevel);
        String expStr = upgradeExp + "/" + xpNeeded;
        guiGraphics.drawString(font, expStr, colX + colWidth - 5 - font.width(expStr), levelY, renderer.getTextColor());

        // 经验进度条
        levelY += 12;
        int barWidth = colWidth - 10;
        float progress = xpNeeded > 0 ? (float) upgradeExp / xpNeeded : 0.0f;
        guiGraphics.fill(colX + 5, levelY, colX + 5 + barWidth, levelY + 3, 0xFF333333);
        guiGraphics.fill(colX + 5, levelY, colX + 5 + (int)(barWidth * progress), levelY + 3, renderer.getAccentColor());

        // 升级点
        levelY += 8;
        String pointsStr = Component.translatable("screen.gytrinket.upgrade_points").getString() + ": " + upgradePoints;
        guiGraphics.drawString(font, pointsStr, colX + 5, levelY, renderer.getTextColor());
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!hoveredItem.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }
}
