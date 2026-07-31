package com.gy_mod.gy_trinket.client.shader;

import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nullable;

public class ModShaders {

    private static ShaderInstance shieldGlassShader;
    private static ShaderInstance energyWaveVolShader;

    public static void setShieldGlassShader(ShaderInstance shader) {
        shieldGlassShader = shader;
    }

    @Nullable
    public static ShaderInstance getShieldGlassShader() {
        return shieldGlassShader;
    }

    public static void setEnergyWaveVolShader(ShaderInstance shader) {
        energyWaveVolShader = shader;
    }

    @Nullable
    public static ShaderInstance getEnergyWaveVolShader() {
        return energyWaveVolShader;
    }
}
