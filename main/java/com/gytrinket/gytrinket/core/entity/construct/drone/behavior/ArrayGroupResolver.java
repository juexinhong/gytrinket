package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 无人机阵列成员解析：给「按 index 均匀分布」的阵列行为（环绕/警戒/列队）提供同一份成员列表。
 * <p>
 * 服务端：直接读 {@link ConstructManager} 的活跃实体表。
 * <p>
 * 客户端：{@link ConstructManager} 的活跃实体表只在服务端维护，客户端拿到的是空表；
 * 但阵列「刚性跟随」要求客户端自行计算位置（否则位置有服务端同步延迟），
 * 所以客户端改为扫描本地世界里同 owner、同阵列类型的无人机实体。
 * 实体 ID 与服务端一致，排序规则相同，因此客户端算出的 index/总数与服务端完全对齐。
 */
final class ArrayGroupResolver {

    /** 搜索半径：阵列环绕半径 3 格，留足余量 */
    private static final double CLIENT_SCAN_RADIUS = 40.0;

    private ArrayGroupResolver() {
    }

    /**
     * 收集同一阵列内的全部无人机（按实体 ID 升序，保证 index 稳定）。
     */
    static List<DroneConstructEntity> collect(Entity self, LivingEntity owner,
                                              Predicate<DroneConstructEntity> arrayFilter) {
        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance()
                .getActiveConstructEntities(owner.getUUID(), DroneConstructTypes.DRONE);

        List<DroneConstructEntity> result = new ArrayList<>();
        if (!entitiesMap.isEmpty()) {
            for (Entity entity : entitiesMap.values()) {
                if (entity instanceof DroneConstructEntity drone && drone.isAlive() && arrayFilter.test(drone)) {
                    result.add(drone);
                }
            }
        } else if (self.getLevel().isClientSide) {
            // 客户端没有服务端注册表：从本地世界扫描同 owner 的无人机
            for (DroneConstructEntity drone : self.getLevel().getEntitiesOfClass(
                    DroneConstructEntity.class, owner.getBoundingBox().inflate(CLIENT_SCAN_RADIUS),
                    drone -> drone.isAlive()
                            && owner.getUUID().equals(drone.getOwnerUUID())
                            && arrayFilter.test(drone))) {
                result.add(drone);
            }
        }
        result.sort(Comparator.comparingInt(Entity::getId));
        return result;
    }
}
