package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.world.entity.player.Player;

/**
 * 构造体工厂接口，用于创建构造体逻辑实例。
 * <p>
 * 各构造体类型注册时提供此工厂的实现，
 * 由 {@link ConstructBuilder} 在构建完成时统一调用。
 */
@FunctionalInterface
public interface IConstructFactory {
    /**
     * 创建构造体逻辑实例
     *
     * @param player 所属玩家
     * @param type   构造体类型
     * @return 新创建的构造体实例
     */
    IConstruct create(Player player, ConstructType type);
}
