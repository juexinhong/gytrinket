package com.gy_mod.gy_trinket.client.random_build;

import com.gy_mod.gy_trinket.client.datacenter.ClientDataCenter;
import com.gy_mod.gy_trinket.client.screen.ScreenUtils;
import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.random_build.RandomBuildManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.key.KeyInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 随机构建系统 HUD 提示
 * <p>
 * 玩家"可升级"（可从随机池获取物品：升级点足够 / 代币足够，且光点核心未满）时，
 * 在物品栏上方提示按绑定键打开升级界面。提示文案使用玩家实际绑定的按键名。
 * <p>
 * 提醒机制：出现后持续显示 5 秒，随后 3 秒内透明度降至 0 并向下滑出屏幕；
 * 淡出结束后进入隐藏状态（位置保持在屏幕外，透明度 0，不渲染）。
 * 仅当状态从"不可升级"变为"可升级"（无→有）时才开启新周期，回到原位并恢复透明度。
 * "有→有 / 有→无"不打断当前周期，避免透明度突变。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class RandomBuildHudEvent {

    /** 完整显示时长（毫秒）：期间全亮（叠加原有呼吸透明度） */
    private static final long SHOW_MS = 5000;
    /** 淡出时长（毫秒）：透明度 1→0，同时向下滑出屏幕 */
    private static final long FADE_MS = 3000;

    private static boolean renderedThisFrame = false;
    /** 上一帧玩家是否可升级（用于"无→有"判定） */
    private static boolean hadAffordable = false;
    /** 当前提醒周期开始时间（毫秒）；-1 = 隐藏状态（位置在屏幕外，透明度保持 0） */
    private static long hintCycleStart = -1;

    @SubscribeEvent
    public static void onRenderGameOverlay(RenderGuiOverlayEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;
        if (!Config.isRandomBuildEnabled()) return;
        if (!Config.isShowUpgradeReminderHud()) return;

        boolean affordable = isAffordable();
        long now = System.currentTimeMillis();

        // 状态机：仅"无→有"开启新提醒周期（回到原位并恢复透明度）；
        // 其余情况（有→有、有→无）不打断当前周期，避免透明度/位置突变
        if (affordable && !hadAffordable) {
            hintCycleStart = now;
        }
        hadAffordable = affordable;

        // 隐藏状态：位置保持在屏幕外，透明度 0（不渲染）
        if (hintCycleStart < 0) return;

        long elapsed = now - hintCycleStart;
        if (elapsed >= SHOW_MS + FADE_MS) {
            // 周期自然结束：进入隐藏状态（位置屏幕外、透明度 0），直到下一次"无→有"
            hintCycleStart = -1;
            return;
        }

        // 淡出进度 0~1：透明度线性 1→0；下滑位移用 ease-in 曲线（速度慢→快）滑出屏幕
        float fade = 1.0f;
        float slideProgress = 0.0f;
        if (elapsed >= SHOW_MS) {
            float t = Math.min(1.0f, (float) (elapsed - SHOW_MS) / FADE_MS);
            slideProgress = t * t;                 // ease-in 二次曲线：慢→快
            fade = Math.max(0.0f, 1.0f - t);       // 透明度线性淡出
        }
        // 淡出已结束（透明度 0 且在屏幕外）：不再绘制
        if (fade <= 0.0f) return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.type().id())) {
            renderedThisFrame = true;
            render(event.getGuiGraphics(), fade, slideProgress);
        } else if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.type().id()) && !renderedThisFrame) {
            // HOTBAR 未触发的兜底
            render(event.getGuiGraphics(), fade, slideProgress);
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.type().id())) {
            renderedThisFrame = false;
        }
    }

    /** 玩家当前是否可升级：光点核心未满，且（代币模式有代币 / 升级点模式升级点足够支付一次获取） */
    private static boolean isAffordable() {
        if (ClientDataCenter.getSnapshot().isCoreFull()) return false;
        if (Config.isRandomBuildTokenEnabled()) {
            return ClientDataCenter.getTokenCount() > 0;
        }
        return ClientDataCenter.getUpgradePoints() >= RandomBuildManager.getEquipUpgradePointCost();
    }

    private static void render(GuiGraphics g, float fade, float slideProgress) {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 使用实际绑定的按键名（玩家可自定义面板打开按键）
        Component keyMessage = KeyInputHandler.getAttributeKey().getTranslatedKeyMessage();
        Component text = Component.translatable("hud.gytrinket.random_build.hint", keyMessage);
        int textWidth = mc.font.width(text);
        int barWidth = textWidth + 24;
        int barHeight = 14;
        int x = (width - barWidth) / 2;
        // 原版物品栏大约在 guiHeight-44，提示条放在其上方；淡出时向下滑出屏幕
        int baseY = height - 44 - barHeight - 6;
        int y = baseY + (int) (slideProgress * (height + barHeight));

        long time = System.currentTimeMillis();

        // 整体透明度循环 35% ~ 70%（周期 4s，与流动边框同周期），再乘淡出系数
        double t = time / 4000.0;
        float alpha01 = (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * t)); // -1~1 -> 0~1
        int bgAlpha = (int) (0.35f * 255 + 0.35f * 255 * alpha01);        // 35% ~ 70%
        bgAlpha = (int) (bgAlpha * fade);
        int bgColor = ScreenUtils.withAlpha(0x2A3D66, bgAlpha);

        // 背景（深蓝半透明）
        g.fill(x, y, x + barWidth, y + barHeight, bgColor);

        // 流动切角边框：右上角与左下角削角，蓝青循环流动（周期 4s），随淡出同步变透明
        int borderColor = ScreenUtils.withAlpha(0x4FC3F7, (int) (255 * fade));
        ScreenUtils.drawChamferRect(g, x, y, barWidth, barHeight, 4, borderColor);

        // 青色流动字体（与边框同风格），随淡出同步变透明
        int textColor = ScreenUtils.flowingColor(0xFF66F2E0, time, 0.5f);
        textColor = ScreenUtils.withAlpha(textColor, (int) ((textColor >>> 24) * fade));
        g.drawString(mc.font, text, x + 12, y + 3, textColor, false);
    }
}
