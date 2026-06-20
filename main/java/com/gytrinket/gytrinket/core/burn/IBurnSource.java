package com.gytrinket.gytrinket.core.burn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 灼烧源接口
 * <p>
 * 定义灼烧的发起者，用于归因灼烧伤害
 * 扩展性：未来可以添加不同类型的灼烧源，实现不同的效果
 */
public interface IBurnSource {

    /**
     * 获取灼烧发起者
     * @return 发起者实体，如果为null则表示无归属（如环境伤害）
     */
    Entity getInitiator();

    /**
     * 获取发起者的UUID
     * @return 发起者UUID，如果无归属则返回null
     */
    default UUID getInitiatorUUID() {
        Entity initiator = getInitiator();
        return initiator != null ? initiator.getUUID() : null;
    }

    /**
     * 检查发起者是否为玩家
     * @return 如果发起者是玩家返回true
     */
    default boolean isPlayerInitiator() {
        return getInitiator() instanceof LivingEntity;
    }

    /**
     * 获取灼烧源的显示名称（用于调试）
     * @return 灼烧源名称
     */
    default String getName() {
        Entity initiator = getInitiator();
        return initiator != null ? initiator.getName().getString() : "Unknown";
    }

    /**
     * 默认的灼烧源实现
     */
    class DefaultBurnSource implements IBurnSource {
        private final Entity initiator;

        public DefaultBurnSource(Entity initiator) {
            this.initiator = initiator;
        }

        @Override
        public Entity getInitiator() {
            return initiator;
        }
    }
}
