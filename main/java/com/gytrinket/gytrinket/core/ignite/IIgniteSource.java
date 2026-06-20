package com.gytrinket.gytrinket.core.ignite;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * 点燃源接口
 * <p>
 * 用于归因点燃伤害，使伤害归属为特定实体
 */
public interface IIgniteSource {
    /**
     * 获取点燃源实体
     * @return 点燃源实体
     */
    Entity getInitiator();

    /**
     * 获取点燃源UUID
     * @return 点燃源UUID
     */
    default UUID getInitiatorUUID() {
        Entity initiator = getInitiator();
        return initiator != null ? initiator.getUUID() : null;
    }

    /**
     * 获取点燃源名称
     * @return 点燃源名称
     */
    String getName();

    /**
     * 默认点燃源实现
     */
    class DefaultIgniteSource implements IIgniteSource {
        private final Entity entity;

        public DefaultIgniteSource(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Entity getInitiator() {
            return entity;
        }

        @Override
        public String getName() {
            return "default";
        }
    }
}
