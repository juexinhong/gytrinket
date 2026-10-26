package com.gytrinket.gytrinket.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 1.19.2 的绘制适配层：API 形状对齐 1.20+ 的 {@code GuiCanvas}，
 * 让 1.20.1 分支的界面代码可以机械替换（{@code guiGraphics.} 保持不变），
 * 以后要升版本只换这一层实现。
 * <p>
 * 1.19.2 的绘制入口是 {@link PoseStack} 静态工具 {@link GuiComponent}，
 * 本类继承 {@link GuiComponent} 以便复用其 protected 静态方法（fillGradient 等），
 * 不做反射、不产生额外绘制批次。
 * <p>
 * 约定：{@link #screen()} 可为 null（HUD 场景），此时 tooltip 相关调用静默跳过。
 */
public class GuiCanvas extends GuiComponent {

    private final PoseStack pose;
    @Nullable
    private final Screen screen;

    public GuiCanvas(PoseStack pose, @Nullable Screen screen) {
        this.pose = pose;
        this.screen = screen;
    }

    /** 便捷构造：界面里通常写成 {@code GuiCanvas guiGraphics = canvas(poseStack);} */
    public static GuiCanvas of(PoseStack pose, @Nullable Screen screen) {
        return new GuiCanvas(pose, screen);
    }

    public PoseStack pose() {
        return pose;
    }

    @Nullable
    public Screen screen() {
        return screen;
    }

    // ==================== 底色 / 渐变 ====================

    public void fill(int x1, int y1, int x2, int y2, int color) {
        fill(pose, x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        // 1.19.2 的实例方法会带上当前 blitOffset，语义与 1.20 的 GuiCanvas#fillGradient 一致
        this.fillGradient(pose, x1, y1, x2, y2, colorFrom, colorTo);
    }

    /** 1.20 的带 RenderType 重载：1.19.2 立即模式不需要批次类型，忽略之。 */
    public void fillGradient(RenderType renderType, int x1, int y1, int x2, int y2,
                             int colorFrom, int colorTo, int z) {
        this.fillGradient(pose, x1, y1, x2, y2, colorFrom, colorTo);
    }

    /**
     * 以「GUI 覆盖层」语义绘制渐变（对齐 1.20.1 的 {@code RenderType.guiOverlay()}）：
     * 关闭深度测试并把 z 提到物品与物品装饰之上。
     * <p>
     * 1.19.2 的物品在 GUI 里渲染于 z=100+blitOffset（容器界面 blitOffset=100 → z=200，
     * 数量/耐久装饰在 z=300），普通 fill 在 z=0 会被深度测试挡在物品下方。
     * 容器界面的禁用遮罩必须用本方法而不是 {@link #fillGradient}。
     */
    public void fillGradientOverlay(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        RenderSystem.disableDepthTest();
        pose.pushPose();
        pose.translate(0.0, 0.0, 400.0);
        fillGradient(pose, x1, y1, x2, y2, colorFrom, colorTo);
        pose.popPose();
        RenderSystem.enableDepthTest();
    }

    // ==================== 文本 ====================

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawCenteredString(Font font, String text, int x, int y, int color) {
        drawCenteredString(pose, font, text, x, y, color);
        return 0;
    }

    public int drawCenteredString(Font font, Component text, int x, int y, int color) {
        drawCenteredString(pose, font, text, x, y, color);
        return 0;
    }

    // ==================== 贴图 ====================

    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        RenderSystem.setShaderTexture(0, texture);
        blit(pose, x, y, width, height, (float) u, (float) v, width, height, 256, 256);
    }

    public void blit(ResourceLocation texture, int x, int y, int u, int v,
                     int width, int height, int texWidth, int texHeight) {
        RenderSystem.setShaderTexture(0, texture);
        blit(pose, x, y, width, height, (float) u, (float) v, width, height, texWidth, texHeight);
    }

    // ==================== 物品 ====================

    public void renderItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x, y);
    }

    // ==================== 裁剪 ====================

    // 1.19.2 的 GuiComponent 已有 public static enableScissor/disableScissor，
    // 通过 GuiCanvas 实例调用会解析到这两个静态方法，无需再包一层。

    /** 1.20 的批次提交入口；1.19.2 立即模式无对应物，保持空实现以免调用点分叉。 */
    public void flush() {
        // no-op
    }

    // ==================== tooltip ====================

    public void renderTooltip(Component text, int x, int y) {
        if (screen != null) {
            screen.renderTooltip(pose, text, x, y);
        }
    }

    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        if (screen == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        TooltipFlag flag = mc.options.advancedItemTooltips
                ? TooltipFlag.Default.ADVANCED
                : TooltipFlag.Default.NORMAL;
        List<Component> lines = stack.getTooltipLines(mc.player, flag);
        screen.renderTooltip(pose, lines, Optional.empty(), x, y, stack);
    }

    public void renderTooltip(Font font, List<Component> lines, Optional<?> tooltipComponent, int x, int y) {
        if (screen == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> component =
                (Optional<net.minecraft.world.inventory.tooltip.TooltipComponent>) tooltipComponent;
        screen.renderTooltip(pose, lines, component, x, y);
    }

    public void renderTooltip(Font font, List<Component> lines, int x, int y) {
        renderTooltip(font, lines, Optional.empty(), x, y);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        if (screen != null) {
            screen.renderComponentTooltip(pose, lines, x, y);
        }
    }
}
