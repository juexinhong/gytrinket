package com.gytrinket.gytrinket.client.shield;

import com.gytrinket.gytrinket.Config;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class ShieldHudRenderer {
    private static ShieldHudRenderer instance;
    private double currentShield = 0;
    private double maxShield = 0;
    private int currentCooldown = 0;
    private int maxCooldown = 0;
    private double adaptiveArmorReduction = 0;
    private static final float TEXT_SCALE = 0.75f;

    private double displayShield = 0;
    private float displayCooldownRatio = 0;
    private double displayAdaptiveArmorReduction = 0;
    private static final float LERP_SPEED = 0.005f;
    private static final float VANILLA_LERP_SPEED = 0.05f;
    private static final float VANILLA_COOLDOWN_LERP_SPEED = 0.3f;
    private static final double LERP_THRESHOLD = 0.01f;

    private static final ResourceLocation SHIELD_HUD_TEXTURE = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/gui/shield_hud.png");
    private static final ResourceLocation SHIELD_COOLDOWN_TEXTURE = ResourceLocation.fromNamespaceAndPath("gytrinket", "textures/gui/shield_cooldown_hud.png");
    private static final int TEXTURE_WIDTH = 83;
    private static final int TEXTURE_HEIGHT = 11;

    public static ShieldHudRenderer getInstance() {
        if (instance == null) {
            instance = new ShieldHudRenderer();
        }
        return instance;
    }

    private ShieldHudRenderer() {}

    public void updateShieldData(double current, double max, int currentCooldown, int maxCooldown, double adaptiveArmorReduction) {
        this.currentShield = Math.max(0, Math.min(current, max));
        this.maxShield = Math.max(0, max);
        this.currentCooldown = currentCooldown;
        this.maxCooldown = maxCooldown;
        this.adaptiveArmorReduction = adaptiveArmorReduction;
    }

    public double getCurrentShield() {
        return this.displayShield;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.screen != null) return;

        boolean vanillaStyle = Config.VANILLA_STYLE_HUD.get();
        float shieldLerpSpeed = vanillaStyle ? VANILLA_LERP_SPEED : LERP_SPEED;
        float cooldownLerpSpeed = vanillaStyle ? VANILLA_COOLDOWN_LERP_SPEED : LERP_SPEED;

        lerpShieldValues(shieldLerpSpeed);
        lerpCooldownValues(cooldownLerpSpeed);
        lerpAdaptiveArmorReduction();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (maxShield > 0 || displayAdaptiveArmorReduction > 0) {
            if (vanillaStyle) {
                drawVanillaStyleShieldHUD(guiGraphics);
            } else {
                Window window = minecraft.getWindow();
                int screenWidth = window.getGuiScaledWidth();
                int hudX = screenWidth / 2 + Config.HUD_DEFAULT_OFFSET_X.get();
                int hudY = Config.HUD_DEFAULT_OFFSET_Y.get();
                drawShieldHUD(guiGraphics, hudX, hudY);
            }
        }

        RenderSystem.disableBlend();
    }

    private void lerpShieldValues(float speed) {
        double diffShield = this.currentShield - this.displayShield;
        if (Math.abs(diffShield) > LERP_THRESHOLD) {
            this.displayShield += diffShield * speed;
            if (Math.abs(this.currentShield - this.displayShield) < 0.02f) {
                this.displayShield = this.currentShield;
            }
        } else {
            this.displayShield = this.currentShield;
        }
    }

    private void lerpCooldownValues(float speed) {
        float targetRatio = this.maxCooldown > 0 ? (float) this.currentCooldown / this.maxCooldown : 0;
        float diffRatio = targetRatio - this.displayCooldownRatio;

        if (diffRatio > 0) {
            this.displayCooldownRatio += diffRatio * speed;
            if (this.displayCooldownRatio > targetRatio) {
                this.displayCooldownRatio = targetRatio;
            }
        } else {
            this.displayCooldownRatio = targetRatio;
        }
    }

    private void lerpAdaptiveArmorReduction() {
        double diff = this.adaptiveArmorReduction - this.displayAdaptiveArmorReduction;
        if (Math.abs(diff) > LERP_THRESHOLD) {
            this.displayAdaptiveArmorReduction += diff * LERP_SPEED;
            if (Math.abs(this.adaptiveArmorReduction - this.displayAdaptiveArmorReduction) < 0.001) {
                this.displayAdaptiveArmorReduction = this.adaptiveArmorReduction;
            }
        } else {
            this.displayAdaptiveArmorReduction = this.adaptiveArmorReduction;
        }
    }

    private void drawShieldHUD(GuiGraphics guiGraphics, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barWidth = Config.HUD_DEFAULT_BAR_WIDTH.get();
        int barHeight = Config.HUD_DEFAULT_BAR_HEIGHT.get();
        int cooldownHeight = Config.HUD_DEFAULT_COOLDOWN_HEIGHT.get();

        String shieldText = String.format("%.1f / %.1f", displayShield, maxShield);
        int textWidth = (int) (font.width(shieldText) * TEXT_SCALE);

        int bgX = x - barWidth / 2;
        int bgY = y;

        if (maxShield > 0) {
            guiGraphics.fill(bgX, bgY, bgX + barWidth, bgY + barHeight, 0xFFFFFFFF);

            float fillRatio = (float) (displayShield / maxShield);
            int fillWidth = (int) (barWidth * fillRatio);

            if (fillWidth > 0) {
                guiGraphics.fill(bgX, bgY, bgX + fillWidth, bgY + barHeight, 0xFF55AACC);
            }

            int textX = bgX + (barWidth - textWidth) / 2;
            int textY = bgY + (int) ((barHeight - font.lineHeight * TEXT_SCALE) / 2);

            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(textX, textY, 0);
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1.0f);

            guiGraphics.drawString(font, shieldText, 0, 0, 0xFF0000FF, false);

            poseStack.popPose();

            if (displayCooldownRatio > 0) {
                int cooldownBgY = bgY + barHeight;
                int cooldownFillWidth = (int) (barWidth * displayCooldownRatio);

                if (cooldownFillWidth > 0) {
                    guiGraphics.fill(bgX, cooldownBgY, bgX + cooldownFillWidth, cooldownBgY + cooldownHeight, 0xFF808080);
                }
            }
        }

        if (displayAdaptiveArmorReduction > 0.001) {
            int textY = maxShield > 0 ? bgY + barHeight + cooldownHeight + 4 : y;
            String armorText = String.format("适应性装甲: -%.1f%%", displayAdaptiveArmorReduction * 100);
            int armorTextWidth = (int) (font.width(armorText) * TEXT_SCALE);
            int armorTextX = bgX + (barWidth - armorTextWidth) / 2;

            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(armorTextX, textY, 0);
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1.0f);

            guiGraphics.drawString(font, armorText, 0, 0, 0xFF00AA00, false);

            poseStack.popPose();
        }
    }

    private void drawVanillaStyleShieldHUD(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gameMode == null) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 护盾 HUD 紧贴在生命条下方（生命条底部 Y = screenHeight - 39）
        // 生命条多排心时向上扩展，底部位置不变，护盾 HUD 不会偏移
        int left = screenWidth / 2 - 92 + Config.HUD_VANILLA_OFFSET_X.get();
        int top = screenHeight - 40 + Config.HUD_VANILLA_OFFSET_Y.get();

        float scale = Config.VANILLA_STYLE_HUD_SCALE.get().floatValue();

        if (maxShield > 0) {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(left, top, 0);
            poseStack.scale(scale, scale, 1.0f);

            float shieldRatio = maxShield > 0 ? (float) (displayShield / maxShield) : 0;
            int shieldVisibleWidth = Mth.clamp((int) (TEXTURE_WIDTH * shieldRatio), 0, TEXTURE_WIDTH);

            if (shieldVisibleWidth > 0) {
                guiGraphics.blit(SHIELD_HUD_TEXTURE, 0, 0, 0, 0, shieldVisibleWidth, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }

            if (displayCooldownRatio > 0) {
                int cooldownVisibleWidth = Mth.clamp((int) (TEXTURE_WIDTH * displayCooldownRatio), 0, TEXTURE_WIDTH);

                if (cooldownVisibleWidth > 0) {
                    float alpha = Config.HUD_VANILLA_COOLDOWN_ALPHA.get().floatValue();
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                    guiGraphics.blit(SHIELD_COOLDOWN_TEXTURE, 0, 0, 0, 0, cooldownVisibleWidth, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }

            poseStack.popPose();

            double displayShieldCapped = Math.min(displayShield, 999.9);
            double maxShieldCapped = Math.min(maxShield, 999.9);
            String shieldText = String.format("%.1f / %.1f", displayShieldCapped, maxShieldCapped);
            int textWidth = font.width(shieldText);
            int scaledTextureWidth = (int) (TEXTURE_WIDTH * scale);
            int textX = left + scaledTextureWidth - textWidth + Config.HUD_VANILLA_TEXT_OFFSET_X.get();
            int textY = top - font.lineHeight - 2 + Config.HUD_VANILLA_TEXT_OFFSET_Y.get();
            guiGraphics.drawString(font, shieldText, textX, textY, 0xFF5599FF, false);
        }
    }

    public void reset() {
        this.currentShield = 0;
        this.maxShield = 0;
        this.currentCooldown = 0;
        this.maxCooldown = 0;
        this.adaptiveArmorReduction = 0;
        this.displayShield = 0;
        this.displayCooldownRatio = 0;
        this.displayAdaptiveArmorReduction = 0;
    }
}
