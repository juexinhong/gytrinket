package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorAttackMode;
import com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorConfigContainer;
import com.gytrinket.gytrinket.network.packet.SetInterceptorAttackModePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 拦截机配置容器界面
 * <p>
 * 使用与 G 键面板一致的深色 PANEL 主题，包含武器槽、弹药槽、攻击模式按钮和玩家背包。
 */
public class InterceptorConfigContainerScreen extends AbstractContainerScreen<InterceptorConfigContainer> {

    private final UIRenderer renderer = SolidUIRenderer.PANEL;

    private SciFiButton attackModeButton;

    public InterceptorConfigContainerScreen(InterceptorConfigContainer container, Inventory inventory, Component title) {
        super(container, inventory, title);
        this.imageWidth = 196;
        this.imageHeight = 168;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 5;

        // 攻击模式切换按钮（右侧区域）
        this.attackModeButton = this.addRenderableWidget(SciFiButton.create(
                getAttackModeButtonText(),
                button -> toggleAttackMode()
        ).bounds(this.leftPos + 116, this.topPos + 20, 52, 20).renderer(renderer).build());
    }

    private Component getAttackModeButtonText() {
        InterceptorAttackMode mode = this.menu.getAttackMode();
        return Component.translatable(mode.getTranslationKey());
    }

    private void toggleAttackMode() {
        InterceptorAttackMode current = this.menu.getAttackMode();
        InterceptorAttackMode next = current.next();
        this.menu.setAttackMode(next);
        attackModeButton.setMessage(getAttackModeButtonText());

        PacketDistributor.sendToServer(new SetInterceptorAttackModePayload(next.getSerializedName()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 绘制顶部配置面板背景与边框
        renderer.drawPanelBackground(guiGraphics, x, y, this.imageWidth, 55);
        renderer.drawPanelBorder(guiGraphics, x, y, this.imageWidth, 55);

        // 绘制武器槽背景（container中slot位置: 26, 22）
        drawSlotBg(guiGraphics, x + 26 - 1, y + 22 - 1, mouseX, mouseY, 0, 18);

        // 绘制弹药槽背景（container中slot位置: 80, 22）
        drawSlotBg(guiGraphics, x + 80 - 1, y + 22 - 1, mouseX, mouseY, 1, 18);

        // 绘制玩家背包区域背景与边框
        renderer.drawPanelBackground(guiGraphics, x, y + 71, this.imageWidth, 96);
        renderer.drawPanelBorder(guiGraphics, x, y + 71, this.imageWidth, 96);

        // 绘制玩家背包槽位背景（3行9列，起始: 8, 84；格子 18px，间隔 20px → 2px 间隙）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 2 + row * 9 + col;
                drawSlotBg(guiGraphics, x + 8 + col * 20 - 1, y + 84 + row * 20 - 1, mouseX, mouseY, slotIndex, 18);
            }
        }

        // 绘制快捷栏槽位背景（9列，起始: 8, 148）
        for (int col = 0; col < 9; col++) {
            int slotIndex = 2 + 27 + col;
            drawSlotBg(guiGraphics, x + 8 + col * 20 - 1, y + 148 - 1, mouseX, mouseY, slotIndex, 18);
        }
    }

    /**
     * 绘制物品槽背景（带悬停高亮），与其他拦截机界面风格一致
     */
    private void drawSlotBg(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY, int slotIndex, int size) {
        boolean hovered = false;
        if (slotIndex >= 0 && slotIndex < this.menu.slots.size()) {
            Slot slot = this.menu.slots.get(slotIndex);
            hovered = this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
        }
        renderer.drawSlot(guiGraphics, x, y, size, size, hovered);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, renderer.getAccentColor(), false);

        // 武器槽标签（槽位下方）
        guiGraphics.drawString(this.font,
                Component.translatable("screen.gytrinket.interceptor_weapon"),
                24, 41, renderer.getTextColor(), false);

        // 弹药槽标签（槽位下方）
        guiGraphics.drawString(this.font,
                Component.translatable("screen.gytrinket.interceptor_ammo"),
                78, 41, renderer.getTextColor(), false);

        // 攻击模式标签
        guiGraphics.drawString(this.font,
                Component.translatable("screen.gytrinket.interceptor_attack_mode"),
                118, 10, renderer.getAccentColor(), false);

        // 玩家背包标签
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, renderer.getTextColor(), false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
