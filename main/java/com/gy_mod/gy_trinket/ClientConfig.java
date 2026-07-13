package com.gy_mod.gy_trinket;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 客户端配置类
 * 仅在客户端生效的配置项（HUD渲染、粒子特效等）
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ===== 护盾待机粒子 =====
    public static final ForgeConfigSpec.ConfigValue<Boolean> SHIELD_IDLE_PARTICLE_ENABLED;

    // ===== 护盾HUD =====
    public static final ForgeConfigSpec.ConfigValue<Boolean> VANILLA_STYLE_HUD;
    public static final ForgeConfigSpec.DoubleValue VANILLA_STYLE_HUD_SCALE;
    public static final ForgeConfigSpec.DoubleValue HUD_VANILLA_COOLDOWN_ALPHA;

    // ===== 默认样式HUD =====
    public static final ForgeConfigSpec.IntValue HUD_DEFAULT_OFFSET_X;
    public static final ForgeConfigSpec.IntValue HUD_DEFAULT_OFFSET_Y;
    public static final ForgeConfigSpec.IntValue HUD_DEFAULT_BAR_WIDTH;
    public static final ForgeConfigSpec.IntValue HUD_DEFAULT_BAR_HEIGHT;
    public static final ForgeConfigSpec.IntValue HUD_DEFAULT_COOLDOWN_HEIGHT;

    // ===== 原版样式HUD =====
    public static final ForgeConfigSpec.IntValue HUD_VANILLA_OFFSET_X;
    public static final ForgeConfigSpec.IntValue HUD_VANILLA_OFFSET_Y;
    public static final ForgeConfigSpec.IntValue HUD_VANILLA_TEXT_OFFSET_X;
    public static final ForgeConfigSpec.IntValue HUD_VANILLA_TEXT_OFFSET_Y;

    public static final ForgeConfigSpec SPEC;

    private static boolean initialized = false;

    public static boolean isLoaded() {
        return initialized;
    }

    static {
        BUILDER.comment("护盾待机粒子配置").push("shield_idle_particle");

        SHIELD_IDLE_PARTICLE_ENABLED = BUILDER.comment(
            "是否启用护盾待机粒子特效",
            "启用后，当玩家拥有护盾时，每隔一定时间在玩家两侧生成护盾粒子",
            "默认不启用"
        ).define("enabled", false);

        BUILDER.pop();

        BUILDER.comment("护盾HUD配置").push("shield_hud");

        VANILLA_STYLE_HUD = BUILDER.comment(
            "是否使用原版样式HUD",
            "启用后，护盾将使用纹理渲染在原版生命条位置，而非默认的纯色长条",
            "在原版生命条上渲染边描,注意只是模拟而不是真正意义上的描边",
            "护盾值减少时，纹理从右往左消失",
            "冷却条以深蓝50%透明度独立叠加渲染",
            "数值在纹理上方居中显示"
        ).define("vanillaStyle", true);

        BUILDER.comment("默认样式HUD配置").push("default_style");

        HUD_DEFAULT_OFFSET_X = BUILDER.comment(
            "默认样式HUD的X偏移量",
            "基于屏幕中心，0为居中，正数向右，负数向左",
            "默认0"
        ).defineInRange("offsetX", 0, -500, 500);

        HUD_DEFAULT_OFFSET_Y = BUILDER.comment(
            "默认样式HUD的Y偏移量",
            "基于屏幕顶部，默认6",
            "默认6"
        ).defineInRange("offsetY", 6, 0, 500);

        HUD_DEFAULT_BAR_WIDTH = BUILDER.comment(
            "默认样式护盾条宽度（像素）",
            "默认150"
        ).defineInRange("barWidth", 150, 10, 500);

        HUD_DEFAULT_BAR_HEIGHT = BUILDER.comment(
            "默认样式护盾条高度（像素）",
            "默认5"
        ).defineInRange("barHeight", 5, 1, 50);

        HUD_DEFAULT_COOLDOWN_HEIGHT = BUILDER.comment(
            "默认样式冷却条高度（像素）",
            "默认2"
        ).defineInRange("cooldownHeight", 2, 1, 50);

        BUILDER.pop();

        BUILDER.comment("原版样式HUD配置").push("vanilla_style");

        VANILLA_STYLE_HUD_SCALE = BUILDER.comment(
            "原版样式HUD的缩放比例",
            "1.0为原始大小，0.5为缩小一半，2.0为放大两倍",
            "默认1.0"
        ).defineInRange("scale", 1.0, 0.1, 3.0);

        HUD_VANILLA_OFFSET_X = BUILDER.comment(
            "原版样式HUD的X偏移量",
            "基于原版生命条位置，0为默认对齐，正数向右，负数向左",
            "默认0"
        ).defineInRange("offsetX", 0, -500, 500);

        HUD_VANILLA_OFFSET_Y = BUILDER.comment(
            "原版样式HUD的Y偏移量",
            "基于原版生命条位置，0为默认对齐，正数向下，负数向上",
            "默认0"
        ).defineInRange("offsetY", 0, -500, 500);

        HUD_VANILLA_COOLDOWN_ALPHA = BUILDER.comment(
            "原版样式冷却条的透明度",
            "0.0为完全透明，1.0为完全不透明",
            "默认0.5（50%透明度）"
        ).defineInRange("cooldownAlpha", 0.7, 0.0, 1.0);

        HUD_VANILLA_TEXT_OFFSET_X = BUILDER.comment(
            "原版样式护盾值文本的X偏移量",
            "基于纹理左侧，0为左对齐，正数向右，负数向左",
            "默认0"
        ).defineInRange("textOffsetX", -85, -500, 500);

        HUD_VANILLA_TEXT_OFFSET_Y = BUILDER.comment(
            "原版样式护盾值文本的Y偏移量",
            "基于纹理上方，0为默认位置，正数向下，负数向上",
            "默认0"
        ).defineInRange("textOffsetY", 13, -500, 500);

        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        if (initialized) {
            return;
        }
        initialized = true;
        gytrinket.LOGGER.info("客户端配置加载完成");
    }
}
