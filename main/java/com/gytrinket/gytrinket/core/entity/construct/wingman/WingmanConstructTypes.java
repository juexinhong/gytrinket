package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.ConstructCategory;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.ConstructType;

import java.util.Set;

/**
 * 僚机构造体类型注册
 */
public class WingmanConstructTypes {
    public static final String WINGMAN = "wingman";

    public static void register() {
        ConstructManager manager = ConstructManager.getInstance();

        manager.registerConstructType(ConstructType.builder(WINGMAN)
                .name("僚机")
                .categories(ConstructCategory.createOtherCategories(ConstructCategory.Tier.ADVANCED))
                .tags(Set.of("wingman"))
                .buildTime(500) // 25秒 = 500tick
                .maxHealth(Config.getWingmanBaseHealth())
                .maxCount(Config.getWingmanMaxCount())
                .constructClass(WingmanConstruct.class)
                .build());
    }
}
