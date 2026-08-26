package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gy_mod.gy_trinket.core.modifier.player.attack.AttackSpeedManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * 弓箭武器模式
 * <p>
 * 使用弓/弩发射箭矢攻击目标。
 * 附魔（力量/火矢/无限等）由 vanilla 的 ProjectileUtil.getMobArrow 自动处理。
 * executeAttack 不设置 weaponAttackCooldown，由模块模式管理器负责冷却。
 */
public class BowWeaponMode implements InterceptorWeaponMode {

    @Override
    public void executeAttack(WingmanConstructEntity attacker, LivingEntity target, ItemStack weapon, Player owner) {
        if (!isWeapon(weapon)) {
            InterceptorDebug.logSlow(attacker, "bow_not_weapon_" + attacker.getId(),
                    "非弓箭武器: " + weapon.getItem());
            return;
        }

        if (!(attacker.level() instanceof ServerLevel serverLevel)) return;

        // 从拦截机弹药槽获取弹药，空则用默认箭矢
        ItemStack ammoStack = InterceptorWeaponManager.getAmmo(owner.getUUID());
        if (ammoStack.isEmpty()) {
            ammoStack = new ItemStack(Items.ARROW);
        }

        InterceptorDebug.logAttackStep(attacker, "bow_exec", "弓箭攻击: weapon=" + weapon.getItem()
                + " ammo=" + ammoStack.getItem() + " dist=" + String.format("%.1f", attacker.distanceTo(target)));

        // 用 vanilla 的 ProjectileUtil.getMobArrow 创建箭矢，自动应用力量/火矢等附魔
        float chargeFactor = 1.0F;
        AbstractArrow arrow = ProjectileUtil.getMobArrow(attacker, ammoStack, chargeFactor);

        // 弓自定义箭矢（支持药箭等特殊箭矢）
        if (weapon.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow);
        }

        arrow.setOwner(attacker);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

        // 计算弹道方向
        double x = target.getX() - attacker.getX();
        double y = target.getEyeY() - attacker.getEyeY();
        double z = target.getZ() - attacker.getZ();
        float distance = attacker.distanceTo(target);
        if (distance < 0.1f) {
            x = attacker.getLookAngle().x;
            y = attacker.getLookAngle().y;
            z = attacker.getLookAngle().z;
            distance = 1.0f;
        }
        // 根据距离动态调整速度和精度（与女仆模组一致）
        float velocity = Mth.clamp(distance / 10f, 1.6f, 3.2f);
        float inaccuracy = 1 - Mth.clamp(distance / 100f, 0, 0.9f);
        arrow.shoot(x, y, z, velocity, inaccuracy);

        // 拦截机不消耗武器耐久和弹药

        attacker.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);
        serverLevel.addFreshEntity(arrow);
    }

    /**
     * 获取附魔等级（1.20.1: EnchantmentHelper.getItemEnchantmentLevel）
     */
    private int getEnchantmentLevel(WingmanConstructEntity attacker, ItemStack stack,
                                     net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    @Override
    public int calculateCooldown(WingmanConstructEntity attacker, ItemStack weapon, Player owner) {
        int quickChargeLevel = getEnchantmentLevel(attacker, weapon, Enchantments.QUICK_CHARGE);
        int drawTicks = Math.max(1, 20 - quickChargeLevel * 5);
        int cooldownTicks = drawTicks * 4;
        double modMultiplier = AttackSpeedManager.getInterceptorAttackSpeedMultiplier(owner.getUUID());
        cooldownTicks = (int) (cooldownTicks / modMultiplier);
        cooldownTicks = (int) (cooldownTicks / attacker.getAttackSpeedMultiplier());
        cooldownTicks = (int) (cooldownTicks / attacker.getWeaponAttackSpeedMultiplier());
        return Math.max(2, cooldownTicks);
    }

    @Override
    public double[] getIdealDistanceRange(WingmanConstructEntity attacker) {
        return new double[]{6.0, 7.0, 8.0};
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    @Override
    public String getSerializedName() {
        return "bow";
    }
}

