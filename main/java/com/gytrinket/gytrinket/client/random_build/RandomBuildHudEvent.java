package com.gytrinket.gytrinket.client.random_build;

import com.gytrinket.gytrinket.client.datacenter.ClientDataCenter;
import com.gytrinket.gytrinket.client.screen.ScreenUtils;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 随机构建系统 HUD 提示
 * 当玩家有未使用的升级点时，在物品栏上方提示按 G 键打开升级界面
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class RandomBuildHudEvent {

    private static boolean renderedThisFrame = false;

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiLayerEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;
        if (!Config.isRandomBuildEnabled()) return;
        if (!Config.isShowUpgradeReminderHud()) return;
        if (ClientDataCenter.getUpgradePoints() <= 0) return;
        // 光点核心已满时不再提示
        if (ClientDataCenter.getSnapshot().isCoreFull()) return;

        if (event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            renderedThisFrame = true;
            render(event.getGuiGraphics());
        } else if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL) && !renderedThisFrame) {
            // HOTBAR 未触发的兜底
            render(event.getGuiGraphics());
        }
        if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            renderedThisFrame = false;
        }
    }

    private static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int width = g.guiWidth();
        int height = g.guiHeight();

        String text = Component.translatable("hud.gytrinket.random_build.hint").getString();
        int textWidth = mc.font.width(text);
        int barWidth = textWidth + 24;
        int barHeight = 14;
        int x = (width - barWidth) / 2;
        // 原版物品栏大约在 guiHeight-44，提示条放在其上方
        int y = height - 44 - barHeight - 6;

        long time = System.currentTimeMillis();

        // 整体透明度循环 35% ~ 70%（周期 4s，与流动边框同周期）
        double t = time / 4000.0;
        float alpha01 = (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * t)); // -1~1 -> 0~1
        int bgAlpha = (int) (0.35f * 255 + 0.35f * 255 * alpha01);        // 35% ~ 70%
        int bgColor = ScreenUtils.withAlpha(0x2A3D66, bgAlpha);

        // 背景（深蓝半透明）
        g.fill(x, y, x + barWidth, y + barHeight, bgColor);

        // 流动切角边框：右上角与左下角削角，蓝青循环流动（周期 4s）
        ScreenUtils.drawChamferRect(g, x, y, barWidth, barHeight, 4, 0xFF4FC3F7);

        // 青色流动字体（与边框同风格）
        int textColor = ScreenUtils.flowingColor(0xFF66F2E0, time, 0.5f);
        g.drawString(mc.font, text, x + 12, y + 3, textColor, false);
    }
}
