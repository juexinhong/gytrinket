package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.network.packet.SetInterceptorWeaponMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InterceptorWeaponSelectScreen extends Screen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("gytrinket", "textures/gui/weapon_select.png");
    private final List<WeaponOption> weaponOptions = new ArrayList<>();
    private final UIRenderer renderer = SolidUIRenderer.CONFIG;
    private int selectedWeapon = -1;
    private int entityId;

    public InterceptorWeaponSelectScreen(int entityId) {
        super(Component.translatable("screen.gytrinket.interceptor_weapon_select"));
        this.entityId = entityId;
        initWeaponOptions();
    }

    private void initWeaponOptions() {
        weaponOptions.add(new WeaponOption(0, Component.translatable("weapon.gytrinket.machine_gun"), ChatFormatting.WHITE));
        weaponOptions.add(new WeaponOption(1, Component.translatable("weapon.gytrinket.gatling"), ChatFormatting.YELLOW));
        weaponOptions.add(new WeaponOption(2, Component.translatable("weapon.gytrinket.missile"), ChatFormatting.RED));
        weaponOptions.add(new WeaponOption(3, Component.translatable("weapon.gytrinket.plasma_cannon"), ChatFormatting.AQUA));
        weaponOptions.add(new WeaponOption(4, Component.translatable("weapon.gytrinket.railgun"), ChatFormatting.LIGHT_PURPLE));
    }

    private int panelWidth() { return 200; }
    private int panelHeight() { return weaponOptions.size() * 25 + 40; }
    private int panelX() { return (this.width - panelWidth()) / 2; }
    private int panelY() { return (this.height - panelHeight()) / 2 - 10; }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 150;
        int buttonHeight = 20;
        int startX = panelX() + (panelWidth() - buttonWidth) / 2;
        int startY = panelY() + 26;

        for (int i = 0; i < weaponOptions.size(); i++) {
            final int index = i;
            WeaponOption option = weaponOptions.get(i);

            this.addRenderableWidget(SciFiButton.create(option.name(), button -> {
                selectedWeapon = option.id();
                sendWeaponSelection();
                this.onClose();
            }).pos(startX, startY + i * (buttonHeight + 5)).size(buttonWidth, buttonHeight).build());
        }
    }

    private void sendWeaponSelection() {
        if (selectedWeapon >= 0) {
            ItemStack weaponStack = new ItemStack(net.minecraft.world.item.Items.PAPER);
            NetworkHandler.INSTANCE.sendToServer(new SetInterceptorWeaponMessage(weaponStack));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int px = panelX();
        int py = panelY();
        renderer.drawPanelBackground(guiGraphics, px, py, panelWidth(), panelHeight());
        renderer.drawPanelBorder(guiGraphics, px, py, panelWidth(), panelHeight());
        renderer.drawPanelHeader(guiGraphics, px + 1, py + 1, panelWidth() - 2, 16);
        guiGraphics.drawString(this.font, this.title.getString(), px + 10, py + 5, renderer.getAccentColor(), false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record WeaponOption(int id, Component name, ChatFormatting color) {
    }
}
