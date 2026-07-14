package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 充能攻击伤害处理
 * <p>
 * 1. 充能期间的攻击禁用由客户端 InteractionKeyMappingTriggered 从根源上阻止
 * 2. 充能释放后，每次攻击都可以消耗当前充能值获得伤害加成
 * 3. 充能值随tick快速消退，自然处理衰减
 * 4. 首次释放攻击全额生效（充能值尚未消退）
 * <p>
 * 注意：剑类物品的充能横扫已由 ChargedAttackSweepHandler.executeChargedSweepAttack 处理，
 * 此处仅处理非剑类物品的充能加成。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackDamageHandler {

    private ChargedAttackDamageHandler() {}

    /**
     * 处理充能攻击的伤害加成
     * 释放后每次攻击都可以消耗当前充能值
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return;
        }

        double chargeValue = ChargedAttackDamageTracker.getChargeValue(playerUUID);

        if (chargeValue <= 0) {
            return;
        }

        LivingEntity target = event.getEntity();

        // 充能值乘算加成：最终伤害 = 原始伤害 * (1 + 充能值)
        float newDamage = event.getAmount() * (1.0F + (float) chargeValue);

        // 注意：剑类的充能横扫已由executeChargedSweepAttack处理，此处仅处理非剑类充能加成
        event.setAmount(newDamage);
    }
}
