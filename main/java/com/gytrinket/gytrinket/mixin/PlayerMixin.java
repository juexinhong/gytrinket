package com.gytrinket.gytrinket.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Player Mixin
 * <p>
 * 充能横扫已改用 ChargedAttackSweepHandler.executeChargedSweepAttack 在服务端直接执行，
 * 不再使用Mixin注入原版attack/sweepAttack方法。
 * 保留此类以备未来需要Player级别的Mixin时使用。
 */
@Mixin(Player.class)
public class PlayerMixin {
}
