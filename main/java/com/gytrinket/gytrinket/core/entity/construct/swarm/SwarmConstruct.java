package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.core.entity.construct.AbstractConstruct;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 蜂群构造体逻辑类
 * <p>
 * 蜂群是基础其他构造体，行为类似无人机追击阵列（含鸟群算法）。
 * 攻击时发射电弧（范围伤害，单次伤害，施加可叠加易伤）。
 * 玩家护盾受损时部分蜂群转为修复模式；护盾破裂时全员获得攻速/移速增益。
 * 单实例构建时有小概率提升等阶（标准/高阶），获得属性加成。
 */
public class SwarmConstruct extends AbstractConstruct {

    /** 单实例等阶：0=基础 1=标准 2=高阶 */
    private final int tier;

    public SwarmConstruct(String constructId, net.minecraft.world.entity.LivingEntity owner, double maxHealth, int tier) {
        super(constructId, owner, maxHealth);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    /**
     * 根据等阶返回生命/伤害加成倍率。
     * 基础=1.0，标准=1.5，高阶=2.0。
     */
    public double getTierStatMultiplier() {
        switch (tier) {
            case SwarmConstructTypes.TIER_STANDARD: return 1.5;
            case SwarmConstructTypes.TIER_ADVANCED: return 2.0;
            default: return 1.0;
        }
    }

    @Override
    protected void spawnEntity() {
        Level level = owner.level();
        if (level.isClientSide) return;

        SwarmConstructEntity swarm = new SwarmConstructEntity(level, owner.getUUID(), this);

        Vec3 spawnPos = owner.position().add(0, 2, 0);
        swarm.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        swarm.setHealth(swarm.getMaxHealth());

        level.addFreshEntity(swarm);
        entityUUID = swarm.getUUID();

        ConstructManager.getInstance().registerConstructEntity(owner.getUUID(), constructId, swarm);
    }
}
