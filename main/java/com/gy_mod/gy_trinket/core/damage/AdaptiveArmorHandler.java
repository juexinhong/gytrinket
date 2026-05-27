package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * 适应性装甲处理器
 * 优先级 100
 * 
 * <p>触发条件：
 * <ul>
 *   <li>玩家有适应性装甲物品</li>
 *   <li>玩家有护盾值（护盾值 > 0）</li>
 * </ul>
 * 
 * <p>护盾值为0时的行为：
 * <ul>
 *   <li>停止因受到攻击获得装甲叠层</li>
 *   <li>不会因装甲叠层的免伤数值修改伤害量</li>
 *   <li>已有的装甲叠层不会被清除，会正常过期</li>
 * </ul>
 */
public class AdaptiveArmorHandler implements DamageHandler {
    private static final int PRIORITY = 100;
    private final AdaptiveArmorManager armorManager;

    public AdaptiveArmorHandler() {
        this.armorManager = AdaptiveArmorManager.getInstance();
    }

    @Override
    public void handle(DamageContext context) {
        // 检查玩家是否启用适应性装甲
        if (!armorManager.hasAdaptiveArmor(context.getPlayer())) {
            return;
        }

        // 检查护盾值：护盾值为0时不处理（不添加装甲叠层，不修改伤害）
        double currentShield = ShieldManager.getCurrentShield(context.getPlayer().getUUID());
        if (currentShield <= 0) {
            return;
        }

        // 检测伤害源：跳过玩家自伤、玩家协议自伤
        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE) {
            return;
        }

        // 步骤 1：计算当前伤害减免，修改伤害值
        double reduction = armorManager.calculateDamageReduction(context.getPlayer());
        float currentDamage = context.getCurrentDamage();
        float reducedDamage = currentDamage * (1 - (float) reduction);
        context.setCurrentDamage(reducedDamage);

        // 步骤 2：添加新的装甲叠层（基于减伤后的伤害值 × 配置的转化系数）
        // addArmorLayers 会自动更新动态护盾效果属性（当玩家有护盾效果物品时）
        double layersToAdd = context.getCurrentDamage() * Config.getAdaptiveArmorLayersPerDamage();
        armorManager.addArmorLayers(context.getPlayer(), layersToAdd);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}