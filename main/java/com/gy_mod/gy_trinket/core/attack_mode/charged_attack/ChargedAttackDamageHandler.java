package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * 5. 横扫伤害额外受充能值加成（每点+10%，最高100%）
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

        // 获取当前充能值（不移除，由tick消退处理）
        double chargeValue = ChargedAttackDamageTracker.getChargeValue(playerUUID);

        if (chargeValue <= 0) {
            return;
        }

        // 移除目标无敌时间
        LivingEntity target = event.getEntity();
        target.invulnerableTime = 0;

        // 记录被横扫命中的实体（用于补伤判断）
        ChargedAttackSweepHandler.recordSweptEntity(playerUUID, target.getId());

        // 充能值乘算加成：最终伤害 = 原始伤害 * (1 + 充能值)
        float newDamage = event.getAmount() * (1.0F + (float) chargeValue);

        // 横扫伤害额外加成：剑类物品对横扫目标（非主要攻击目标）额外提升横扫伤害
        ItemStack mainHandItem = player.getMainHandItem();
        if (ChargedAttackSweepHandler.isSwordItem(mainHandItem)
                && ChargedAttackSweepHandler.isSweepTarget(playerUUID, target)) {
            float sweepMultiplier = ChargedAttackSweepHandler.getSweepDamageMultiplier(chargeValue);
            newDamage *= sweepMultiplier;
        }

        event.setAmount(newDamage);
    }
}
