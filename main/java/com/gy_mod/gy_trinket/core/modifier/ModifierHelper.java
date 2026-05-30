package com.gy_mod.gy_trinket.core.modifier;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

public final class ModifierHelper {

    public static final String MOD_PREFIX = "gy_trinket:";

    private ModifierHelper() {}

    public static void removeAllModModifiers(AttributeInstance attribute) {
        if (attribute == null) {
            return;
        }
        List<AttributeModifier> toRemove = new ArrayList<>();
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getName().startsWith(MOD_PREFIX)) {
                toRemove.add(modifier);
            }
        }
        for (AttributeModifier modifier : toRemove) {
            attribute.removeModifier(modifier);
        }
    }

    public static void removeModifierByUuid(AttributeInstance attribute, java.util.UUID uuid) {
        if (attribute == null) {
            return;
        }
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getId().equals(uuid)) {
                attribute.removeModifier(modifier);
                return;
            }
        }
    }
}
