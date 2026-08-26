package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface InterceptorAttackModeHandler {
    String getName();

    void tick(WingmanConstructEntity wingman, Player owner);

    boolean onPreAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner);

    void onPostAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner);

    int modifyCooldown(int baseCooldown, WingmanConstructEntity wingman, Player owner);

    float modifyDamage(float baseDamage, WingmanConstructEntity wingman, Player owner);

    void onTargetChanged(WingmanConstructEntity wingman, LivingEntity oldTarget, LivingEntity newTarget);

    void clearState(WingmanConstructEntity wingman);
}

