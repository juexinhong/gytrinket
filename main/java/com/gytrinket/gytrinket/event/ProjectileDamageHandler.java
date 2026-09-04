package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/**
 * 弹射物伤害同步处理器
 * <p>
 * 玩家伤害属性同步提升弹射物伤害：
 * 当 AbstractArrow（弓箭/弩箭/三叉戟等）加入世界时，按与近战伤害完全一致的方式应用玩家伤害属性组。
 * <p>
 * 与 AttackDamageManager 应用到原版 ATTACK_DAMAGE 的逻辑一致：
 * <ul>
 *   <li>attack_damage（BASE底数）：加算到 baseDamage</li>
 *   <li>attack_damage_percent（百分比）：作为乘区</li>
 *   <li>attack_damage_independent（独立乘区）：作为乘区</li>
 *   <li>总乘区 = attack_damage_percent × attack_damage_independent</li>
 *   <li>新 baseDamage = (原版 baseDamage + attackDamageBase) × 总乘区</li>
 * </ul>
 * <p>
 * 对比近战伤害（原版玩家 ATTACK_DAMAGE base=1.0）：
 *   近战 = (1.0 + base) × (percent × independent)
 *   弹射物 = (baseDamage + base) × (percent × independent)
 * 两者加成逻辑完全一致，仅基础值不同。
 * <p>
 * 示例：base=9, percent=1.5, independent=1.3, 弓箭原版baseDamage=2.0
 *   近战 = (1.0 + 9) × 1.95 = 19.5
 *   弹射物 = (2.0 + 9) × 1.95 = 21.45
 * <p>
 * 仅服务端处理：baseDamage 是服务端权威值，会通过原版同步包同步到客户端。
 * <p>
 * 覆盖范围：所有继承 AbstractArrow 的弹射物（原版弓箭/弩/三叉戟 + 继承该类的模组弹射物）。
 * 不覆盖 ThrowableProjectile（雪球/末影珍珠等，无伤害或固定伤害）和完全自定义弹射物。
 * 另：弹射物黑名单（{@link Config#isProjectileBlacklisted}，末影珍珠等）一律跳过，不受充能攻击增幅。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ProjectileDamageHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        // 弹射物黑名单（末影珍珠等）：不被充能攻击增幅
        if (Config.isProjectileBlacklisted(arrow)) {
            return;
        }

        Entity owner = arrow.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 读取玩家伤害属性组（与 AttackDamageManager 一致）
        double attackDamageBase = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage");
        double attackDamagePercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_percent");
        double attackDamageIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_independent");

        // 总乘区 = 百分比 × 独立乘区
        double totalMultiplier = attackDamagePercent * attackDamageIndependent;

        double originalBaseDamage = arrow.getBaseDamage();
        // 应用：底数加算 + 乘区乘算（与近战伤害加成逻辑一致）
        double newBaseDamage = (originalBaseDamage + attackDamageBase) * totalMultiplier;

        // 叠加充能攻击释放期增幅：基础伤害 × (1 + 当前充能值)，公式与近战一致
        // 消退期间归属玩家的箭矢类弹射物加入世界时按当前充能值增幅
        double chargeValue = com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager
                .getReleasingChargeValue(playerUUID);
        if (chargeValue > 0) {
            newBaseDamage *= (1.0 + chargeValue);

            // 长按右键充能释放：每点充能值提升10%箭矢速度
            double speedMultiplier = 1.0 + chargeValue * 0.1;
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(speedMultiplier));
        }

        // 仅在有加成时修改
        if (newBaseDamage != originalBaseDamage) {
            arrow.setBaseDamage(newBaseDamage);
        }
    }
}

