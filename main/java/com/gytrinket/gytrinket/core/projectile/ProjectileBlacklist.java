package com.gytrinket.gytrinket.core.projectile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * 弹射物黑名单
 * <p>
 * 黑名单中的弹射物类型不参与本模组的弹射物系统：
 * <ul>
 *   <li>不会被充能攻击增幅（ProjectileDamageHandler 跳过）</li>
 *   <li>不会被点射复制（ProjectileBurstManager 跳过）</li>
 * </ul>
 * 当前名单：末影珍珠（点射复制会导致多次瞬移，语义混乱且不可控）。
 */
public final class ProjectileBlacklist {

    private static final Set<EntityType<?>> BLACKLIST = Set.of(EntityType.ENDER_PEARL);

    private ProjectileBlacklist() {
    }

    public static boolean isBlacklisted(Entity entity) {
        return BLACKLIST.contains(entity.getType());
    }
}
