package com.gy_mod.gy_trinket.core.sound;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, gytrinket.MODID);

    public static final RegistryObject<SoundEvent> SHIELD_HIT = SOUND_EVENTS.register("shield_hit",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(gytrinket.MODID, "shield_hit")));
}
