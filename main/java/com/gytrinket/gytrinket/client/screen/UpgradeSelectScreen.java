package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.network.NetworkHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public class UpgradeSelectScreen extends AbstractPanelScreen {

    private final String baseItemKey;
    private final String upgradedItemKey;
    private final ListTag ingredients;

    private ItemStack hoveredItem = ItemStack.EMPTY;
    private int hoveredSlotIndex = -1;

    public UpgradeSelectScreen(Screen parentScreen, String baseItemKey, String upgradedItemKey, ListTag ingredients) {
        super(Component.translatable("screen.gytrinket.upgrade_select"), parentScreen, SolidUIRenderer.UPGRADE);
        this.baseItemKey = baseItemKey;
        this.upgradedItemKey = upgradedItemKey;
        this.ingredients = ingredients != null ? ingredients : new ListTag();
    }

    public String getBaseItemKey() {
        return baseItemKey;
    }

    public String getUpgradedItemKey() {
        return upgradedItemKey;
    }

    public void updateIngredients(ListTag newIngredients) {
        if (newIngredients != null) {
            ingredients.clear();
            for (int i = 0; i < newIngredients.size(); i++) {
                ingredients.add(newIngredients.getCompound(i));
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(200, 260, 20, 40);

        int btnY = panelY + panelHeight + 5;
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.return_materials"),
                button -> {
                    PacketDistributor.sendToServer(new NetworkHandler.UpgradeReturnPayload(baseItemKey, upgradedItemKey));
                    Minecraft.getInstance().setScreen(parentScreen);
                }
        ).bounds(panelX + 5, btnY, 90, 16).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ).bounds(panelX + panelWidth - 95, btnY, 90, 16).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderPanelBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.upgrade_select_title").getString(),
                panelX + 8, panelY + 6, renderer.getAccentColor());

        int y = panelY + 18;
        for (int i = 0; i < ingredients.size(); i++) {
            CompoundTag ing = ingredients.getCompound(i);
            String itemKey = ing.getString("itemKey");
            int required = ing.getInt("required");
            int collected = ing.getInt("collected");

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemKey));
            String itemName = item != null ? new ItemStack(item).getHoverName().getString() : itemKey;

            String text = itemName + " " + collected + "/" + required;
            int color = collected >= required ? renderer.getValueColor() : renderer.getTextColor();
            guiGraphics.drawString(font, text, panelX + 10, y, color);
            y += 10;
        }

        y += 4;
        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.upgrade_select_hint").getString(),
                panelX + 8, y, renderer.getHintColor());
        y += 12;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Inventory inventory = player.getInventory();
        int cols = 9;
        int slotSize = 16;
        int startX = panelX + (panelWidth - cols * slotSize) / 2;
        int startY = y;

        hoveredItem = ItemStack.EMPTY;
        hoveredSlotIndex = -1;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < cols; col++) {
                int slot = 9 + row * cols + col;
                int x = startX + col * slotSize;
                int iy = startY + row * slotSize;

                boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= iy && mouseY < iy + slotSize;
                renderer.drawSlot(guiGraphics, x, iy, slotSize - 1, slotSize - 1, hovered);

                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    guiGraphics.renderItem(stack, x, iy);
                    guiGraphics.renderItemDecorations(font, stack, x, iy);
                    if (hovered) {
                        hoveredItem = stack;
                        hoveredSlotIndex = slot;
                    }
                }
            }
        }

        int hotbarY = startY + 3 * slotSize + 4;
        for (int col = 0; col < cols; col++) {
            int slot = col;
            int x = startX + col * slotSize;

            boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= hotbarY && mouseY < hotbarY + slotSize;
            renderer.drawSlot(guiGraphics, x, hotbarY, slotSize - 1, slotSize - 1, hovered);

            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, x, hotbarY);
                guiGraphics.renderItemDecorations(font, stack, x, hotbarY);
                if (hovered) {
                    hoveredItem = stack;
                    hoveredSlotIndex = slot;
                }
            }
        }

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (!hoveredItem.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlotIndex >= 0 && !hoveredItem.isEmpty()) {
            PacketDistributor.sendToServer(new NetworkHandler.UpgradeConsumePayload(hoveredSlotIndex, baseItemKey, upgradedItemKey));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
