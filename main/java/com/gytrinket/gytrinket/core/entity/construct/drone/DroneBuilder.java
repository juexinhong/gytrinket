package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.core.entity.construct.ConstructBuilder;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;
import com.gytrinket.gytrinket.core.entity.construct.drone.effect.AssaultEffect;
import com.gytrinket.gytrinket.core.entity.construct.drone.effect.DefenseEffect;
import com.gytrinket.gytrinket.core.entity.construct.drone.effect.IDroneEffect;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 无人机构建器
 * <p>
 * 扩展了基础构建器，创建无人机专用的构造体数据。
 * 会自动检测玩家是否拥有突击/防御模块。
 */
public class DroneBuilder extends ConstructBuilder {
    private final DroneArrayType defaultArrayType;

    public DroneBuilder(Player player, ConstructType constructType, DroneArrayType defaultArrayType) {
        super(player, constructType);
        this.defaultArrayType = defaultArrayType;
    }

    @Override
    protected void onBuildComplete() {
        UUID entityUUID = UUID.randomUUID();

        boolean hasAssault = DroneManager.getInstance().hasAssaultModule(getPlayer());
        boolean hasDefense = DroneManager.getInstance().hasDefenseModule(getPlayer());

        // 在构建完成时获取玩家当前的阵列类型，而不是开始构建时的类型
        DroneArrayType currentArrayType = DroneArrayManager.getInstance().getPlayerArrayType(getPlayer());

        DroneConstructData data = new DroneConstructData(
                getConstructType().getId(),
                entityUUID,
                getConstructType().getMaxHealth(),
                currentArrayType
        );
        data.setHasAssaultModule(hasAssault);
        data.setHasDefenseModule(hasDefense);

        ConstructManager.getInstance().addConstruct(getPlayer(), data);
        
        List<IDroneEffect> effects = new ArrayList<>();
        if (hasAssault) {
            effects.add(new AssaultEffect());
        }
        if (hasDefense) {
            effects.add(new DefenseEffect());
        }
        
        DroneConstruct droneConstruct = new DroneConstruct(
                getConstructType().getId(),
                currentArrayType,
                effects,
                getPlayer(),
                getConstructType().getMaxHealth()
        );
        droneConstruct.onCreated();

        if (droneConstruct.getEntityUUID() != null) {
            data.setEntityUUID(droneConstruct.getEntityUUID());
        }
    }
}
