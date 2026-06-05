package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class ConfigPanelScreen extends AbstractPanelScreen {

    private static final int BASE_ROW_HEIGHT = 18;
    private static final int ATTR_LINE_HEIGHT = 12;

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
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.add_item"),
                button -> {
                    isAddingItem = true;
                    addingItemId = "";
                }
        ).bounds(panelX + 5, btnY, 80, 16).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.reset_defaults"),
                button -> NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ConfigResetMessage())
        ).bounds(panelX + 90, btnY, 80, 16).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ).bounds(panelX + panelWidth - 85, btnY, 80, 16).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isAddingItem) {
            if (keyCode == 257 || keyCode == 335) {
                finishAddingItem();
                return true;
            } else if (keyCode == 256) {
                isAddingItem = false;
                addingItemId = "";
                return true;
            } else if (keyCode == 259) {
                if (!addingItemId.isEmpty()) {
                    addingItemId = addingItemId.substring(0, addingItemId.length() - 1);
                }
                return true;
            } else if (keyCode == 86 && hasControlDown()) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) {
                    clip = clip.replaceAll("[\\s\\n\\r]", "");
                    if (!clip.isEmpty()) {
                        addingItemId += clip;
                    }
                }
                return true;
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isAddingItem) {
            if (codePoint != ' ' && codePoint >= 33) {
                addingItemId += codePoint;
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
                    new NetworkHandler.ConfigAddItemMessage(addingItemId));
            }
        }
        isAddingItem = false;
        addingItemId = "";
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
                        new NetworkHandler.ConfigUpdateMessage(itemTag.getString("itemId"), attr.getString("name"), val));
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
                    new NetworkHandler.ConfigRemoveAttrMessage(itemId, editingAttrName));
            }
        }
        isEditing = false;
        isNewAttribute = false;
        editingAttrName = null;
        editingValue = "";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isSelectingAttr) {
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

    private int calcRowHeight(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemConfigData.size()) return BASE_ROW_HEIGHT;
        boolean isSelected = (itemIndex == selectedItemIndex);

        if (!isSelected) return BASE_ROW_HEIGHT;

        CompoundTag itemTag = itemConfigData.getCompound(itemIndex);
        ListTag attrs = itemTag.getList("attributes", 10);

        if (attrs.isEmpty()) {
            return BASE_ROW_HEIGHT + ATTR_LINE_HEIGHT + ATTR_LINE_HEIGHT;
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
        return BASE_ROW_HEIGHT + attrLines * ATTR_LINE_HEIGHT + ATTR_LINE_HEIGHT;
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
        renderPanelBackground(guiGraphics);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.config_panel_title").getString(),
                panelX + 8, panelY + 6, renderer.getAccentColor());

        if (isDeletingAttr) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.delete_attr_hint").getString(),
                    panelX + panelWidth / 2 + 5, panelY + 6, renderer.getDeleteColor());
        }

        if (isDraggingItem) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.reorder_hint").getString(),
                    panelX + panelWidth / 2 + 5, panelY + 6, 0xFF00FF00);
        }

        boolean hasOverlay = isSelectingAttr || isAddingItem;

        if (!hasOverlay) {
            renderContent(guiGraphics, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (isSelectingAttr) {
            renderSelectAttrOverlay(guiGraphics, mouseX, mouseY);
        }

        if (isAddingItem) {
            renderAddItemOverlay(guiGraphics);
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
                guiGraphics.fill(panelX + 5, y, panelX + panelWidth - 5, y + 2, 0xFF00FF00);
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

            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
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
            guiGraphics.drawString(font, "X", delX + 4, y + 3, delHovered ? renderer.getDeleteColor() : 0xFF664444);

            if (isSelected) {
                int attrX = panelX + 28;
                int attrY = y + BASE_ROW_HEIGHT;
                int attrCellMaxWidth = panelWidth - 55;

                if (attrs.isEmpty()) {
                    if (!isSelectingAttr && !isEditing && !isDeletingAttr) {
                        guiGraphics.drawString(font,
                                Component.translatable("screen.gytrinket.no_attributes").getString(),
                                attrX, attrY + 2, 0xFF888888);

                        String addText = "[+]";
                        int hintWidth = font.width(Component.translatable("screen.gytrinket.no_attributes").getString());
                        int btnX = attrX + hintWidth + 8;
                        int btnY = attrY;
                        if (btnY < contentBottom) {
                            boolean addHovered = mouseX >= btnX && mouseX < btnX + font.width(addText) + 6
                                    && mouseY >= btnY && mouseY < btnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, addText, btnX + 3, btnY + 2, addHovered ? 0xFF55FF55 : 0xFF558855);
                            if (addHovered) {
                                hoveredAddBtn = true;
                                hoveredItemIndex = i;
                            }
                        }

                        String removeText = "[-]";
                        int remBtnX = btnX + font.width(addText) + 8;
                        int remBtnY = attrY;
                        if (remBtnY < contentBottom) {
                            boolean remHovered = mouseX >= remBtnX && mouseX < remBtnX + font.width(removeText) + 6
                                    && mouseY >= remBtnY && mouseY < remBtnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, removeText, remBtnX + 3, remBtnY + 2, remHovered ? renderer.getDeleteColor() : 0xFF885555);
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
                            guiGraphics.drawString(font, editText, attrX + 4, attrY + 2, 0xFFFFFF00);
                        } else if (isDeletingAttr) {
                            guiGraphics.drawString(font, attrText, attrX + 4, attrY + 2,
                                    attrHovered ? renderer.getDeleteColor() : 0xFFAA6666);
                        } else {
                            guiGraphics.drawString(font, attrText, attrX + 4, attrY + 2, renderer.getValueColor());
                        }

                        attrX += textWidth + 2;
                    }

                    if (!isSelectingAttr && !isEditing && !isDeletingAttr) {
                        String addText = "[+]";
                        int btnX = attrX + 2;
                        int btnY = attrY;
                        if (btnX + font.width(addText) + 6 > panelX + panelWidth - 25) {
                            btnX = panelX + 28;
                            btnY += ATTR_LINE_HEIGHT;
                        }
                        if (btnY < contentBottom) {
                            boolean addHovered = mouseX >= btnX && mouseX < btnX + font.width(addText) + 6
                                    && mouseY >= btnY && mouseY < btnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, addText, btnX + 3, btnY + 2, addHovered ? 0xFF55FF55 : 0xFF558855);
                            if (addHovered) {
                                hoveredAddBtn = true;
                                hoveredItemIndex = i;
                            }
                        }

                        String removeText = "[-]";
                        int remBtnX = btnX + font.width(addText) + 8;
                        int remBtnY = btnY;
                        if (remBtnX + font.width(removeText) + 6 > panelX + panelWidth - 25) {
                            remBtnX = panelX + 28;
                            remBtnY += ATTR_LINE_HEIGHT;
                        }
                        if (remBtnY < contentBottom) {
                            boolean remHovered = mouseX >= remBtnX && mouseX < remBtnX + font.width(removeText) + 6
                                    && mouseY >= remBtnY && mouseY < remBtnY + ATTR_LINE_HEIGHT - 1;
                            guiGraphics.drawString(font, removeText, remBtnX + 3, remBtnY + 2, remHovered ? renderer.getDeleteColor() : 0xFF885555);
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
            guiGraphics.fill(panelX + 5, y, panelX + panelWidth - 5, y + 2, 0xFF00FF00);
        }

        if (itemConfigData.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_config_items").getString(),
                    panelX + 15, contentY, 0xFF888888);
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

        guiGraphics.fill(panelX + 5, dragY, panelX + panelWidth - 5, dragY + rowHeight, 0xDD2a2a3a);

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item != null) {
            ItemStack itemStack = new ItemStack(item);
            guiGraphics.renderItem(itemStack, panelX + 10, dragY + 1);
            String itemName = itemStack.getHoverName().getString();
            guiGraphics.drawString(font, itemName, panelX + 28, dragY + 3, renderer.getTextColor());
        } else {
            guiGraphics.drawString(font, itemId, panelX + 10, dragY + 3, renderer.getTextColor());
        }

        guiGraphics.fill(panelX + 5, dragY, panelX + panelWidth - 5, dragY + 2, 0xFF00FF00);
        guiGraphics.fill(panelX + 5, dragY + rowHeight - 2, panelX + panelWidth - 5, dragY + rowHeight, 0xFF00FF00);
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
                guiGraphics.drawString(font, displayName, overlayX + 8, listY, 0xFF555555);
                if (hovered) {
                    guiGraphics.drawString(font, " *", overlayX + 8 + font.width(displayName), listY, 0xFF666666);
                }
            } else {
                guiGraphics.drawString(font, displayName, overlayX + 8, listY, hovered ? 0xFFFFFF00 : renderer.getTextColor());
            }
            listY += 10;
        }

        if (allAttributeNames.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_attributes_registered").getString(), overlayX + 8, listY, 0xFF888888);
        }
    }

    private void renderAddItemOverlay(GuiGraphics guiGraphics) {
        int overlayW = 240;
        int overlayH = 60;
        int overlayX = panelX + panelWidth / 2 - overlayW / 2;
        int overlayY = panelY + panelHeight / 2 - overlayH / 2;

        renderer.drawOverlayBackground(guiGraphics, overlayX, overlayY, overlayW, overlayH);
        renderer.drawOverlayBorder(guiGraphics, overlayX, overlayY, overlayW, overlayH);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.add_item_prompt").getString(),
                overlayX + 8, overlayY + 8, renderer.getAccentColor());

        guiGraphics.drawString(font, addingItemId + "_", overlayX + 8, overlayY + 28, 0xFFFFFF00);
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
            int overlayH = 60;
            int overlayX = panelX + panelWidth / 2 - overlayW / 2;
            int overlayY = panelY + panelHeight / 2 - overlayH / 2;
            if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                isAddingItem = false;
                addingItemId = "";
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
                        new NetworkHandler.ConfigRemoveAttrMessage(itemId, attrName));
                }
                isDeletingAttr = false;
                return true;
            }
            isDeletingAttr = false;
            return true;
        }

        if (hoveredDelete && hoveredItemIndex >= 0) {
            CompoundTag itemTag = itemConfigData.getCompound(hoveredItemIndex);
            String itemId = itemTag.getString("itemId");
            NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ConfigDeleteItemMessage(itemId));
            itemConfigData.remove(hoveredItemIndex);
            if (selectedItemIndex == hoveredItemIndex) selectedItemIndex = -1;
            else if (selectedItemIndex > hoveredItemIndex) selectedItemIndex--;
            return true;
        }

        if (hoveredAddBtn && hoveredItemIndex >= 0) {
            selectedItemIndex = hoveredItemIndex;
            isSelectingAttr = true;
            selectAttrScrollOffset = 0;
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

        selectedItemIndex = -1;
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
                    new NetworkHandler.ConfigReorderMessage(dragFromIndex, dragTargetIndex));
            }
            isDraggingItem = false;
            dragFromIndex = -1;
            dragTargetIndex = -1;
            return true;
        }
        scrollBar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
                new NetworkHandler.ConfigUpdateMessage(itemId, attrName, value));
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
