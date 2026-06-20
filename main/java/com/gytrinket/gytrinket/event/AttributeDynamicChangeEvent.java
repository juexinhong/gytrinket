package com.gytrinket.gytrinket.event;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * 属性动态变化事件
 * 当动态属性被添加、更新或移除时触发此事件
 */
public class AttributeDynamicChangeEvent extends Event {
    private final UUID playerUUID;
    private final String namespace;
    private final String attributeName;
    private final double value;
    private final ChangeType changeType;

    public enum ChangeType {
        ADD,
        UPDATE,
        REMOVE
    }

    public AttributeDynamicChangeEvent(UUID playerUUID, String namespace, String attributeName, double value, ChangeType changeType) {
        this.playerUUID = playerUUID;
        this.namespace = namespace;
        this.attributeName = attributeName;
        this.value = value;
        this.changeType = changeType;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public double getValue() {
        return value;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getFullKey() {
        return namespace + ":" + attributeName;
    }
}
