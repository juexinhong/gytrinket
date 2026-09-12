package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.core.entity.construct.AbstractConstruct;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 僚机构造体逻辑类
 * <p>
 * 僚机是高阶武器构造体，行为类似追击阵列无人机。
 * 无阵列系统，攻击时发射多枚爆破弹。
 */
public class WingmanConstruct extends AbstractConstruct {

    /** 实例键（来源物品 ID），非实例化路径为 null */
    @javax.annotation.Nullable
    private final String instanceKey;

    public WingmanConstruct(String constructId, net.minecraft.world.entity.LivingEntity owner, double maxHealth) {
        this(constructId, owner, maxHealth, null);
    }

    public WingmanConstruct(String constructId, net.minecraft.world.entity.LivingEntity owner, double maxHealth,
                            @javax.annotation.Nullable String instanceKey) {
        super(constructId, owner, maxHealth);
        this.instanceKey = instanceKey;
    }

    /** 实例键（来源物品 ID），非实例化路径为 null */
    @javax.annotation.Nullable
    public String getInstanceKey() {
        return instanceKey;
    }

    @Override
    public ConstructData createData(UUID entityUUID) {
        WingmanConstructData data = new WingmanConstructData(constructId, entityUUID, maxHealth);
        data.setInstanceKey(instanceKey);
        return data;
    }

    @Override
    protected void spawnEntity() {
        Level level = owner.level();
        if (level.isClientSide) return;

        WingmanConstructEntity wingman = new WingmanConstructEntity(level, owner.getUUID(), this);

        Vec3 spawnPos = owner.position().add(0, 2, 0);
        wingman.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        wingman.setHealth(wingman.getMaxHealth());

        level.addFreshEntity(wingman);
        entityUUID = wingman.getUUID();

        ConstructManager.getInstance().registerConstructEntity(owner.getUUID(), constructId, wingman);
    }
}
