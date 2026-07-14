package com.gy_mod.gy_trinket.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Player Mixin
 * <p>
 * 充能横扫已改用事件检查方式实现，不再使用Mixin注入。
 * 保留此类以备未来需要Player级别的Mixin时使用。
 */
@Mixin(Player.class)
public class PlayerMixin {
}
