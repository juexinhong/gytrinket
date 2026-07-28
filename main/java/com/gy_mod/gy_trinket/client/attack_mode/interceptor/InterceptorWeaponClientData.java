package com.gy_mod.gy_trinket.client.attack_mode.interceptor;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InterceptorWeaponClientData {
    private static final Map<UUID, ItemStack> WEAPON = new ConcurrentHashMap<>();
    private static final Map<UUID, InterceptorAttackMode> ATTACK_MODE = new ConcurrentHashMap<>();

    public static void setWeapon(UUID playerUUID, ItemStack weapon) {
        WEAPON.put(playerUUID, weapon.copy());
    }

    public static ItemStack getWeapon(UUID playerUUID) {
        return WEAPON.getOrDefault(playerUUID, ItemStack.EMPTY);
    }

    public static void setAttackMode(UUID playerUUID, InterceptorAttackMode attackMode) {
        ATTACK_MODE.put(playerUUID, attackMode);
    }

    public static InterceptorAttackMode getAttackMode(UUID playerUUID) {
        return ATTACK_MODE.getOrDefault(playerUUID, InterceptorAttackMode.MELEE);
    }

    public static void clearAll() {
        WEAPON.clear();
        ATTACK_MODE.clear();
    }
}
