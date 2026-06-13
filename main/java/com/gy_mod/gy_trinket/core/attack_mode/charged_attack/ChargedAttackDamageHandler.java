package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;

/**
 * 充能攻击伤害处理
 * <p>
 * 1. 充能期间的攻击禁用由客户端 InteractionKeyMappingTriggered 从根源上阻止
 * 2. 充能释放攻击通过 AttackEntityEvent 精确识别（chargeValue > 0）
 * 3. 在 LivingHurtEvent 中增强伤害
 * 4. 只影响这一次攻击，后续连击不受充能影响
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ChargedAttackDamageHandler {

    // 记录当前tick内通过 AttackEntityEvent 确认的充能攻击释放
    private static final Set<UUID> PENDING_MELEE_RELEASE = new java.util.concurrent.CopyOnWriteArraySet<>();

    private ChargedAttackDamageHandler() {}

    /**
     * 充能释放攻击标记
     * <p>
     * 客户端 InteractionKeyMappingTriggered 已从根源上阻止充能期间的攻击输入，
     * 此处仅标记充能释放攻击（chargeValue > 0）用于伤害增强。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.isCanceled()) {
            return;
        }

        Player player = event.getEntity();
        UUID playerUUID = player.getUUID();

        if (!ChargedAttackManager.hasChargedAttack(player)) {
            return;
        }

        // 检查是否有待释放的充能值（充能释放攻击）
        double chargeValue = ChargedAttackDamageTracker.getChargeValue(playerUUID);
        if (chargeValue > 0) {
            // 标记为充能攻击释放
            PENDING_MELEE_RELEASE.add(playerUUID);
        }
    }

    /**
     * 处理充能攻击的伤害加成
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 只处理经过 AttackEntityEvent 确认的充能攻击释放
        if (!PENDING_MELEE_RELEASE.contains(playerUUID)) {
            return;
        }

        // 消费充能值（一次性）
        double chargeValue = ChargedAttackDamageTracker.consumeChargeValue(playerUUID);

        // 清除标记
        PENDING_MELEE_RELEASE.remove(playerUUID);

        if (chargeValue <= 0) {
            return;
        }

        // 移除目标无敌时间
        LivingEntity target = event.getEntity();
        target.invulnerableTime = 0;

        // 添加充能值到伤害
        float newDamage = event.getAmount() + (float) chargeValue;
        event.setAmount(newDamage);
    }

    /**
     * 每tick末清除过期的标记
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PENDING_MELEE_RELEASE.clear();
        }
    }
}
