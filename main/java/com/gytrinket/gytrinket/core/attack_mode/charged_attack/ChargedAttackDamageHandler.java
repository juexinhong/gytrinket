package com.gytrinket.gytrinket.core.attack_mode.charged_attack;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.UUID;

/**
 * 充能攻击伤害处理
 * <p>
 * 1. 充能期间的攻击禁用由客户端 Mixin 从根源上阻止
 * 2. 充能释放后，每次攻击都可以消耗当前充能值获得伤害加成
 * 3. 充能值随tick快速消退，自然处理衰减
 * 4. 首次释放攻击全额生效（充能值尚未消退）
 * <p>
 * 注意：剑类物品的充能横扫已由 ChargedAttackSweepHandler.executeChargedSweepAttack 处理，
 * 此处仅处理非剑类物品的充能加成。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackDamageHandler {

    private ChargedAttackDamageHandler() {}

    /**
     * 处理充能攻击的伤害加成
     * 释放后每次攻击都可以消耗当前充能值
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        // 剑类的充能伤害由 executeChargedSweepAttack 全额处理，此处跳过以避免双重乘算
        if (ChargedAttackSweepHandler.isSwordItem(player.getMainHandItem())) {
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

        // 充能值乘算加成：最终伤害 = 原始伤害 * (1 + 充能值)
        // 注意：剑类的充能横扫已由executeChargedSweepAttack处理，此处仅处理非剑类充能加成；
        // 非剑类释放走原版攻击，原版自带成功命中扣1点耐久
        event.setNewDamage(event.getNewDamage() * (1.0F + (float) chargeValue));
    }
}
