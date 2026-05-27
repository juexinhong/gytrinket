package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.gytrinket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DroneSpecialBehaviorManager {

    private static final DroneSpecialBehaviorManager INSTANCE = new DroneSpecialBehaviorManager();

    private final Map<String, IDroneSpecialBehavior> registeredBehaviors = new ConcurrentHashMap<>();

    private DroneSpecialBehaviorManager() {}

    public static DroneSpecialBehaviorManager getInstance() {
        return INSTANCE;
    }

    public void registerBehavior(IDroneSpecialBehavior behavior) {
        registeredBehaviors.put(behavior.getId(), behavior);
        gytrinket.LOGGER.info("注册无人机特殊行为: {}", behavior.getId());
    }

    public IDroneSpecialBehavior getBehavior(String id) {
        return registeredBehaviors.get(id);
    }

    public Collection<IDroneSpecialBehavior> getAllBehaviors() {
        return Collections.unmodifiableCollection(registeredBehaviors.values());
    }

    public List<IDroneSpecialBehavior> getApplicableBehaviors(com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity drone) {
        List<IDroneSpecialBehavior> applicable = new ArrayList<>();
        for (IDroneSpecialBehavior behavior : registeredBehaviors.values()) {
            if (behavior.canApply(drone)) {
                applicable.add(behavior);
            }
        }
        return applicable;
    }
}
