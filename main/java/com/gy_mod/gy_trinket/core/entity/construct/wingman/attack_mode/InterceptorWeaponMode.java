package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 拦截机武器模式接口
 * <p>
 * 定义武器如何攻击。武器模式关注的是"用什么方式攻击"：
 * 近战模式使用武器近身攻击，弓箭模式使用弓/弩射箭。
 * <p>
 * 武器模式只负责执行一次攻击和计算基础冷却，
 * 不负责攻击频率、连发、充能等逻辑（这些由模块模式处理）。
 * <p>
 * executeAttack 不会设置 weaponAttackCooldown，由调用方管理冷却。
 */
public interface InterceptorWeaponMode {

    /**
     * 执行一次武器攻击
     * <p>
     * 注意：此方法不设置 weaponAttackCooldown，由调用方（模块模式管理器）负责冷却管理。
     *
     * @param attacker 拦截机实体
     * @param target   攻击目标
     * @param weapon   使用的武器
     * @param owner    所属玩家
     */
    void executeAttack(WingmanConstructEntity attacker, LivingEntity target, ItemStack weapon, Player owner);

    /**
     * 计算一次武器攻击的基础冷却时间（tick）
     *
     * @param attacker 拦截机实体
     * @param weapon   使用的武器
     * @param owner    所属玩家
     * @return 基础冷却tick数
     */
    int calculateCooldown(WingmanConstructEntity attacker, ItemStack weapon, Player owner);

    /**
     * 获取追击理想距离参数
     *
     * @param attacker 拦截机实体
     * @return [minDist, maxDist, farDist]
     */
    double[] getIdealDistanceRange(WingmanConstructEntity attacker);

    /**
     * 判断给定物品是否为该武器模式适用的武器
     */
    boolean isWeapon(ItemStack stack);

    /**
     * 获取序列化名称
     */
    String getSerializedName();

    /**
     * 获取翻译键
     */
    default String getTranslationKey() {
        return "screen.gytrinket.interceptor_attack_mode_" + getSerializedName();
    }
}
