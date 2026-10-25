package com.gytrinket.gytrinket.client.shader;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class ModShaders {
    public static final ResourceLocation ENERGY_WAVE_SHADER = new ResourceLocation("gytrinket", "shaders/core/energy_wave");
    public static final ResourceLocation VOLMETRIC_RENDER_SHADER = new ResourceLocation("gytrinket", "shaders/core/volumetric_render");
    public static final ResourceLocation GHOST_FUSELAGE_SHADER = new ResourceLocation("gytrinket", "shaders/core/ghost_fuselage");
    public static final ResourceLocation SWARM_SHADER = new ResourceLocation("gytrinket", "shaders/core/swarm");

    // ===== ShaderInstance 对象缓存（运行时填充） =====
    private static ShaderInstance shieldGlassShader;
    /** 同一套 vsh/fsh，但 JSON 里的 blend 是加色（srcalpha, one）：光晕与高光用 */
    private static ShaderInstance shieldGlassAddShader;
    /** 高光 Pass 专用（PassMode 在 JSON 里就写死 2.0，不依赖 uniform） */
    private static ShaderInstance shieldGlassHiShader;
    /** 旧版贴图棱柱专用（历史 glass 着色器，带 Sampler0 / Brightness） */
    private static ShaderInstance shieldGlassLegacyShader;
    private static ShaderInstance energyWaveVolShader;
    private static ShaderInstance lightningVolShader;

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
    }

    public static ResourceLocation getEnergyWaveShader() {
        return ENERGY_WAVE_SHADER;
    }

    public static ResourceLocation getVolumetricRenderShader() {
        return VOLMETRIC_RENDER_SHADER;
    }

    public static ResourceLocation getGhostFuselageShader() {
        return GHOST_FUSELAGE_SHADER;
    }

    public static ResourceLocation getSwarmShader() {
        return SWARM_SHADER;
    }

    // ===== ShieldGlass 着色器（护盾粒子体积渲染） =====

    public static void setShieldGlassShader(ShaderInstance shader) {
        shieldGlassShader = shader;
    }

    @Nullable
    public static ShaderInstance getShieldGlassShader() {
        return shieldGlassShader;
    }

    public static void setShieldGlassAddShader(ShaderInstance shader) {
        shieldGlassAddShader = shader;
    }

    @Nullable
    public static ShaderInstance getShieldGlassAddShader() {
        return shieldGlassAddShader;
    }

    public static void setShieldGlassHiShader(ShaderInstance shader) {
        shieldGlassHiShader = shader;
    }

    @Nullable
    public static ShaderInstance getShieldGlassHiShader() {
        return shieldGlassHiShader;
    }

    // ===== 旧版「贴图转立体」着色器（style = 1，历史外观） =====

    public static void setShieldGlassLegacyShader(ShaderInstance shader) {
        shieldGlassLegacyShader = shader;
    }

    @Nullable
    public static ShaderInstance getShieldGlassLegacyShader() {
        return shieldGlassLegacyShader;
    }

    // ===== EnergyWave 体积着色器（能量波 raymarching） =====

    public static void setEnergyWaveVolShader(ShaderInstance shader) {
        energyWaveVolShader = shader;
    }

    @Nullable
    public static ShaderInstance getEnergyWaveVolShader() {
        if (energyWaveVolShader == null) {
        }
        return energyWaveVolShader;
    }

    // ===== LightningVol 着色器（闪电3D圆柱体积渲染） =====

    public static void setLightningVolShader(ShaderInstance shader) {
        lightningVolShader = shader;
    }

    @Nullable
    public static ShaderInstance getLightningVolShader() {
        return lightningVolShader;
    }

    // ===== IslandSky 着色器（孤岛维度全程序化星云天空） =====

    private static ShaderInstance islandSkyShader;

    public static void setIslandSkyShader(ShaderInstance shader) {
        islandSkyShader = shader;
    }

    @Nullable
    public static ShaderInstance getIslandSkyShader() {
        return islandSkyShader;
    }
}

