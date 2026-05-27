package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructCategory;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;

import java.util.Set;

public class DroneConstructTypes {
    public static final String DRONE = "drone";

    public static void register() {
        ConstructManager manager = ConstructManager.getInstance();

        manager.registerConstructType(ConstructType.builder(DRONE)
                .name("无人机")
                .categories(ConstructCategory.createOtherCategories(ConstructCategory.Tier.STANDARD))
                .tags(Set.of("drone"))
                .buildTime(100)
                .maxHealth(5.0)
                .maxCount(3)
                .constructClass(DroneConstruct.class)
                .build());
    }
}
