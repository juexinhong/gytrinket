package com.gy_mod.gy_trinket.core.attack_mode.burst_fire;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.player.attack.AttackSpeedManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 点射共享工具：近战点射与弹射物点射共用的连击段数读取、有效攻速捕获与冷却公式
 * <p>
 * 冷却公式（单一公式，无分档）：点射预支未来 comboStacksBonus 次攻击换取爆发效果
 * （首发不计——其已承受自身原本的冷却），冷却等价于这些未来攻击的总耗时 =
 * 连击段数 × 攻击间隔，整体一次取整。
 * <p>
 * 高攻速下（攻击间隔 &lt; 1 刻）冷却可短于复制循环时长（循环每段间隔 1 刻、必超 1 刻），
 * 冷却先于循环结束到期、玩家再次攻击即叠加新循环——循环叠加是公式的自然结果而非分支逻辑
 */
final class BurstFireSupport {

    private BurstFireSupport() {}

    /**
     * 获取玩家的连击段数加成
     */
    static int getComboStacksBonus(Player player) {
        double combo = AttributeManager.getPlayerAttribute(player.getUUID(), "combo");
        return (int) Math.floor(combo);
    }

    /**
     * 借用属性系统捕获当前有效攻速（修正值施加方式与右键充能一致）：
     * 1. 主手物品为武器 → 不施加（武器自带攻速已在属性中生效）
     * 2. 主手物品命中充能物品白名单 → 临时施加白名单攻速修正值
     * 3. 其余（含空手）→ 临时施加默认攻速修正值
     * 读取属性最终攻速（自动叠加急迫等玩家身上所有攻速修饰符）后立即移除临时修饰符
     * <p>
     * 临时修正值与充能攻速减益（{@link AttackSpeedManager} 投影的 attack_speed_flat）
     * 共用同一修饰符：充能减益已在身上时（瞬发物品扔出的瞬间充能减益未解除）
     * 同种减益无法重复施加——不施加也不移除，借用现有减益直接读数，
     * 避免双重减益把攻速压到 0 以下、点射冷却被钳制到连击段数×10秒
     */
    static double captureEffectiveAttackSpeed(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        // 非武器（含空手）按充能逻辑取修正值：白名单命中取白名单值，未命中自动返回默认值
        double speedModifier = (mainHand.isEmpty() || !Config.isWeaponLikeItem(mainHand.getItem()))
                ? Config.getItemUseChargeSpeedModifier(mainHand.getItem())
                : 0;

        AttributeInstance attackSpeedAttribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speedModifier == 0 || attackSpeedAttribute == null) {
            return player.getAttributeValue(Attributes.ATTACK_SPEED);
        }

        // 充能减益已在身上：借用现有减益直接读数（不移除，避免拆掉充能状态的攻速减益）
        if (attackSpeedAttribute.getModifier(AttackSpeedManager.FLAT_MODIFIER_UUID) != null) {
            return player.getAttributeValue(Attributes.ATTACK_SPEED);
        }

        // 临时施加修正值，读取最终攻速后立即移除（transient ADDITION，与右键充能同一施加方式）
        attackSpeedAttribute.addTransientModifier(new AttributeModifier(
                AttackSpeedManager.FLAT_MODIFIER_UUID, AttackSpeedManager.FLAT_MODIFIER_NAME,
                speedModifier, AttributeModifier.Operation.ADDITION));
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        attackSpeedAttribute.removeModifier(AttackSpeedManager.FLAT_MODIFIER_UUID);
        return attackSpeed;
    }

    /**
     * 按有效攻速计算点射冷却刻数：冷却 = 连击段数 × 攻击间隔（整体一次取整）
     */
    static int calcBurstCooldownTicks(int comboStacksBonus, double attackSpeed) {
        return (int) Math.ceil(comboStacksBonus * 20.0 / Math.max(attackSpeed, 0.1));
    }
}
