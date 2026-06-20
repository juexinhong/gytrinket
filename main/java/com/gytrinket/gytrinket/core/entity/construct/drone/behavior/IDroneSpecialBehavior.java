package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Set;

public interface IDroneSpecialBehavior {

    String getId();

    Set<String> getRequiredTags();

    default int getPriority() {
        return 100;
    }

    default boolean canApply(DroneConstructEntity drone) {
        if (getRequiredTags() == null || getRequiredTags().isEmpty()) {
            return true;
        }
        Set<String> droneTags = drone.getDroneConstruct() != null
                ? drone.getDroneConstruct().getCurrentTags()
                : Set.of();
        return droneTags.containsAll(getRequiredTags());
    }

    default boolean tryPreventDeath(DroneConstructEntity drone, DamageSource source) {
        return false;
    }

    default void onTick(DroneConstructEntity drone) {
    }

    default void onDamageTaken(DroneConstructEntity drone, DamageSource source, float amount) {
    }

    default void onNearDeath(DroneConstructEntity drone, DamageSource source) {
    }

    default void onDeath(DroneConstructEntity drone, DamageSource source) {
    }

    default void onArrayChanged(DroneConstructEntity drone) {
    }

    default void onSpawned(DroneConstructEntity drone) {
    }
}
