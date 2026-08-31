package com.gy_mod.gy_trinket.client.screen;

import com.gy_mod.gy_trinket.client.datacenter.ClientDataCenter;
import com.gy_mod.gy_trinket.menu.LightPointCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 光点核心容器界面（科幻风格）
 * 深蓝半透明面板 + 切角边框 + 蓝青标题，槽位 18px 格子 / 20px 间隔
 * 禁用物品遮罩在 render 内叠加（覆盖在物品图标上方，与玩家面板同款调用）
 * 注意：renderBg 阶段使用绝对坐标（leftPos + slot.x），物品渲染由 super.render 以相对坐标完成
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

        // 槽位底框：纯色填充 + 统一色相循环淡边框（所有槽一致；悬停高亮由自绘提供）
        // 槽位位置已在菜单中整体 +1（物品渲染于 slot.x），槽框回退 1px，使物品位于槽框内右下 1px（与玩家面板一致）
        int slotX = this.leftPos;
        int slotY = this.topPos;
        for (Slot slot : this.menu.slots) {
            int sx = slotX + slot.x - 1;
            int sy = slotY + slot.y - 1;
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

        // 先提交并渲染 super.render 累积的物品/背景批次，
        // 确保禁用遮罩与悬停高亮（后提交）渲染在物品上方（修复物品批次后渲染盖住遮罩的问题）
        g.flush();

        // 自绘悬停高亮（原版白色高亮已通过 getSlotColor 置透明取消，此处改为科幻样式）
        Slot hovered = this.hoveredSlot;
        if (hovered != null && hovered.isActive()) {
            int hx = this.leftPos + hovered.x - 1;
            int hy = this.topPos + hovered.y - 1;
            g.fill(hx + 1, hy + 1, hx + 17, hy + 17, 0x3D2A3B60);
            ScreenUtils.drawChamferRect(g, hx, hy, 18, 18, 2, 0xFF4AA8FF);
        }

        // 禁用槽位：70% 灰色遮罩 + 对角相交 ×（覆盖在物品图标上方）
        if (ClientDataCenter.isCoreContainerSynced()) {
            String[] reasons = ClientDataCenter.getDisabledReasons();
            for (Slot slot : this.menu.slots) {
                if (slot.index >= 0 && slot.index < 27 && slot.index < reasons.length) {
                    String reason = reasons[slot.index];
                    if (reason != null && !reason.isEmpty()) {
                        int sx = this.leftPos + slot.x - 1;
                        int sy = this.topPos + slot.y - 1;
                        drawDisabledOverlay(g, sx, sy, 18);
                    }
                }
            }
        }

        // 提交遮罩/高亮批次，确保渲染在物品之上
        g.flush();

        // 原版 AbstractContainerScreen.render 不自动渲染悬停 tooltip，需手动调用
        this.renderTooltip(g, mouseX, mouseY);
    }

    /**
     * 禁用物品遮罩：70% 灰色半透明 + 对角相交 × 线段。
     * 使用 RenderType.guiOverlay()（与原版槽位高亮 renderSlotHighlight 相同）绘制，
     * 该 RenderType 设计为渲染在所有 GUI 内容之上，不受物品批次顺序与深度测试影响，
     * 确保遮罩一定覆盖在物品图标上方。
     */
    private static void drawDisabledOverlay(GuiGraphics g, int x, int y, int size) {
        g.fillGradient(RenderType.guiOverlay(), x + 1, y + 1, x + size - 1, y + size - 1, 0xB0808080, 0xB0808080, 0);
        int x0 = x + 3, y0 = y + 3, x1 = x + size - 3, y1 = y + size - 3;
        for (int k = 0; k <= 9; k++) {
            g.fillGradient(RenderType.guiOverlay(), x0 + k, y0 + k, x0 + k + 3, y0 + k + 3, 0xFF000000, 0xFF000000, 0);   // 左上→右下
            g.fillGradient(RenderType.guiOverlay(), x1 - k - 3, y0 + k, x1 - k, y0 + k + 3, 0xFF000000, 0xFF000000, 0);   // 右上→左下
        }
    }

    /** 取消原版白色悬停高亮：自定义悬停样式在 render() 中绘制 */
    @Override
    public int getSlotColor(int index) {
        return 0x00000000;
    }

    /**
     * 鼠标中键一键整理：仅当鼠标位于光点核心容器槽区（非玩家背包区）且手上无物品时触发
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2 && this.menu.getCarried().isEmpty() && isMouseOverContainerArea(mouseX, mouseY)) {
            com.gy_mod.gy_trinket.network.NetworkHandler.sendSortLightPointCore();
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
