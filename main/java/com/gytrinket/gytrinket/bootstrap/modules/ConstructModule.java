package com.gytrinket.gytrinket.bootstrap.modules;

import com.gytrinket.gytrinket.bootstrap.GyModule;
import com.gytrinket.gytrinket.bootstrap.ModuleContext;
import com.gytrinket.gytrinket.core.entity.construct.ConstructTypeRegistry;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneRegistry;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.CounterPulseBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.DroneSpecialBehaviorManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.NearDeathExplosionBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.NearDeathProtectionBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.SelfDestructBehavior;
import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmRegistry;
import com.gytrinket.gytrinket.core.entity.construct.wingman.NanoRegenManager;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanRegistry;

import java.util.Set;

/**
 * 构造体模块：三类构造体注册、无人机行为、僚机纳米回复。
 */
public final class ConstructModule implements GyModule {

    @Override
    public String id() {
        return "core.construct";
    }

    @Override
    public Set<String> requires() {
        return Set.of("core.defs", "core.attribute");
    }

    @Override
    public void setup(ModuleContext context) {
        NanoRegenManager.init();

        ConstructTypeRegistry.register(new DroneRegistry());
        ConstructTypeRegistry.register(new WingmanRegistry());
        ConstructTypeRegistry.register(new SwarmRegistry());
        ConstructTypeRegistry.executeRegistries();

        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathProtectionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new NearDeathExplosionBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new ReshapingBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new CounterPulseBehavior());
        DroneSpecialBehaviorManager.getInstance().registerBehavior(new SelfDestructBehavior());
    }
}
