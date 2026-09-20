package com.gytrinket.gytrinket.client.shield;

import com.gytrinket.gytrinket.config.ClientConfig;
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
    private double adaptiveArmorReduction = 0;
    private static final float TEXT_SCALE = 0.75f;

    private double displayShield = 0;
    private double displayFocusShield = 0; // 焦点实例（当前护盾）平滑显示值
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

    /** 护盾实例列表（服务端 ShieldInstance 镜像，实例列表序 = 装备扫描序） */
    private final java.util.List<com.gytrinket.gytrinket.network.packet.SyncShieldPayload.InstanceShieldData> instances =
            new java.util.ArrayList<>();

    public static ShieldHudRenderer getInstance() {
        if (instance == null) {
            instance = new ShieldHudRenderer();
        }
        return instance;
    }

    private ShieldHudRenderer() {}

    public void updateShieldData(double current, double max, double adaptiveArmorReduction) {
        this.currentShield = Math.max(0, Math.min(current, max));
        this.maxShield = Math.max(0, max);
        this.adaptiveArmorReduction = adaptiveArmorReduction;
    }

    /** 同步护盾实例列表（HUD 焦点实例数据源） */
    public void updateInstances(java.util.List<com.gytrinket.gytrinket.network.packet.SyncShieldPayload.InstanceShieldData> instances) {
        this.instances.clear();
        this.instances.addAll(instances);
    }

    /**
     * 焦点实例（当前护盾）：第一个未破盾且有池量的实例 = 当前能够承受且最先承受伤害的护盾。
     */
    private com.gytrinket.gytrinket.network.packet.SyncShieldPayload.InstanceShieldData findFocusInstance() {
        for (com.gytrinket.gytrinket.network.packet.SyncShieldPayload.InstanceShieldData inst : instances) {
            if (!inst.broken && inst.currentShield > 0) {
                return inst;
            }
        }
        return null;
    }

    /**
     * 冷却条数据源：焦点实例的冷却充能进度；无焦点实例（全部破盾）时
     * 回退显示第一个冷却充能实例（破盾回满 / 装备入场充能可见）。
     */
    private float cooldownTargetRatio() {
        var focus = findFocusInstance();
        if (focus != null) {
            return focus.isCoolingDown() && focus.maxCooldown > 0
                    ? Mth.clamp((float) focus.cooldownProgress / focus.maxCooldown, 0.0f, 1.0f)
                    : 0.0f;
        }
        for (com.gytrinket.gytrinket.network.packet.SyncShieldPayload.InstanceShieldData inst : instances) {
            if (inst.isCoolingDown() && inst.maxCooldown > 0) {
                return Mth.clamp((float) inst.cooldownProgress / inst.maxCooldown, 0.0f, 1.0f);
            }
        }
        return 0.0f;
    }

    public double getCurrentShield() {
        return this.displayShield;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.screen != null) return;

        boolean vanillaStyle = ClientConfig.VANILLA_STYLE_HUD.get();
        float shieldLerpSpeed = vanillaStyle ? VANILLA_LERP_SPEED : LERP_SPEED;
        float cooldownLerpSpeed = vanillaStyle ? VANILLA_COOLDOWN_LERP_SPEED : LERP_SPEED;

        lerpShieldValues(shieldLerpSpeed);
        lerpFocusShield(shieldLerpSpeed);
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
                int hudX = screenWidth / 2 + ClientConfig.HUD_DEFAULT_OFFSET_X.get();
                int hudY = ClientConfig.HUD_DEFAULT_OFFSET_Y.get();
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

    /** 焦点实例（当前护盾）平滑：目标 = 可用实例的当前池量，冷却/破盾实例计 0 */
    private void lerpFocusShield(float speed) {
        var focus = findFocusInstance();
        double target = (focus != null && !focus.broken) ? focus.currentShield : 0;
        double diff = target - this.displayFocusShield;
        if (Math.abs(diff) > LERP_THRESHOLD) {
            this.displayFocusShield += diff * speed;
            if (Math.abs(target - this.displayFocusShield) < 0.02f) {
                this.displayFocusShield = target;
            }
        } else {
            this.displayFocusShield = target;
        }
    }

    private void lerpCooldownValues(float speed) {
        float targetRatio = cooldownTargetRatio();
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

        int barWidth = ClientConfig.HUD_DEFAULT_BAR_WIDTH.get();
        int barHeight = ClientConfig.HUD_DEFAULT_BAR_HEIGHT.get();
        int cooldownHeight = ClientConfig.HUD_DEFAULT_COOLDOWN_HEIGHT.get();

        String shieldText = String.format("%.1f / %.1f", displayShield, maxShield);
        int textWidth = (int) (font.width(shieldText) * TEXT_SCALE);

        int bgX = x - barWidth / 2;
        int bgY = y;

        if (maxShield > 0) {
            guiGraphics.fill(bgX, bgY, bgX + barWidth, bgY + barHeight, 0xFFFFFFFF);

            // 暗层：总计护盾值
            float fillRatio = (float) (displayShield / maxShield);
            int fillWidth = (int) (barWidth * fillRatio);

            if (fillWidth > 0) {
                guiGraphics.fill(bgX, bgY, bgX + fillWidth, bgY + barHeight, 0xFF2A5566);
            }

            // 亮层：当前护盾值（最先承受伤害的护盾），同一位置叠加
            var focus = findFocusInstance();
            if (focus != null && !focus.broken && focus.maxShield > 0 && displayFocusShield > 0) {
                float focusRatio = Mth.clamp((float) (displayFocusShield / focus.maxShield), 0.0f, 1.0f);
                int focusWidth = (int) (barWidth * focusRatio);
                if (focusWidth > 0) {
                    guiGraphics.fill(bgX, bgY, bgX + focusWidth, bgY + barHeight, 0xFF55AACC);
                }
            }

            int textX = bgX + (barWidth - textWidth) / 2;
            int textY = bgY + (int) ((barHeight - font.lineHeight * TEXT_SCALE) / 2);

            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(textX, textY, 0);
            poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1.0f);

            guiGraphics.drawString(font, shieldText, 0, 0, 0xFF0000FF, false);

            poseStack.popPose();

            // 冷却条：当前护盾冷却进度（主条下方灰块）
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
        int left = screenWidth / 2 - 92 + ClientConfig.HUD_VANILLA_OFFSET_X.get();
        int top = screenHeight - 40 + ClientConfig.HUD_VANILLA_OFFSET_Y.get();

        float scale = ClientConfig.VANILLA_STYLE_HUD_SCALE.get().floatValue();

        if (maxShield > 0) {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(left, top, 0);
            poseStack.scale(scale, scale, 1.0f);

            // 暗层：总计护盾值（亮度调暗）
            float shieldRatio = maxShield > 0 ? (float) (displayShield / maxShield) : 0;
            int shieldVisibleWidth = Mth.clamp((int) (TEXTURE_WIDTH * shieldRatio), 0, TEXTURE_WIDTH);

            if (shieldVisibleWidth > 0) {
                RenderSystem.setShaderColor(0.45f, 0.45f, 0.45f, 1.0f);
                guiGraphics.blit(SHIELD_HUD_TEXTURE, 0, 0, 0, 0, shieldVisibleWidth, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // 亮层：当前护盾值（最先承受伤害的护盾），同一位置叠加
            var focus = findFocusInstance();
            if (focus != null && !focus.broken && focus.maxShield > 0 && displayFocusShield > 0) {
                float focusRatio = Mth.clamp((float) (displayFocusShield / focus.maxShield), 0.0f, 1.0f);
                int focusVisibleWidth = Mth.clamp((int) (TEXTURE_WIDTH * focusRatio), 0, TEXTURE_WIDTH);
                if (focusVisibleWidth > 0) {
                    guiGraphics.blit(SHIELD_HUD_TEXTURE, 0, 0, 0, 0, focusVisibleWidth, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                }
            }

            // 冷却条：当前护盾冷却进度（贴图叠加，透明度走配置）
            if (displayCooldownRatio > 0) {
                int cooldownVisibleWidth = Mth.clamp((int) (TEXTURE_WIDTH * displayCooldownRatio), 0, TEXTURE_WIDTH);

                if (cooldownVisibleWidth > 0) {
                    float alpha = ClientConfig.HUD_VANILLA_COOLDOWN_ALPHA.get().floatValue();
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
            int textX = left + scaledTextureWidth - textWidth + ClientConfig.HUD_VANILLA_TEXT_OFFSET_X.get();
            int textY = top - font.lineHeight - 2 + ClientConfig.HUD_VANILLA_TEXT_OFFSET_Y.get();
            guiGraphics.drawString(font, shieldText, textX, textY, 0xFF5599FF, false);
        }
    }

    public void reset() {
        this.currentShield = 0;
        this.maxShield = 0;
        this.adaptiveArmorReduction = 0;
        this.displayShield = 0;
        this.displayFocusShield = 0;
        this.displayCooldownRatio = 0;
        this.displayAdaptiveArmorReduction = 0;
        this.instances.clear();
    }
}
