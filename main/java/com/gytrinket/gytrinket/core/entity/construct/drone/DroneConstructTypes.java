package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.ConstructCategory;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;

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
                .maxHealth(Config.getDroneBaseHealth())
                .maxCount(Config.getDroneMaxCount())
                .constructClass(DroneConstruct.class)
                .build());
    }
}
