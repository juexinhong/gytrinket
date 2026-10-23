package com.gytrinket.gytrinket.core.entity.meteor;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 陨石实体注册。
 * <p>
 * 四个 {@link EntityType} 共用 {@link MeteorEntity} 类，尺寸和数值由构造时传入的
 * {@link MeteorSize} 决定。这样碰撞箱是注册期固定的，不依赖同步顺序。
 */
public final class MeteorEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE, gytrinket.MODID
    );

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR_SMALL =
            register("meteor_small", MeteorSize.SMALL);
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR_MEDIUM =
            register("meteor_medium", MeteorSize.MEDIUM);
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR_LARGE =
            register("meteor_large", MeteorSize.LARGE);
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR_HUGE =
            register("meteor_huge", MeteorSize.HUGE);

    private MeteorEntities() {
    }

    private static DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> register(String name, MeteorSize size) {
        return ENTITIES.register(name, () -> EntityType.Builder
                .<MeteorEntity>of((type, level) -> new MeteorEntity(type, level, size), MobCategory.MISC)
                .sized(size.hitboxDiameter(), size.hitboxDiameter())
                .fireImmune()
                .clientTrackingRange(20)
                .updateInterval(1)
                .build(name));
    }

    public static MeteorEntity create(MeteorSize size, Level level) {
        EntityType<MeteorEntity> type = switch (size) {
            case SMALL -> METEOR_SMALL.get();
            case MEDIUM -> METEOR_MEDIUM.get();
            case LARGE -> METEOR_LARGE.get();
            case HUGE -> METEOR_HUGE.get();
        };
        MeteorEntity entity = type.create(level);
        if (entity == null) {
            throw new IllegalStateException("Failed to create meteor entity for size " + size);
        }
        return entity;
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
