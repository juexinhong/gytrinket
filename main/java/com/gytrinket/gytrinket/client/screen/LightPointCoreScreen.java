package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.client.datacenter.ClientDataCenter;
import com.gytrinket.gytrinket.menu.LightPointCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 光点核心容器界面（科幻风格）
 * 深蓝半透明面板 + 切角边框 + 蓝青标题，槽位 18px 格子 / 20px 间隔
 * 禁用物品在 renderSlot 内自控渲染（与玩家面板同款调用），遮罩保证覆盖在图标上方
 * 注意：render 阶段 pose 已平移到 (leftPos, topPos)，槽位内绘制使用相对坐标 slot.x/slot.y
 */
public class LightPointCoreScreen extends AbstractContainerScreen<LightPointCoreMenu> {

    public LightPointCoreScreen(LightPointCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 196;
        this.imageHeight = 180;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 深蓝半透明面板
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xCC121A2E);
        // 切角边框（右上/左下削角，蓝青流动）
        ScreenUtils.drawChamferRect(g, x, y, this.imageWidth, this.imageHeight, 4, 0xFF3D8BFF);

        // 槽位底框：纯色填充 + 统一色相循环淡边框（所有槽一致；悬停高亮由 renderSlotHighlight 提供）
        int slotX = this.leftPos;
        int slotY = this.topPos;
        for (Slot slot : this.menu.slots) {
            int sx = slotX + slot.x;
            int sy = slotY + slot.y;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF1C2740);
            ScreenUtils.drawChamferRectUniform(g, sx, sy, 18, 18, 2, ScreenUtils.withAlpha(0xFF4AA8FF, 90));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 蓝青标题（无阴影）
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF4AA8FF, false);
        // 玩家背包分割标签：位于背包槽区上方（容器槽底 76 下方 3px），颜色与标题一致的蓝色
        g.drawString(this.font, Component.translatable("container.gytrinket.player_inventory"),
                8, 79, 0xFF4AA8FF, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 原版 AbstractContainerScreen.render 不自动渲染悬停 tooltip，需手动调用
        this.renderTooltip(g, mouseX, mouseY);
    }

    /** 悬停槽位高亮：完全替换原版白色高亮，改为科幻样式（半透明深蓝填充 + 亮蓝切角边框） */
    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (slot.isHighlightable()) {
            // 渲染阶段 pose 已平移到 (leftPos, topPos)，使用相对坐标
            int x = slot.x;
            int y = slot.y;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 0.0F);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x3D2A3B60);
            ScreenUtils.drawChamferRect(g, x, y, 18, 18, 2, 0xFF4AA8FF);
            g.pose().popPose();
        }
    }

    /** 自控渲染槽位：物品统一向右下偏移 1px（与玩家面板一致），禁用槽位叠加遮罩与 × */
    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        int sx = slot.x;
        int sy = slot.y;
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(this.font, stack, sx + 1, sy + 1, null);
        }

        // 禁用槽位：70% 灰色遮罩 + 对角相交 ×（覆盖在物品图标上方，与物品同偏移）
        if (ClientDataCenter.isCoreContainerSynced() && slot.index >= 0 && slot.index < 27) {
            String[] reasons = ClientDataCenter.getDisabledReasons();
            if (slot.index < reasons.length) {
                String reason = reasons[slot.index];
                if (reason != null && !reason.isEmpty()) {
                    ScreenUtils.drawDisabledOverlay(g, sx, sy, 18);
                }
            }
        }
    }

    /**
     * 鼠标中键：在光点核心容器槽区（3x9 区域）一键整理
     * 仅当鼠标位于容器槽位区内生效，玩家背包/快捷栏区域不触发
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2 && this.menu.getCarried().isEmpty() && isMouseOverContainerArea(mouseX, mouseY)) {
            com.gytrinket.gytrinket.network.NetworkHandler.sendSortLightPointCore();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标是否位于光点核心容器槽区：相对坐标 x ∈ [8, 188)、y ∈ [18, 78)（3 行 9 列，18px 格 / 20px 间隔） */
    private boolean isMouseOverContainerArea(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        return x >= 8 && x < 188 && y >= 18 && y < 78;
    }
}
