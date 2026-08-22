package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.network.packet.SetInterceptorAmmoPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class InterceptorAmmoSelectScreen extends Screen {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/gui/ammo_select.png");
    private final List<AmmoOption> ammoOptions = new ArrayList<>();
    private final UIRenderer renderer = SolidUIRenderer.CONFIG;
    private int selectedAmmo = -1;
    private int entityId;

    public InterceptorAmmoSelectScreen(int entityId) {
        super(Component.translatable("screen.gytrinket.interceptor_ammo_select"));
        this.entityId = entityId;
        initAmmoOptions();
    }

    private void initAmmoOptions() {
        ammoOptions.add(new AmmoOption(0, Component.translatable("ammo.gytrinket.standard"), ChatFormatting.WHITE));
        ammoOptions.add(new AmmoOption(1, Component.translatable("ammo.gytrinket.incendiary"), ChatFormatting.RED));
        ammoOptions.add(new AmmoOption(2, Component.translatable("ammo.gytrinket.explosive"), ChatFormatting.GOLD));
        ammoOptions.add(new AmmoOption(3, Component.translatable("ammo.gytrinket.armor_piercing"), ChatFormatting.AQUA));
    }

    private int panelWidth() { return 180; }
    private int panelHeight() { return ammoOptions.size() * 25 + 40; }
    private int panelX() { return (this.width - panelWidth()) / 2; }
    private int panelY() { return (this.height - panelHeight()) / 2 - 10; }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 130;
        int buttonHeight = 20;
        int startX = panelX() + (panelWidth() - buttonWidth) / 2;
        int startY = panelY() + 26;

        for (int i = 0; i < ammoOptions.size(); i++) {
            final int index = i;
            AmmoOption option = ammoOptions.get(i);

            this.addRenderableWidget(SciFiButton.create(option.name(), button -> {
                selectedAmmo = option.id();
                sendAmmoSelection();
                this.onClose();
            }).pos(startX, startY + i * (buttonHeight + 5)).size(buttonWidth, buttonHeight).build());
        }
    }

    private void sendAmmoSelection() {
        if (selectedAmmo >= 0) {
            ItemStack ammoStack = new ItemStack(net.minecraft.world.item.Items.PAPER);
            PacketDistributor.sendToServer(new SetInterceptorAmmoPayload(ammoStack));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

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

    private record AmmoOption(int id, Component name, ChatFormatting color) {
    }
}
