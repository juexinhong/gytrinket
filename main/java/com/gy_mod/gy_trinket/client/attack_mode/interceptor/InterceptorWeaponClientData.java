package com.gy_mod.gy_trinket.client.attack_mode.interceptor;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.UUID;

public class InterceptorWeaponClientData {
    private static final HashMap<UUID, ItemStack> weapons = new HashMap<>();
    private static final HashMap<UUID, InterceptorAttackMode> attackModes = new HashMap<>();

    public static void setWeapon(UUID playerUUID, ItemStack weapon) {
        weapons.put(playerUUID, weapon);
    }

    public static ItemStack getWeapon(UUID playerUUID) {
        return weapons.getOrDefault(playerUUID, ItemStack.EMPTY);
    }

    public static void setAttackMode(UUID playerUUID, InterceptorAttackMode mode) {
        attackModes.put(playerUUID, mode);
    }

    public static InterceptorAttackMode getAttackMode(UUID playerUUID) {
        return attackModes.getOrDefault(playerUUID, InterceptorAttackMode.MELEE);
    }

    public static void clear(UUID playerUUID) {
        weapons.remove(playerUUID);
        attackModes.remove(playerUUID);
    }
}

