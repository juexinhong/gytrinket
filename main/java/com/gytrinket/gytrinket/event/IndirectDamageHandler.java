package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.UUID;

/**
 * 间接伤害增幅处理器
 * <p>
 * 玩家伤害属性增幅所有"非玩家直接伤害且不是爆炸但归属玩家"的伤害，
 * 包括但不限于：非箭矢类弹射物、飞溅药水、模组自定义伤害等。
 * <p>
 * 判定条件（全部满足才应用增幅）：
 * <ul>
 *   <li>直接伤害来源不是玩家：玩家近战等直接攻击已由原版 ATTACK_DAMAGE 属性系统增幅
 *       （见 AttackDamageManager），此处排除避免双重加成</li>
 *   <li>不是箭矢类弹射物（{@link AbstractArrow}，弓箭/弩/三叉戟）：已由 ProjectileDamageHandler
 *       在弹射物加入世界时瞬发增幅（baseDamage 方式），此处排除避免双重加成</li>
 *   <li>伤害类型不是爆炸（{@link DamageTypeTags#IS_EXPLOSION}）：爆炸伤害不参与伤害属性增幅</li>
 *   <li>伤害归属玩家：{@link DamageSource#getEntity()} 为服务端玩家</li>
 *   <li>不是模组护盾系统转发的最终伤害（FINAL_DAMAGE 已携带原伤害的增幅结果，避免二次增幅）</li>
 * </ul>
 * <p>
 * 增幅公式与近战伤害加成逻辑完全一致：
 * <ul>
 *   <li>attack_damage（BASE底数）：加算到伤害值</li>
 *   <li>总乘区 = attack_damage_percent × attack_damage_independent</li>
 *   <li>新伤害 = (原伤害 + attack_damage) × 总乘区</li>
 * </ul>
 * <p>
 * 示例：base=9, percent=1.5, independent=1.3, 弹射物命中伤害=2.0
 *   新伤害 = (2.0 + 9) × 1.95 = 21.45
 * <p>
 * 使用 {@link LivingIncomingDamageEvent}（护甲计算前）修改伤害：
 * <ul>
 *   <li>与近战 ATTACK_DAMAGE 属性计算时机一致（均为护甲前）</li>
 *   <li>与 ProjectileDamageHandler 的箭矢类弹射物 baseDamage 瞬发增幅时机一致，伤害经护甲正常减免</li>
 *   <li>每次伤害仅触发一次，覆盖箭矢类弹射物以外的所有间接伤害</li>
 * </ul>
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class IndirectDamageHandler {

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();

        // 玩家直接伤害（近战/直接攻击）由 ATTACK_DAMAGE 属性系统处理，跳过避免双重加成
        if (source.getDirectEntity() instanceof Player) {
            return;
        }

        // 箭矢类弹射物伤害由 ProjectileDamageHandler 在弹射物加入世界时瞬发增幅（baseDamage 方式），跳过避免双重加成
        if (source.getDirectEntity() instanceof AbstractArrow) {
            return;
        }

        // 爆炸伤害不参与伤害属性增幅
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        // 护盾系统转发的最终伤害已携带原伤害的增幅结果，跳过避免二次增幅
        if (source.typeHolder().unwrapKey().orElse(null) == ModDamageTypes.FINAL_DAMAGE) {
            return;
        }

        // 仅处理归属服务端玩家的伤害
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();

        // 读取玩家伤害属性组（与 AttackDamageManager 一致）
        double attackDamageBase = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage");
        double attackDamagePercent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_percent");
        double attackDamageIndependent = AttributeManager.getPlayerAttribute(playerUUID, "attack_damage_independent");

        // 总乘区 = 百分比 × 独立乘区
        double totalMultiplier = attackDamagePercent * attackDamageIndependent;

        // 无加成时跳过
        if (attackDamageBase == 0 && totalMultiplier == 1.0) {
            return;
        }

        // 应用：底数加算 + 乘区乘算（与近战伤害加成逻辑一致）
        float newDamage = (float) ((event.getAmount() + attackDamageBase) * totalMultiplier);
        event.setAmount(newDamage);
    }
}
