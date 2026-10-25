package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.gytrinket;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 战场维度的世界生成注册：残塔结构类型与结构片段类型。
 */
public final class ModWorldgen {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registry.STRUCTURE_TYPE_REGISTRY, gytrinket.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registry.STRUCTURE_PIECE_REGISTRY, gytrinket.MODID);

    /** 残破高塔的结构类型（对应 data/gytrinket/worldgen/structure/ruined_tower.json） */
    public static final RegistryObject<StructureType<?>> RUINED_TOWER =
            registerStructure("ruined_tower", RuinedTowerStructure.CODEC);

    /** 城镇 1：孤岛维度专属结构（只在原点区块生成一次） */
    public static final RegistryObject<StructureType<?>> TOWN1 =
            registerStructure("town1", Town1Structure.CODEC);

    /** 城镇 1 的模板片段 */
    public static final RegistryObject<StructurePieceType> TOWN1_PIECE =
            STRUCTURE_PIECE_TYPES.register("town1",
                    () -> (context, tag) -> new Town1Piece(context.structureTemplateManager(), tag));

    /** 残塔的单个结构片段 */
    public static final RegistryObject<StructurePieceType> RUINED_TOWER_PIECE =
            STRUCTURE_PIECE_TYPES.register("ruined_tower",
                    () -> (context, tag) -> new RuinedTowerPiece(tag));

    private ModWorldgen() {
    }

    /**
     * 注册结构类型。通配符泛型的 DeferredRegister 无法直接接受 lambda 推断，
     * 这里用具体泛型参数中转一次。
     */
    private static <S extends Structure> RegistryObject<StructureType<?>> registerStructure(
            String name, Codec<S> codec) {
        return STRUCTURE_TYPES.register(name, () -> new StructureType<S>() {
            @Override
            public Codec<S> codec() {
                return codec;
            }
        });
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
    }
}
