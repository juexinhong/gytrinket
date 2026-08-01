package com.gytrinket.gytrinket.client.shader;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class ModShaders {
    public static final ResourceLocation ENERGY_WAVE_SHADER = ResourceLocation.fromNamespaceAndPath("gytrinket", "shaders/core/energy_wave");
    public static final ResourceLocation VOLMETRIC_RENDER_SHADER = ResourceLocation.fromNamespaceAndPath("gytrinket", "shaders/core/volumetric_render");
    public static final ResourceLocation GHOST_FUSELAGE_SHADER = ResourceLocation.fromNamespaceAndPath("gytrinket", "shaders/core/ghost_fuselage");
    public static final ResourceLocation SWARM_SHADER = ResourceLocation.fromNamespaceAndPath("gytrinket", "shaders/core/swarm");

    // ===== ShaderInstance 对象缓存（运行时填充） =====
    private static ShaderInstance shieldGlassShader;
    private static ShaderInstance energyWaveVolShader;

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
}
