package com.gytrinket.gytrinket.core.modifier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

public final class ModifierHelper {

    public static final String MOD_PREFIX = "gytrinket:";
    public static final String MOD_NAMESPACE = "gytrinket";

    private ModifierHelper() {}

    public static void removeAllModModifiers(AttributeInstance attribute) {
        if (attribute == null) {
            return;
        }
        List<AttributeModifier> toRemove = new ArrayList<>();
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.id().getNamespace().equals(MOD_NAMESPACE)) {
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
        // In 1.21.1, AttributeModifier uses ResourceLocation as ID, not UUID
        // This method is kept for compatibility but should be replaced with ResourceLocation-based lookups
        for (AttributeModifier modifier : attribute.getModifiers()) {
            // Convert UUID to a ResourceLocation string for comparison
            if (modifier.id().toString().equals(uuid.toString())) {
                attribute.removeModifier(modifier);
                return;
            }
        }
    }

    public static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute == null) {
            return;
        }
        AttributeModifier modifier = attribute.getModifier(id);
        if (modifier != null) {
            attribute.removeModifier(modifier);
        }
    }
}
