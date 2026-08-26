package com.gy_mod.gy_trinket.core.modifier;

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
            // 1.20.1: AttributeModifier 使用名称（"gytrinket:xxx"）标识归属，等价于 1.21.1 的 id().getNamespace()
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
        AttributeModifier modifier = attribute.getModifier(uuid);
        if (modifier != null) {
            attribute.removeModifier(modifier);
        }
    }

    public static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute == null) {
            return;
        }
        // 1.20.1: AttributeModifier 无 ResourceLocation ID，按名称匹配（构造时 name = id.toString()）
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getName().equals(id.toString())) {
                attribute.removeModifier(modifier);
                return;
            }
        }
    }
}
