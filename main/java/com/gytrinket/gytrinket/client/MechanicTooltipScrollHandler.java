package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.gytrinket;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 特殊机制详细描述 tooltip 滚动支持。
 * <p>
 * 配合 {@link com.gytrinket.gytrinket.core.tooltip.TooltipHandler} 的折叠逻辑：
 * 物品声明多个特殊机制时，普通悬停只显示标题；按住 Shift 展开详细描述。
 * 展开后若内容超出屏幕高度，本处理器按鼠标滚轮滚动裁剪显示（Shift 保持按下时滚动）。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class MechanicTooltipScrollHandler {

    private static final int LINE_HEIGHT = 9;

    /** 当前滚动偏移（从 tooltip 顶部裁掉的行数） */
    public static int scrollOffset = 0;
    /** 最大滚动偏移（上一帧 Shift 展开且内容超高时计算） */
    private static int maxScrollOffset = 0;
    /** 滚动是否激活（Shift 展开且内容超高） */
    private static boolean scrollActive = false;

    @SubscribeEvent
    public static void onGather(RenderTooltipEvent.GatherComponents event) {
        if (!Screen.hasShiftDown()) {
            scrollActive = false;
            scrollOffset = 0;
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        // 仅处理纯文本 tooltip（含自定义组件的跳过）
        boolean allText = true;
        int totalLines = 0;
        for (Either<FormattedText, TooltipComponent> e : elements) {
            if (e.left().isPresent()) {
                totalLines++;
            } else {
                allText = false;
                break;
            }
        }
        if (!allText || totalLines == 0) {
            scrollActive = false;
            return;
        }

        int maxHeight = event.getScreenHeight() - 60;
        int visibleLines = Math.max(1, maxHeight / LINE_HEIGHT);
        maxScrollOffset = Math.max(0, totalLines - visibleLines);

        if (maxScrollOffset == 0) {
            scrollActive = false;
            scrollOffset = 0;
            return;
        }

        scrollActive = true;
        if (scrollOffset > maxScrollOffset) {
            scrollOffset = maxScrollOffset;
        }

        // 重建列表：顶部滚动提示 + 从 scrollOffset 开始的可见窗口
        List<Either<FormattedText, TooltipComponent>> trimmed = new ArrayList<>();
        trimmed.add(Either.left((FormattedText) Component.literal("▲▼ 滚轮滚动查看详细描述")
                .withStyle(ChatFormatting.GRAY)));
        for (int i = scrollOffset; i < elements.size(); i++) {
            trimmed.add(elements.get(i));
        }
        event.getTooltipElements().clear();
        event.getTooltipElements().addAll(trimmed);
    }

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (!scrollActive || !Screen.hasShiftDown()) {
            return;
        }
        double amount = event.getScrollDeltaY();
        if (amount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (amount < 0) {
            scrollOffset = Math.min(maxScrollOffset, scrollOffset + 1);
        }
        event.setCanceled(true);
    }
}
