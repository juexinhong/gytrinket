package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 构造体实体恢复器接口，用于从持久化数据中恢复构造体实体。
 * <p>
 * 各构造体类型注册时提供此恢复器的实现，
 * 由 {@link com.gy_mod.gy_trinket.storage.datacenter.DataCenterLifecycleHandler} 在玩家重登时统一调用。
 */
@FunctionalInterface
public interface IEntityRestorer {
    /**
     * 从持久化数据中恢复构造体实体
     *
     * @param player   所属玩家
     * @param data     构造体持久化数据
     * @param level    服务端世界
     * @return 恢复的实体，如果恢复失败返回 null
     */
    Entity restore(ServerPlayer player, ConstructData data, ServerLevel level);
}
