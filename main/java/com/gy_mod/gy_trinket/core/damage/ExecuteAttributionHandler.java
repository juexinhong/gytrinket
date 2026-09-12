package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.core.attack_mode.ExecuteToggleManager;
import com.gy_mod.gy_trinket.core.burn.BurnManager;
import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.ignite.IgniteManager;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 全局减伤后致死归属处理器
 * <p>
 * 无人机子弹/光束/模拟爆炸/能量波/虹吸/灼烧/点燃/蜂群等所有模组伤害体系的
 * 击杀归属不再由原始伤害预判（原始伤害会被护甲、韧性、抗性、吸收等减伤流程
 * 削减，导致高免伤敌人下的斩杀误判），而是统一在 {@link LivingDeathEvent}
 * 阶段按"目标实际死亡"这一事实判定：
 * <ul>
 *   <li>LivingDeathEvent 在 die() 内掉落/击杀统计之前触发，此时设置归属可让
 *       killed_by_player 战利品条件、击杀者记录、经验归属全部生效</li>
 *   <li>死亡即所有减伤流程后的真实结果，天然免疫免伤/护甲造成的误判</li>
 *   <li>不死图腾救活时不会触发 die()，也就不会错误归属</li>
 * </ul>
 * 归属解析顺序：
 * <ol>
 *   <li>{@link DamageAttributionWindow} 施加期窗口标记（模拟爆炸/能量波/虹吸等
 *       施加时不带攻击者的伤害，在施加处登记归属玩家）</li>
 *   <li>灼烧/点燃专有解析（发起者记录在各 Manager 数据中，伤害源不带实体）</li>
 *   <li>通用回退：伤害源实体是本模组构造体（无人机子弹/光束/蜂群/僚机/守卫等）
 *       → 归属其拥有者；玩家直接归属</li>
 * </ol>
 * 仅在事实致死且斩杀归属开关（{@link ExecuteToggleManager}）开启时改写击杀者。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ExecuteAttributionHandler {

    private ExecuteAttributionHandler() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }

        Player player = resolveAttributionPlayer(event.getSource(), target);
        if (player == null) {
            return;
        }

        if (!ExecuteToggleManager.isExecuteEnabled(player)) {
            return;
        }

        // 归属玩家（击杀者记为玩家，掉落/经验归玩家）
        target.setLastHurtByMob(player);
        target.setLastHurtByPlayer(player);
    }

    /**
     * 根据伤害来源解析应归属的玩家，非本模组伤害体系或无法归属时返回 null
     */
    private static Player resolveAttributionPlayer(DamageSource source, LivingEntity target) {
        // 施加期窗口标记优先（模拟爆炸/能量波/虹吸等施加时不带攻击者的伤害）
        Player windowed = DamageAttributionWindow.get(target);
        if (windowed != null) {
            return windowed;
        }

        ResourceKey<DamageType> damageType = source.typeHolder().unwrapKey().orElse(null);
        if (damageType == null) {
            return null;
        }

        ResourceKey<DamageType> magicType =
                target.damageSources().magic().typeHolder().unwrapKey().orElse(null);

        if (damageType == ModDamageTypes.BURN_DAMAGE
                || (damageType.equals(magicType) && BurnManager.isBurnApplying(target))) {
            // 灼烧（两段式：灼烧伤害源半份 + 施加中窗口内的 magic 半份）
            return toPlayer(BurnManager.getCurrentBurnInitiator(target));
        }

        if (damageType == ModDamageTypes.ON_FIRE_DAMAGE) {
            // 点燃
            return toPlayer(IgniteManager.getCurrentIgniteInitiator(target));
        }

        // 通用回退：伤害源实体是本模组构造体（无人机子弹/光束的无人机本体、
        // 蜂群、僚机、守卫代理等）→ 归属其拥有者；玩家直接归属
        return toPlayer(source.getEntity());
    }

    /**
     * 将归属者解析为玩家：玩家直接返回，构造体取其拥有者
     */
    private static Player toPlayer(Entity initiator) {
        if (initiator instanceof Player player) {
            return player;
        }
        if (initiator instanceof AbstractConstructEntity construct
                && construct.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}
