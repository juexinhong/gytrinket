package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 拦截机攻击模式处理器接口
 * <p>
 * 每种攻击模式（强袭、点射、充能攻击）独立实现此接口。
 * 拦截机的攻击模式是自主实现的，不复用玩家攻击模式的底层逻辑。
 */
public interface InterceptorAttackModeHandler {

    /**
     * 获取模式名称（用于序列化和显示）
     */
    String getName();

    /**
     * 每tick更新（在WingmanConstructEntity.tick中调用）
     *
     * @param wingman 拦截机实体
     * @param owner   所属玩家
     */
    void tick(WingmanConstructEntity wingman, Player owner);

    /**
     * 拦截机武器攻击执行前调用。
     * 返回true允许攻击执行，返回false取消攻击（如充能中）。
     *
     * @param wingman 拦截机实体
     * @param target  攻击目标
     * @param weapon  使用的武器
     * @param owner   所属玩家
     * @return 是否允许攻击执行
     */
    boolean onPreAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner);

    /**
     * 拦截机武器攻击执行后调用。
     *
     * @param wingman 拦截机实体
     * @param target  攻击目标
     * @param weapon  使用的武器
     * @param owner   所属玩家
     */
    void onPostAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner);

    /**
     * 修改攻击冷却时间。
     * 基础冷却已由策略计算好，攻击模式可在此基础上修改。
     *
     * @param baseCooldown 策略计算的基础冷却
     * @param wingman      拦截机实体
     * @param owner        所属玩家
     * @return 修改后的冷却tick数
     */
    int modifyCooldown(int baseCooldown, WingmanConstructEntity wingman, Player owner);

    /**
     * 修改攻击伤害倍率。
     *
     * @param baseDamage 基础伤害
     * @param wingman   拦截机实体
     * @param owner     所属玩家
     * @return 修改后的伤害
     */
    float modifyDamage(float baseDamage, WingmanConstructEntity wingman, Player owner);

    /**
     * 目标变更时调用。
     *
     * @param wingman   拦截机实体
     * @param oldTarget 旧目标（可能为null）
     * @param newTarget 新目标（可能为null）
     */
    void onTargetChanged(WingmanConstructEntity wingman, LivingEntity oldTarget, LivingEntity newTarget);

    /**
     * 清除所有状态（登出/死亡等时调用）
     */
    void clearState(WingmanConstructEntity wingman);
}
