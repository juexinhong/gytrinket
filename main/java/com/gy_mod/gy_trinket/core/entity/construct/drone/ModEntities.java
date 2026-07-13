package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.ExplosiveProjectile;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, gytrinket.MODID
    );

    public static final RegistryObject<EntityType<DroneConstructEntity>> DRONE_CONSTRUCT = ENTITIES.register(
            "drone_construct",
            () -> EntityType.Builder.<DroneConstructEntity>of(DroneConstructEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 0.8f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("drone_construct")
    );

    public static final RegistryObject<EntityType<DroneBullet>> DRONE_BULLET = ENTITIES.register(
            "drone_bullet",
            () -> EntityType.Builder.<DroneBullet>of(DroneBullet::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("drone_bullet")
    );

    public static final RegistryObject<EntityType<DroneBeamProjectile>> DRONE_BEAM = ENTITIES.register(
            "drone_beam",
            () -> EntityType.Builder.<DroneBeamProjectile>of(DroneBeamProjectile::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("drone_beam")
    );

    public static final RegistryObject<EntityType<ArmorShardEntity>> ARMOR_SHARD = ENTITIES.register(
            "armor_shard",
            () -> EntityType.Builder.<ArmorShardEntity>of(ArmorShardEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("armor_shard")
    );

    public static final RegistryObject<EntityType<SwarmConstructEntity>> SWARM_CONSTRUCT = ENTITIES.register(
            "swarm_construct",
            () -> EntityType.Builder.<SwarmConstructEntity>of(SwarmConstructEntity::new, MobCategory.CREATURE)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("swarm_construct")
    );

    public static final RegistryObject<EntityType<WingmanConstructEntity>> WINGMAN_CONSTRUCT = ENTITIES.register(
            "wingman_construct",
            () -> EntityType.Builder.<WingmanConstructEntity>of(WingmanConstructEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 0.8f)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("wingman_construct")
    );

    public static final RegistryObject<EntityType<ExplosiveProjectile>> EXPLOSIVE_PROJECTILE = ENTITIES.register(
            "explosive_projectile",
            () -> EntityType.Builder.<ExplosiveProjectile>of(ExplosiveProjectile::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("explosive_projectile")
    );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}