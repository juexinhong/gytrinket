package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.network.packet.SetInterceptorWeaponPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class InterceptorWeaponSelectScreen extends Screen {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/gui/weapon_select.png");
    private final List<WeaponOption> weaponOptions = new ArrayList<>();
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

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 140;
        int buttonHeight = 20;
        int startX = (this.width - buttonWidth) / 2;
        int startY = (this.height - (weaponOptions.size() * (buttonHeight + 5))) / 2;

        for (int i = 0; i < weaponOptions.size(); i++) {
            final int index = i;
            WeaponOption option = weaponOptions.get(i);

            this.addRenderableWidget(Button.builder(option.name(), button -> {
                selectedWeapon = option.id();
                sendWeaponSelection();
                this.onClose();
            }).pos(startX, startY + i * (buttonHeight + 5)).size(buttonWidth, buttonHeight).build());
        }
    }

    private void sendWeaponSelection() {
        if (selectedWeapon >= 0) {
            ItemStack weaponStack = new ItemStack(net.minecraft.world.item.Items.PAPER);
            PacketDistributor.sendToServer(new SetInterceptorWeaponPayload(weaponStack));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record WeaponOption(int id, Component name, ChatFormatting color) {
    }
}
