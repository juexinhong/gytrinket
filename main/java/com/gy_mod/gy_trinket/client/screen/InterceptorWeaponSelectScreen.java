package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.client.attack_mode.interceptor.InterceptorWeaponClientData;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.SetInterceptorWeaponMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class InterceptorWeaponSelectScreen extends AbstractPanelScreen {

    private ItemStack currentWeapon;
    private ItemStack hoveredItem = ItemStack.EMPTY;
    private int hoveredSlotIndex = -1;

    public InterceptorWeaponSelectScreen(Screen parentScreen) {
        super(Component.translatable("screen.gytrinket.interceptor_weapon_select"), parentScreen, SolidUIRenderer.PANEL);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.currentWeapon = InterceptorWeaponClientData.getWeapon(mc.player.getUUID());
        } else {
            this.currentWeapon = ItemStack.EMPTY;
        }
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(200, 250, 20, 40);

        int btnY = panelY + panelHeight + 5;
        int btnWidth = (panelWidth - 16) / 3;

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.gytrinket.clear_weapon"),
                button -> {
                    currentWeapon = ItemStack.EMPTY;
                    NetworkHandler.INSTANCE.sendToServer(new SetInterceptorWeaponMessage(ItemStack.EMPTY));
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

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.interceptor_weapon_select_title").getString(),
                panelX + 8, panelY + 6, renderer.getAccentColor());

        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.interceptor_weapon_select_hint").getString(),
                panelX + 8, panelY + 18, renderer.getHintColor());

        // 当前武器显示
        int currentY = panelY + 30;
        guiGraphics.drawString(font, Component.translatable("screen.gytrinket.current_weapon").getString(),
                panelX + 8, currentY, renderer.getTextColor());

        if (!currentWeapon.isEmpty()) {
            guiGraphics.renderItem(currentWeapon, panelX + 8, currentY + 12);
            guiGraphics.renderItemDecorations(font, currentWeapon, panelX + 8, currentY + 12);
            String weaponName = currentWeapon.getHoverName().getString();
            guiGraphics.drawString(font, weaponName, panelX + 26, currentY + 14, renderer.getValueColor());
            currentY += 30;
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.gytrinket.no_weapon_set").getString(),
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
                int x = startX + col * slotSize;
                int iy = startY + row * slotSize;

                boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= iy && mouseY < iy + slotSize;
                ItemStack stack = inventory.getItem(slot);

                // 检查是否为当前选中的武器
                boolean isSelected = !currentWeapon.isEmpty() && isSameItem(stack, currentWeapon);

                if (isSelected) {
                    // 黄色高亮边框
                    guiGraphics.fill(x - 1, iy - 1, x + slotSize, iy + slotSize, 0xFF5555FF);
                }
                renderer.drawSlot(guiGraphics, x, iy, slotSize - 1, slotSize - 1, hovered);

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

        // 快捷栏 (1行)
        int hotbarY = startY + 3 * slotSize + 4;
        for (int col = 0; col < cols; col++) {
            int slot = col;
            int x = startX + col * slotSize;

            boolean hovered = mouseX >= x && mouseX < x + slotSize && mouseY >= hotbarY && mouseY < hotbarY + slotSize;
            ItemStack stack = inventory.getItem(slot);

            boolean isSelected = !currentWeapon.isEmpty() && isSameItem(stack, currentWeapon);

            if (isSelected) {
                guiGraphics.fill(x - 1, hotbarY - 1, x + slotSize, hotbarY + slotSize, 0xFF5555FF);
            }
            renderer.drawSlot(guiGraphics, x, hotbarY, slotSize - 1, slotSize - 1, hovered);

            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, x, hotbarY);
                guiGraphics.renderItemDecorations(font, stack, x, hotbarY);
                if (hovered) {
                    hoveredItem = stack;
                    hoveredSlotIndex = slot;
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (!hoveredItem.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlotIndex >= 0 && !hoveredItem.isEmpty()) {
            currentWeapon = hoveredItem.copy();
            NetworkHandler.INSTANCE.sendToServer(new SetInterceptorWeaponMessage(hoveredItem.copy()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.isSameItemSameTags(a, b);
    }
}
