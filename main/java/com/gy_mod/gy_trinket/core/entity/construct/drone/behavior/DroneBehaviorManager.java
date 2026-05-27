package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType;


/**
 * 无人机行为管理器
 * <p>
 * 管理所有无人机行为。
 */
public class DroneBehaviorManager {
    private static final DroneBehaviorManager INSTANCE = new DroneBehaviorManager();

    private OrbitBehavior orbitBehavior;
    private StandbyBehavior standbyBehavior;

    private DroneBehaviorManager() {
        registerDefaultBehaviors();
    }

    public static DroneBehaviorManager getInstance() {
        return INSTANCE;
    }

    private void registerDefaultBehaviors() {
        orbitBehavior = new OrbitBehavior();
        standbyBehavior = new StandbyBehavior();
    }

    public OrbitBehavior getOrbitBehavior() {
        return orbitBehavior;
    }

    public StandbyBehavior getStandbyBehavior() {
        return standbyBehavior;
    }

    public IDroneBehavior getBehavior(DroneArrayType arrayType) {
        if (arrayType == null) {
            return null;
        }

        if (arrayType.hasTag(DroneArrayType.Tags.STANDBY)) {
            return standbyBehavior;
        }
        return orbitBehavior;
    }
}
