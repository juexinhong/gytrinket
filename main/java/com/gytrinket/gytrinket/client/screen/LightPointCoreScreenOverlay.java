package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.client.datacenter.ClientDataCenter;
import com.gytrinket.gytrinket.gytrinket;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 光点核心容器界面辅助事件
 * 遮罩/× 由 LightPointCoreScreen 渲染，这里负责悬停禁用原因的 tooltip 追加，
 * 以及容器关闭时重置同步标志
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class LightPointCoreScreenOverlay {

    private LightPointCoreScreenOverlay() {}

    /** 屏幕关闭时重置同步标志，避免普通箱子误显示遮罩 */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            ClientDataCenter.setCoreContainerSynced(false);
        }
    }

    /** 光点核心容器悬停禁用物品时，在 tooltip 末尾追加禁用原因 */
    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> container)) return;
        if (!ClientDataCenter.isCoreContainerSynced()) return;

        String[] reasons = ClientDataCenter.getDisabledReasons();
        if (reasons.length == 0) return;

        int maxSlots = Math.min(27, Math.min(reasons.length, container.getMenu().slots.size()));
        for (int i = 0; i < maxSlots; i++) {
            String reason = reasons[i];
            if (reason == null || reason.isEmpty()) continue;
            Slot slot = container.getMenu().slots.get(i);
            if (slot != null && ItemStack.isSameItemSameComponents(slot.getItem(), event.getItemStack())) {
                event.getTooltipElements().add(Either.left(
                        (net.minecraft.network.chat.FormattedText) Component.literal(reason)
                                .withStyle(net.minecraft.ChatFormatting.RED)));
                return;
            }
        }
    }
}
