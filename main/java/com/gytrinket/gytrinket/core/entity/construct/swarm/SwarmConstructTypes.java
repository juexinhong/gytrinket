package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.ConstructCategory;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;

import java.util.Set;

/**
 * 蜂群构造体类型注册
 * <p>
 * 蜂群属于基础、其他、构造体类别。
 * 构建时有小概率提升等阶（标准/高阶），由 SwarmBuilder 在构建时决定单实例的等阶。
 */
public class SwarmConstructTypes {
    public static final String SWARM = "swarm";

    /** 等阶：基础 */
    public static final int TIER_BASIC = 0;
    /** 等阶：标准 */
    public static final int TIER_STANDARD = 1;
    /** 等阶：高阶 */
    public static final int TIER_ADVANCED = 2;

    public static void register() {
        ConstructManager manager = ConstructManager.getInstance();

        manager.registerConstructType(ConstructType.builder(SWARM)
                .name("蜂群")
                .categories(ConstructCategory.createOtherCategories(ConstructCategory.Tier.BASIC))
                .tags(Set.of("swarm"))
                .buildTime(Config.getSwarmBuildTime())
                .maxHealth(Config.getSwarmBaseHealth())
                .maxCount(Config.getSwarmMaxCount())
                .constructClass(SwarmConstruct.class)
                .build());
    }
}
