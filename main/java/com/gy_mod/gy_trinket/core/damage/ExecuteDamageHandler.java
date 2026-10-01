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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 斩杀处理器（死亡事件阶段拦截，以归属玩家伤害源重施加）
 * <p>
 * 本模组伤害体系（无人机子弹/光束/蜂群/虹吸/灼烧/点燃/模拟爆炸/能量波等）施加时
 * 伤害源恒不带玩家（避免非致死时对玩家触发仇恨），生物死于这些伤害时其他模组
 * （FTB 任务等）无法识别为玩家击杀。本处理器监听 {@link LivingDeathEvent}：
 * <b>当生物死于白名单伤害源（本模组自定义伤害体系）时，取消本次死亡，恢复少量
 * 血量后立即以归属玩家的斩杀伤害源（{@code execute_damage}，穿甲/穿魔咒/
 * 穿抗性）重施加致死伤害</b>——真实死亡以玩家伤害源发生，死亡事件、掉落物、
 * 经验、统计中的击杀者均为玩家，FTB 等按伤害源判定"玩家击杀"的模组可正确检测。
 * <p>
 * 在死亡事件阶段拦截（而非伤害阶段预估致死）覆盖所有致死路径：任何减免/改伤
 * 之后真实发生的死亡（含直接调用 die() 的路径）都会被拦截，无需预估伤害量。
 * <p>
 * <b>白名单资格：仅本模组自定义伤害参与斩杀</b>，玩家近战/玩家射出的箭等
 * 原版伤害不属于本模组体系，天然排除（其击杀归属本就是施加者）。判定途径：
 * <ol>
 *   <li>伤害类型属于 gytrinket 命名空间（全部自定义伤害类型）</li>
 *   <li>伤害源直接实体是本模组构造体（构造体近战使用原版 mob_attack 类型）</li>
 *   <li>{@link DamageAttributionWindow} 施加期窗口标记（模拟爆炸等施加时
 *       使用原版 explosion 类型，由窗口标记识别）</li>
 *   <li>灼烧施加中的 magic 半份（原版 magic 类型，由 {@link BurnManager}
 *       施加中标记识别）</li>
 * </ol>
 * 归属玩家解析顺序（仅资格通过后）：
 * <ol>
 *   <li>{@link DamageAttributionWindow} 施加期窗口标记（模拟爆炸/能量波/虹吸等
 *       施加时不带攻击者的伤害，在施加处登记归属玩家）</li>
 *   <li>灼烧/点燃专有解析（发起者记录在各 Manager 数据中，伤害源不带实体）</li>
 *   <li>伤害源直接实体是本模组构造体（无人机子弹/光束/蜂群/僚机/守卫等）
 *       → 归属其拥有者</li>
 * </ol>
 * 仅在斩杀开关（{@link ExecuteToggleManager}）开启时介入。
 * <p>
 * <b>重复死亡事件防护</b>：重施加的斩杀伤害完成击杀（execute 源死亡事件计数）
 * 后，外层管线/施加方可能残留重复的 die() 调用。execute 源死亡事件正常走完后
 * 标记，随后同一实体在极短窗口内的重复死亡事件在 HIGHEST 优先级（早于 FTB
 * 计数）取消；若 execute 死亡被其他模组取消（如镜魂弱点武器机制）则不标记，
 * 后续死亡事件照常交由其他模组处理。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ExecuteDamageHandler {

    private ExecuteDamageHandler() {}

    /**
     * 已完成斩杀死亡（execute 源死亡事件未被取消）的实体：
     * UUID → 登记时的游戏时间。随后极短窗口内的重复死亡事件将被取消。
     */
    private static final Map<UUID, Long> EXECUTED_DEATH_PENDING_DUPLICATE = new ConcurrentHashMap<>();

    /**
     * 重复死亡事件的取消窗口（tick）：重施加与残留的重复 die 调用发生在同一 tick 内
     */
    private static final long DUPLICATE_DEATH_WINDOW_TICKS = 2L;

    /**
     * 阻止死亡后恢复的血量：hurt() 对濒死（血量 ≤ 0）目标直接无效，
     * 恢复正血量是重施加生效的前提；斩杀伤害穿甲穿魔咒穿抗性，
     * 等量伤害足以致死
     */
    private static final float REVIVE_HEALTH = 1.0F;

    /**
     * 斩杀拦截 + 重复死亡事件取消（HIGHEST：早于 FTB 等击杀计数方，
     * 且事件取消后后续监听者不再收到）。
     * <p>
     * 生物死于白名单伤害源时：取消本次死亡（FTB 不会对模组源计数），
     * 恢复正血量并清空无敌帧后，立即以归属玩家的斩杀伤害源重施加等量
     * 致死伤害完成玩家归属的真实击杀。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }

        ResourceKey<DamageType> damageType =
                event.getSource().typeHolder().unwrapKey().orElse(null);

        // 斩杀自身的死亡事件放行：这是唯一应被计数的死亡
        if (damageType == ModDamageTypes.EXECUTE_DAMAGE) {
            return;
        }

        // 防二次击杀：斩杀完成后短窗口内的重复死亡事件（残留的外层管线 die）→ 取消
        Long markedAt = EXECUTED_DEATH_PENDING_DUPLICATE.remove(target.getUUID());
        if (markedAt != null
                && target.level().getGameTime() - markedAt <= DUPLICATE_DEATH_WINDOW_TICKS) {
            event.setCanceled(true);
            return;
        }

        // 白名单资格：仅本模组自定义伤害体系的击杀参与斩杀改判，
        // 玩家近战/玩家射出的箭等原版伤害不参与
        if (!isModDamage(event.getSource(), target)) {
            return;
        }

        Player player = resolveAttributionPlayer(event.getSource(), target);
        if (player == null) {
            return;
        }

        if (!ExecuteToggleManager.isExecuteEnabled(player)) {
            return;
        }

        event.setCanceled(true);
        target.setHealth(REVIVE_HEALTH);
        target.invulnerableTime = 0;
        target.hurt(ModDamageTypes.getExecuteDamageSource(target.level(), player), REVIVE_HEALTH);
    }

    /**
     * 斩杀死亡事件走完后的标记/清理（LOWEST：其他模组的取消操作已全部完成）。
     * <p>
     * execute 源死亡事件未被取消 → 标记待取消，随后同一实体的重复死亡事件将被取消；
     * 被其他模组取消（如镜魂弱点武器机制拦下击杀）→ 不标记，
     * 后续死亡事件照常交由其他模组处理，避免绕过其机制。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeathMarkDuplicate(LivingDeathEvent event) {
        if (event.getSource().typeHolder().unwrapKey().orElse(null) != ModDamageTypes.EXECUTE_DAMAGE) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (event.isCanceled()) {
            // 斩杀死亡被其他模组拦下：清理登记，不标记重复
            EXECUTED_DEATH_PENDING_DUPLICATE.remove(target.getUUID());
            return;
        }

        // 斩杀死亡正常完成：登记，用于取消残留的重复死亡事件
        EXECUTED_DEATH_PENDING_DUPLICATE.put(target.getUUID(), target.level().getGameTime());
    }

    /**
     * 白名单资格判定：该次伤害是否属于本模组自定义伤害体系。
     * <p>
     * 仅本模组伤害体系才允许进入斩杀判定与换源，玩家近战/原版生物攻击等
     * 一律不参与。判定途径：
     * <ol>
     *   <li>伤害类型属于 gytrinket 命名空间（无人机子弹/蜂群/虹吸/灼烧/点燃/
     *       最终伤害等全部自定义类型）</li>
     *   <li>伤害源直接实体是本模组构造体（构造体近战使用原版 mob_attack 类型）</li>
     *   <li>{@link DamageAttributionWindow} 施加期窗口标记（模拟爆炸等施加时
     *       使用原版 explosion 类型，由窗口标记识别；窗口以 try/finally 紧密
     *       包裹 hurt()，存在即代表本次伤害为本模组施加）</li>
     *   <li>灼烧施加中的 magic 半份（原版 magic 类型，由 {@link BurnManager}
     *       施加中标记识别）</li>
     * </ol>
     */
    private static boolean isModDamage(DamageSource source, LivingEntity target) {
        ResourceKey<DamageType> damageType = source.typeHolder().unwrapKey().orElse(null);
        if (damageType != null
                && ModDamageTypes.NAMESPACE.equals(damageType.location().getNamespace())) {
            return true;
        }
        if (source.getEntity() instanceof AbstractConstructEntity) {
            return true;
        }
        if (DamageAttributionWindow.get(target) != null) {
            return true;
        }
        ResourceKey<DamageType> magicType =
                target.damageSources().magic().typeHolder().unwrapKey().orElse(null);
        return damageType != null && damageType.equals(magicType) && BurnManager.isBurnApplying(target);
    }

    /**
     * 根据伤害来源解析应归属的玩家（仅白名单资格通过后调用），
     * 无法归属时返回 null
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

        // 伤害源直接实体是本模组构造体（无人机子弹/光束的无人机本体、
        // 蜂群、僚机、守卫代理等）→ 归属其拥有者
        if (source.getEntity() instanceof AbstractConstructEntity construct
                && construct.getOwner() instanceof Player player) {
            return player;
        }

        // 其余本模组类型（最终伤害/瞬时伤害等）未携带可归属实体 → 无法归属
        return null;
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
