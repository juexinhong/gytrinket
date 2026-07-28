package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstruct;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 僚机构造体逻辑类
 * <p>
 * 僚机是高阶武器构造体，行为类似追击阵列无人机。
 * 无阵列系统，攻击时发射多枚爆破弹。
 */
public class WingmanConstruct extends AbstractConstruct {

    public WingmanConstruct(String constructId, net.minecraft.world.entity.LivingEntity owner, double maxHealth) {
        super(constructId, owner, maxHealth);
    }

    @Override
    protected void spawnEntity() {
        Level level = owner.level();
        if (level.isClientSide) return;

        WingmanConstructEntity wingman = new WingmanConstructEntity(level, owner.getUUID(), this);

        Vec3 spawnPos = owner.position().add(0, 2, 0);
        wingman.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        // 刷新拦截机数据到客户端（从Manager统一查询）
        wingman.refreshInterceptorData();

        // 主动获取构造体属性（进化/母舰等动态属性需在实体创建后应用）
        wingman.refreshConstructAttributes();

        // 属性应用后再设置满血（此时maxHealth已包含动态加成）
        wingman.setHealth(wingman.getMaxHealth());

        level.addFreshEntity(wingman);
        entityUUID = wingman.getUUID();

        ConstructManager.getInstance().registerConstructEntity(owner.getUUID(), constructId, wingman);
    }
}
