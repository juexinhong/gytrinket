package com.gytrinket.gytrinket.core.sound;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, gytrinket.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_HIT = SOUND_EVENTS.register("shield_hit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(gytrinket.MODID, "shield_hit")));
}
