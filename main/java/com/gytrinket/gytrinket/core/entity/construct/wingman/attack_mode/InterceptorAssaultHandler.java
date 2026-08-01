package com.gytrinket.gytrinket.core.entity.construct.wingman.attack_mode;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 拦截机强袭模式
 * <p>
 * 行为：
 * - 拥有攻击目标时进入强袭状态
 * - 更换目标时重置强袭状态
 * - 目标死亡/丢失时退出强袭状态
 * - 每次武器攻击叠加1层强袭
 * - 每层提供10%攻击速度（减少冷却时间）
 * - 每次攻击受到层数对应的自伤
 */
public class InterceptorAssaultHandler implements InterceptorAttackModeHandler {

    public static final String NAME = "assault";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void tick(WingmanConstructEntity wingman, Player owner) {
        // 不在此处检查目标有效性，由 onTargetChanged 回调统一处理
        // （僚机不使用原版 setTarget()，wingman.getTarget() 可能返回 null）
    }

    @Override
    public boolean onPreAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner) {
        // 强袭不阻止攻击
        return true;
    }

    @Override
    public void onPostAttack(WingmanConstructEntity wingman, LivingEntity target, ItemStack weapon, Player owner) {
        triggerAssaultStack(wingman);
    }

    /**
     * 叠加强袭层数并自伤。
     * 由管理器在跨模式触发时调用（如连发自动攻击触发强袭、充能期间触发强袭）。
     */
    public static void triggerAssaultStack(WingmanConstructEntity wingman) {
        int stacks = getAssaultStacks(wingman) + 1;
        setAssaultStacks(wingman, stacks);

        InterceptorDebug.logAttackResult(wingman, "强袭叠层: stacks=" + stacks
                + " selfDmg=" + String.format("%.1f", Config.getAssaultSelfDamagePerStack() * stacks));

        // 自伤
        float selfDamage = (float) (Config.getAssaultSelfDamagePerStack() * stacks);
        if (selfDamage > 0) {
            wingman.hurt(ModDamageTypes.getPlayerSelfDamageSource(wingman.level()), selfDamage);
        }
    }

    @Override
    public int modifyCooldown(int baseCooldown, WingmanConstructEntity wingman, Player owner) {
        int stacks = getAssaultStacks(wingman);
        if (stacks <= 0) return baseCooldown;

        // 每层提供10%攻击速度（减少冷却）
        double attackSpeedBonus = Config.getAssaultAttackSpeedPerStack() * stacks;
        // 攻击速度加成是独立乘区，冷却 = 基础冷却 / (1 + 加成)
        double modifiedCooldown = baseCooldown / (1.0 + attackSpeedBonus);
        return Math.max(2, (int) modifiedCooldown);
    }

    @Override
    public float modifyDamage(float baseDamage, WingmanConstructEntity wingman, Player owner) {
        // 强袭不影响伤害
        return baseDamage;
    }

    @Override
    public void onTargetChanged(WingmanConstructEntity wingman, LivingEntity oldTarget, LivingEntity newTarget) {
        if (newTarget == null) {
            InterceptorDebug.logStateChange(wingman, "强袭: 目标丢失，退出强袭 stacks=" + getAssaultStacks(wingman));
            resetAssault(wingman);
        } else if (oldTarget != null && oldTarget != newTarget) {
            InterceptorDebug.logStateChange(wingman, "强袭: 更换目标，重置层数 stacks=" + getAssaultStacks(wingman));
            resetAssault(wingman);
        }
    }

    @Override
    public void clearState(WingmanConstructEntity wingman) {
        resetAssault(wingman);
    }

    // ===== 强袭层数管理 =====

    private static final String ASSAULT_STACKS_KEY = "InterceptorAssaultStacks";

    public static int getAssaultStacks(WingmanConstructEntity wingman) {
        return wingman.getPersistentData().getInt(ASSAULT_STACKS_KEY);
    }

    public static void setAssaultStacks(WingmanConstructEntity wingman, int stacks) {
        wingman.getPersistentData().putInt(ASSAULT_STACKS_KEY, stacks);
    }

    private void resetAssault(WingmanConstructEntity wingman) {
        wingman.getPersistentData().remove(ASSAULT_STACKS_KEY);
    }
}
