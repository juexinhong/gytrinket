package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 拦截机点射模式
 * <p>
 * 行为：
 * - 拦截机攻击时触发点射
 * - 快速攻击连击(combo)属性次，每次间隔1刻
 * - 连发结束后进入总冷却 = 连击段数 × 武器攻击间隔
 * - 连发期间和冷却期间禁止武器攻击
 * <p>
 * 连发攻击通过武器模式执行（近战/弓箭），由管理器统一调度。
 */
public class InterceptorBurstHandler implements InterceptorAttackModeHandler {

    public static final String NAME = "burst";

    private static final String BURST_REMAINING_KEY = "InterceptorBurstRemaining";
    private static final String BURST_COOLDOWN_KEY = "InterceptorBurstCooldown";
    private static final String BURST_COMBO_KEY = "InterceptorBurstCombo";
    /** 连击间隔：1刻 */
    private static final int BURST_INTERVAL_TICKS = 1;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void tick(WingmanConstructEntity wingman, Player owner) {
        int remaining = wingman.getPersistentData().getInt(BURST_REMAINING_KEY);
        if (remaining <= 0) return;

        // 处理连发间隔
        int burstCooldown = wingman.getPersistentData().getInt(BURST_COOLDOWN_KEY);
        if (burstCooldown > 0) {
            wingman.getPersistentData().putInt(BURST_COOLDOWN_KEY, burstCooldown - 1);
            // 连发期间保持武器冷却，防止普通武器攻击触发
            wingman.setWeaponAttackCooldown(BURST_INTERVAL_TICKS + 1);
            // cooldown递减到0时立即执行攻击（1tick间隔），不等待下一tick
            if (burstCooldown > 1) {
                return;
            }
            // fall through: cooldown已到0，执行连发攻击
        }

        // 使用 lastTrackedTarget 而非 getTarget()，因为僚机不使用原版 setTarget()
        LivingEntity target = wingman.getLastTrackedTarget();
        if (target != null && target.isAlive()) {
            InterceptorDebug.logStateChange(wingman, "点射连发攻击: remaining=" + remaining + " target=" + target.getName().getString());
            executeBurstAttack(wingman, target, owner);
        } else {
            InterceptorDebug.logStateChange(wingman, "点射连发中断: 目标丢失 remaining=" + remaining);
            finishBurst(wingman, owner);
        }
    }

    @Override
    public boolean onPreAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner) {
        // 连发进行中，不允许新的攻击触发
        if (wingman.getPersistentData().getInt(BURST_REMAINING_KEY) > 0) {
            InterceptorDebug.logFast(wingman, "burst_active_" + wingman.getId(),
                    "点射连发中: remaining=" + wingman.getPersistentData().getInt(BURST_REMAINING_KEY));
            return false;
        }

        // 首次攻击触发点射
        int comboBonus = getComboBonus(owner);
        if (comboBonus <= 0) {
            return true; // 无连击属性，普通攻击
        }

        // 标记连发次数（首次攻击由WingmanConstructEntity通过武器模式执行，剩余 = combo次）
        wingman.getPersistentData().putInt(BURST_REMAINING_KEY, comboBonus);
        wingman.getPersistentData().putInt(BURST_COOLDOWN_KEY, BURST_INTERVAL_TICKS);
        wingman.getPersistentData().putInt(BURST_COMBO_KEY, 1 + comboBonus);

        // 连击锁定：连发期间临时提升reach+3
        wingman.setBurstLock(true);

        InterceptorDebug.logStateChange(wingman, "点射触发: combo=" + comboBonus
                + " totalShots=" + (1 + comboBonus) + " 属性连击值=" + AttributeManager.getPlayerAttribute(owner.getUUID(), "combo"));

        return true;
    }

    @Override
    public void onPostAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner) {
        // 首次攻击后，通知强袭
        if (wingman.getPersistentData().getInt(BURST_COMBO_KEY) > 0) {
            InterceptorAttackModeManager.onBurstAutoAttack(wingman, target, owner);
        }
    }

    @Override
    public int modifyCooldown(int baseCooldown, WingmanConstructEntity wingman, Player owner) {
        if (wingman.getPersistentData().getInt(BURST_REMAINING_KEY) > 0) {
            return BURST_INTERVAL_TICKS + 1;
        }
        return baseCooldown;
    }

    @Override
    public float modifyDamage(float baseDamage, WingmanConstructEntity wingman, Player owner) {
        return baseDamage;
    }

    @Override
    public void onTargetChanged(WingmanConstructEntity wingman, LivingEntity oldTarget, LivingEntity newTarget) {
        if (newTarget == null || !newTarget.isAlive()) {
            if (wingman.getPersistentData().getInt(BURST_REMAINING_KEY) > 0) {
                if (wingman.getOwner() instanceof Player owner) {
                    finishBurst(wingman, owner);
                }
            }
        }
    }

    @Override
    public void clearState(WingmanConstructEntity wingman) {
        wingman.getPersistentData().remove(BURST_REMAINING_KEY);
        wingman.getPersistentData().remove(BURST_COOLDOWN_KEY);
        wingman.getPersistentData().remove(BURST_COMBO_KEY);
        wingman.setBurstLock(false);
    }

    // ===== 辅助方法 =====

    private static int getComboBonus(Player owner) {
        double combo = AttributeManager.getPlayerAttribute(owner.getUUID(), "combo");
        return (int) Math.floor(combo);
    }

    /**
     * 执行一次连发攻击（通过武器模式）
     */
    private void executeBurstAttack(WingmanConstructEntity wingman, LivingEntity target, Player owner) {
        ItemStack weapon = wingman.getInterceptorWeapon();
        if (weapon.isEmpty()) {
            weapon = owner.getMainHandItem();
        }

        // 通过管理器调用武器模式执行攻击
        InterceptorAttackModeManager.executeWeaponAttack(wingman, target, weapon, owner);

        // 通知组合调度：连发自动攻击命中
        InterceptorAttackModeManager.onBurstAutoAttack(wingman, target, owner);

        // 减少剩余连击
        int remaining = wingman.getPersistentData().getInt(BURST_REMAINING_KEY) - 1;
        if (remaining > 0 && target.isAlive()) {
            wingman.getPersistentData().putInt(BURST_REMAINING_KEY, remaining);
            wingman.getPersistentData().putInt(BURST_COOLDOWN_KEY, BURST_INTERVAL_TICKS);
            wingman.setWeaponAttackCooldown(BURST_INTERVAL_TICKS + 1);
        } else {
            finishBurst(wingman, owner);
        }
    }

    /**
     * 由充能释放后触发点射
     */
    public static void startBurstFromCharged(WingmanConstructEntity wingman, LivingEntity target, Player owner) {
        if (isBursting(wingman)) return;

        int comboBonus = getComboBonus(owner);
        if (comboBonus <= 0) return;

        wingman.getPersistentData().putInt(BURST_REMAINING_KEY, comboBonus);
        wingman.getPersistentData().putInt(BURST_COOLDOWN_KEY, BURST_INTERVAL_TICKS);
        wingman.getPersistentData().putInt(BURST_COMBO_KEY, 1 + comboBonus);
        wingman.setBurstLock(true);
    }

    /**
     * 结束连发并设置总冷却。
     * 总冷却 = 连击段数 × 武器攻击间隔。
     */
    private void finishBurst(WingmanConstructEntity wingman, Player owner) {
        int totalCombo = wingman.getPersistentData().getInt(BURST_COMBO_KEY);
        if (totalCombo <= 0) totalCombo = 1;

        int baseWeaponCooldown = InterceptorAttackModeManager.calculateWeaponCooldown(
                wingman, wingman.getInterceptorWeapon(), owner);

        int totalCooldown = Math.max(2, baseWeaponCooldown * totalCombo);
        wingman.setWeaponAttackCooldown(totalCooldown);

        // 清除连击锁定
        wingman.setBurstLock(false);

        InterceptorDebug.logStateChange(wingman, "点射连发结束: totalCombo=" + totalCombo
                + " baseWeaponCd=" + baseWeaponCooldown + " totalCd=" + totalCooldown);

        wingman.getPersistentData().remove(BURST_REMAINING_KEY);
        wingman.getPersistentData().remove(BURST_COOLDOWN_KEY);
        wingman.getPersistentData().remove(BURST_COMBO_KEY);
    }

    public static boolean isBursting(WingmanConstructEntity wingman) {
        return wingman.getPersistentData().getInt(BURST_REMAINING_KEY) > 0;
    }
}

