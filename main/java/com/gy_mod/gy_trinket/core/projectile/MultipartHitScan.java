package com.gy_mod.gy_trinket.core.projectile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 多体节实体（multi-part entity）的弹道路径命中检测。
 * <p>
 * 无人机子弹、僚机爆破弹等「自实现碰撞」弹射物共用本类：沿本刻到下一刻的线段
 * 找出第一个可命中的碰撞箱。与普通实体不同，多体节生物必须逐个测体节，
 * 原因见下。
 *
 * <h2>为什么不能只测父实体 AABB</h2>
 * 体节（{@link net.minecraftforge.entity.PartEntity}）由父实体用普通对象
 * 持有，<b>不注册进 level 实体列表</b>，因此 {@code level.getEntities(...)}
 * 只会返回父实体；而父实体 AABB 往往只是一小块躯干，伸出去的头、颈、翅、尾、
 * 身体节段全落在它外面：
 * <ul>
 *   <li>原版末影龙：父实体 AABB 是 16×8 的巨型包络且 {@code isPickable() == false}
 *       （原版准星也打不到），真实可打点只有 head / neck / body / tail×3 / wing×2
 *       八个体节；</li>
 *   <li>暮色森林娜迦：父实体 AABB 只有头部 2×2，12 个身体节段沿行进方向拖出 20+ 格；</li>
 *   <li>暮色森林九头蛇、灾变等模组的 boss：同类结构。</li>
 * </ul>
 * 与强袭客户端准星检查（{@code AttackModeClientUtil.findTargetInCrosshair}）
 * 、充能横扫检查（{@code ChargedAttackSweepHandler.findTargetInCrosshair}）保持
 * 同一套做法：<b>先按父实体扫候选，再逐个测体节碰撞箱</b>。
 *
 * <h2>父实体 AABB 何时参与判定</h2>
 * <ul>
 *   <li>普通实体（无体节）：照旧只用父实体 AABB；</li>
 *   <li>多体节实体：父实体 AABB 仅在 {@link Entity#isPickable()} 为 true 时参与
 *       ——末影龙的 16×8 包络整块不可选中，若当命中箱，子弹会在离龙身数格外的
 *       空气里“命中”；娜迦/九头蛇父实体可选中，头部 AABB 仍需保留。</li>
 * </ul>
 *
 * <h2>搜索范围</h2>
 * 候选扫描盒按 {@link #PART_SEARCH_MARGIN} 外扩：体节可能离父实体很远
 * （娜迦尾节与头相距 20+ 格），只扫弹道邻域会连父实体都找不到、更测不到体节。
 * 外扩只影响候选数量，命中仍由精确线段裁剪决定。
 */
public final class MultipartHitScan {

    /**
     * 多体节父实体探测外扩（格）。
     * <p>
     * 体节相对父实体 AABB 的最大外伸距离：末影龙翼尖约 8 格，娜迦节段可到 20+ 格，
     * 取 16 格覆盖绝大多数多体节生物；再远的目标在弹道邻域里连父实体都扫不到，
     * 加大只会线性增加候选数量。
     */
    public static final double PART_SEARCH_MARGIN = 16.0D;

    /** 弹体自身的命中容差（格），与旧实现保持一致 */
    public static final double DEFAULT_HITBOX_INFLATE = 0.3D;

    private MultipartHitScan() {
    }

    /**
     * 一次命中结果。
     *
     * @param hitbox  真正被击中的碰撞箱实体：普通生物是父实体本身，多体节生物是体节
     * @param target  用于结算的父实体（伤害合并、击退标记、易伤等按父实体记账）
     * @param position 线段与碰撞箱的相交点（子弹停在这里）
     */
    public record Hit(Entity hitbox, LivingEntity target, Vec3 position) {
    }

    /**
     * 沿线段找第一个命中的碰撞箱。
     *
     * @param level        世界
     * @param from         本刻位置
     * @param to           下一刻位置
     * @param hitboxInflate 碰撞箱外扩（格）
     * @param canHit       目标过滤：只对父实体调用（体节继承父实体的过滤结果），
     *                     被拒绝的父实体连同其体节一并跳过
     * @return 最近的命中结果；没有命中返回 null
     */
    @Nullable
    public static Hit findFirstOnPath(Level level, Vec3 from, Vec3 to, double hitboxInflate,
                                      Predicate<Entity> canHit) {
        if (from.distanceToSqr(to) < 1.0E-9D) {
            return null;
        }

        AABB searchBox = new AABB(from, to).inflate(PART_SEARCH_MARGIN);
        Best best = new Best(from);

        for (Entity candidate : level.getEntities((Entity) null, searchBox, e -> e instanceof LivingEntity)) {
            if (!(candidate instanceof LivingEntity target) || !canHit.test(candidate)) {
                continue;
            }

            Entity[] parts = target.getParts();
            if (parts == null || parts.length == 0) {
                // 普通单体生物：维持原判定（仅父实体 AABB）
                best.offer(target, target, target.getBoundingBox(), to, hitboxInflate);
                continue;
            }

            // 多体节：父实体 AABB 只是包络，仅可选中时才是有效命中箱
            if (target.isPickable()) {
                best.offer(target, target, target.getBoundingBox(), to, hitboxInflate);
            }
            for (Entity part : parts) {
                if (part == null || !part.isAlive() || !part.isPickable()) {
                    continue;
                }
                best.offer(part, target, part.getBoundingBox(), to, hitboxInflate);
            }
        }

        return best.hit;
    }

    /**
     * 区域命中（爆炸/能量波/电弧/近战横扫等范围伤害用）：每个父实体只返回一个命中箱。
     * <p>
     * 与弹道路径不同，区域伤害是「体积波及」：父实体 AABB 恒参与判定（与原版爆炸
     * 一致，也避免把末影龙这类不可选中的巨型包络漏掉），再额外测试各体节——
     * 暮色森林娜迦这类「父实体只有头、身体节段拖出 20+ 格」的 boss，爆心落在
     * 身体上时才能被波及。
     * <p>
     * 同一父实体的多个体节同时落在区域内时只取离 {@code reference}（爆心/波心）
     * 最近的一个，保证每个生物每次结算只受一次伤害。
     *
     * @param level       世界
     * @param region      区域查询盒（实际查询会再外扩 {@link #PART_SEARCH_MARGIN}）
     * @param reference   参考点：区域内多个命中箱时取最近的一个（通常是爆心）
     * @param canHit      目标过滤，只对父实体调用
     * @param hitboxTest  命中箱判定（爆炸球/能量波矩形/横扫扇形等各自的几何判定）
     * @param parentBoxRequiresPickable 父实体 AABB 是否要求可选中：
     *        范围伤害传 false（与原版爆炸一致，末影龙等不可选中的巨型包络也要参与）；
     *        光束/射线等瞄准命中传 true（末影龙 16×8 的空中包络不应算命中）
     * @return 每个命中的父实体一条记录；没有命中返回空列表
     */
    public static List<Hit> collectInRegion(Level level, AABB region, Vec3 reference,
                                            Predicate<Entity> canHit, Predicate<AABB> hitboxTest,
                                            boolean parentBoxRequiresPickable) {
        return collectInRegion(level, region, reference, canHit, hitboxTest, parentBoxRequiresPickable,
                PART_SEARCH_MARGIN);
    }

    /**
     * 区域查询（可指定体节探测外扩）。
     * <p>
     * 每 tick 执行的热路径（守卫撞退、脉冲索敌、近死索敌、陨石逐 tick 碰撞）应传较小的
     * {@code searchMargin}（如 1~2）：这些机制的判定距离本身很短，远处体节意义不大，
     * 避免每 tick 扫描 30+ 格的区块段。
     */
    public static List<Hit> collectInRegion(Level level, AABB region, Vec3 reference,
                                            Predicate<Entity> canHit, Predicate<AABB> hitboxTest,
                                            boolean parentBoxRequiresPickable, double searchMargin) {
        AABB queryBox = region.inflate(searchMargin);
        List<Hit> hits = new ArrayList<>();
        for (Entity candidate : level.getEntities((Entity) null, queryBox, e -> e instanceof LivingEntity)) {
            if (!(candidate instanceof LivingEntity target) || !canHit.test(candidate)) {
                continue;
            }
            Entity bestBox = null;
            double bestDistSqr = Double.MAX_VALUE;
            AABB parentBox = target.getBoundingBox();
            if ((!parentBoxRequiresPickable || target.isPickable()) && hitboxTest.test(parentBox)) {
                bestBox = target;
                bestDistSqr = parentBox.getCenter().distanceToSqr(reference);
            }
            Entity[] parts = target.getParts();
            if (parts != null) {
                for (Entity part : parts) {
                    if (part == null || !part.isAlive() || !part.isPickable()) {
                        continue;
                    }
                    AABB partBox = part.getBoundingBox();
                    if (!hitboxTest.test(partBox)) {
                        continue;
                    }
                    double distSqr = partBox.getCenter().distanceToSqr(reference);
                    if (distSqr < bestDistSqr) {
                        bestDistSqr = distSqr;
                        bestBox = part;
                    }
                }
            }
            if (bestBox != null) {
                hits.add(new Hit(bestBox, target, bestBox.getBoundingBox().getCenter()));
            }
        }
        return hits;
    }

    /** 最近命中累加器：同一线段上多个碰撞箱取离起点最近的一个 */
    private static final class Best {
        private final Vec3 from;
        private Hit hit;
        private double bestDistSqr = Double.MAX_VALUE;

        private Best(Vec3 from) {
            this.from = from;
        }

        private void offer(Entity hitbox, LivingEntity target, AABB box, Vec3 to, double inflate) {
            Vec3 position = box.inflate(inflate).clip(from, to).orElse(null);
            if (position == null) {
                return;
            }
            double distSqr = from.distanceToSqr(position);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                hit = new Hit(hitbox, target, position);
            }
        }
    }
}
