package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.ExplosiveProjectile;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE, gytrinket.MODID
    );

    public static final DeferredHolder<EntityType<?>, EntityType<DroneConstructEntity>> DRONE_CONSTRUCT = ENTITIES.register(
            "drone_construct",
            () -> EntityType.Builder.<DroneConstructEntity>of(DroneConstructEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 0.8f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("drone_construct")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<DroneBullet>> DRONE_BULLET = ENTITIES.register(
            "drone_bullet",
            () -> EntityType.Builder.<DroneBullet>of(DroneBullet::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("drone_bullet")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<DroneBeamProjectile>> DRONE_BEAM = ENTITIES.register(
            "drone_beam",
            () -> EntityType.Builder.<DroneBeamProjectile>of(DroneBeamProjectile::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("drone_beam")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ArmorShardEntity>> ARMOR_SHARD = ENTITIES.register(
            "armor_shard",
            () -> EntityType.Builder.<ArmorShardEntity>of(ArmorShardEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("armor_shard")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<WingmanConstructEntity>> WINGMAN_CONSTRUCT = ENTITIES.register(
            "wingman_construct",
            () -> EntityType.Builder.<WingmanConstructEntity>of(WingmanConstructEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 0.8f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("wingman_construct")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ExplosiveProjectile>> EXPLOSIVE_PROJECTILE = ENTITIES.register(
            "explosive_projectile",
            () -> EntityType.Builder.<ExplosiveProjectile>of(ExplosiveProjectile::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("explosive_projectile")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<SwarmConstructEntity>> SWARM_CONSTRUCT = ENTITIES.register(
            "swarm_construct",
            () -> EntityType.Builder.<SwarmConstructEntity>of(SwarmConstructEntity::new, MobCategory.CREATURE)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("swarm_construct")
    );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}