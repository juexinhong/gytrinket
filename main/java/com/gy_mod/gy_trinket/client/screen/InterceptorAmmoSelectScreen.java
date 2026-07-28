package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.SetInterceptorAmmoMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;

public class InterceptorAmmoSelectScreen extends AbstractPanelScreen {

    private ItemStack currentAmmo;
    private ItemStack hoveredItem = ItemStack.EMPTY;
    private int hoveredSlotIndex = -1;

    public InterceptorAmmoSelectScreen(Screen parentScreen) {
        super(Component.translatable("screen.gytrinket.interceptor_ammo_select"), parentScreen, SolidUIRenderer.PANEL);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.currentAmmo = InterceptorWeaponManager.getAmmo(mc.player.getUUID());
        } else {
            this.currentAmmo = ItemStack.EMPTY;
        }
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(200, 250, 20, 40);

        int btnY = panelY + panelHeight + 5;
        int btnWidth = (panelWidth - 16) / 3;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.clear_ammo"),
                button -> {
                    currentAmmo = ItemStack.EMPTY;
                    NetworkHandler.INSTANCE.sendToServer(new SetInterceptorAmmoMessage(ItemStack.EMPTY));
                }
        ).bounds(panelX + 5, btnY, btnWidth, 16).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.back"),
                button -> Minecraft.getInstance().setScreen(parentScreen)
        ).bounds(panelX + panelWidth - btnWidth - 5, btnY, btnWidth, 16).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderPanelBackground(guiGraphics);

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.interceptor_ammo_select_title").getString(),
                panelX + 8, panelY + 6, renderer.getAccentColor());

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.interceptor_ammo_select_hint").getString(),
                panelX + 8, panelY + 18, renderer.getHintColor());

        // 当前弹药显示
        int currentY = panelY + 30;
        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.current_ammo").getString(),
                panelX + 8, currentY, renderer.getTextColor());

        if (!currentAmmo.isEmpty()) {
            guiGraphics.renderItem(currentAmmo, panelX + 8, currentY + 12);
            guiGraphics.renderItemDecorations(font, currentAmmo, panelX + 8, currentY + 12);
            String ammoName = currentAmmo.getHoverName().getString();
            guiGraphics.drawString(font, ammoName + " x" + currentAmmo.getCount(), panelX + 26, currentY + 14, renderer.getValueColor());
            currentY += 30;
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_ammo_set").getString(),
                    panelX + 26, currentY + 12, renderer.getHintColor());
            currentY += 30;
        }

        // 物品栏网格
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        Inventory inventory = player.getInventory();
        int cols = 9;
        int slotSize = 16;
        int startX = panelX + (panelWidth - cols * slotSize) / 2;
        int startY = currentY + 2;

        hoveredItem = ItemStack.EMPTY;
        hoveredSlotIndex = -1;

        // 主物品栏 (3行)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < cols; col++) {
                int slot = 9 + row * cols + col;
                renderInventorySlot(guiGraphics, inventory, slot, startX + col * slotSize, startY + row * slotSize, slotSize, mouseX, mouseY);
            }
        }

        // 快捷栏 (1行)
        int hotbarY = startY + 3 * slotSize + 4;
        for (int col = 0; col < cols; col++) {
            renderInventorySlot(guiGraphics, inventory, col, startX + col * slotSize, hotbarY, slotSize, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (!hoveredItem.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }

    private void renderInventorySlot(GuiGraphics guiGraphics, Inventory inventory, int slot, int x, int y, int slotSize, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize;
        ItemStack stack = inventory.getItem(slot);
        boolean isArrow = !stack.isEmpty() && stack.getItem() instanceof ArrowItem;
        boolean isSelected = !currentAmmo.isEmpty() && isSameItem(stack, currentAmmo);

        if (isSelected) {
            guiGraphics.fill(x - 1, y - 1, x + slotSize, y + slotSize, 0xFF5555FF);
        } else if (!isArrow && !stack.isEmpty()) {
            // 非箭矢物品半透明
            guiGraphics.fill(x, y, x + slotSize - 1, y + slotSize - 1, 0x40505050);
        }

        renderer.drawSlot(guiGraphics, x, y, slotSize - 1, slotSize - 1, hovered && isArrow);

        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x, y);
            guiGraphics.renderItemDecorations(font, stack, x, y);
            if (hovered && isArrow) {
                hoveredItem = stack;
                hoveredSlotIndex = slot;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlotIndex >= 0 && !hoveredItem.isEmpty()) {
            currentAmmo = hoveredItem.copy();
            NetworkHandler.INSTANCE.sendToServer(new SetInterceptorAmmoMessage(hoveredItem.copy()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameTags(a, b);
    }
}
