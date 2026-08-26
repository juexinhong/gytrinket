package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface InterceptorWeaponMode {
    void executeAttack(WingmanConstructEntity attacker, LivingEntity target, ItemStack weapon, Player owner);

    int calculateCooldown(WingmanConstructEntity attacker, ItemStack weapon, Player owner);

    double[] getIdealDistanceRange(WingmanConstructEntity attacker);

    boolean isWeapon(ItemStack stack);

    String getSerializedName();
}

