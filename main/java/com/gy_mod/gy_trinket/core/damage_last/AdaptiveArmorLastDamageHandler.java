package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

/**
 * 适应性装甲最终伤害处理器
 * 优先级 100
 * 
 * <p>与 damage 系统中的 AdaptiveArmorHandler 共用同一个管理器。
 * 在最终伤害阶段处理适应性装甲的伤害减免和叠层增加。
 * 
 * <p>检查条件：
 * <ul>
 *   <li>玩家有适应性装甲物品</li>
 *   <li>伤害源不是最终伤害或协议伤害</li>
 * </ul>
 * 
 * <p>注意：此处理器不需要检查护盾值。
 */
public class AdaptiveArmorLastDamageHandler implements LastDamageHandler {
    private static final int PRIORITY = 100;
    private final AdaptiveArmorManager armorManager;

    public AdaptiveArmorLastDamageHandler() {
        this.armorManager = AdaptiveArmorManager.getInstance();
    }

    @Override
    public void handle(LastDamageContext context) {
        // 检查是否是玩家
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }

        // 检查玩家是否启用适应性装甲
        if (!armorManager.hasAdaptiveArmor(player)) {
            return;
        }

        // 检测伤害源：跳过最终伤害和协议伤害
        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.FINAL_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE) {
            return;
        }

        // 步骤 1：计算当前伤害减免，修改伤害值
        double reduction = armorManager.calculateDamageReduction(player);
        float currentDamage = context.getCurrentDamage();
        float reducedDamage = currentDamage * (1 - (float) reduction);
        context.setCurrentDamage(reducedDamage);

        // 步骤 2：添加新的装甲叠层（基于减伤后的伤害值 × 配置的转化系数）
        // addArmorLayers 会自动更新动态护盾效果属性（当玩家有护盾效果物品时）
        double layersToAdd = context.getCurrentDamage() * Config.getAdaptiveArmorLayersPerDamage();
        armorManager.addArmorLayers(player, layersToAdd);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}