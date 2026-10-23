package com.gy_mod.gy_trinket.core.world;

import com.gy_mod.gy_trinket.gytrinket;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
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
            DeferredRegister.create(Registries.STRUCTURE_TYPE, gytrinket.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, gytrinket.MODID);

    /** 残破高塔的结构类型（对应 data/gytrinket/worldgen/structure/ruined_tower.json） */
    public static final RegistryObject<StructureType<?>> RUINED_TOWER =
            registerStructure("ruined_tower", RuinedTowerStructure.CODEC);

    /** 手工塔结构类型（复用残塔结构生成器） */
    public static final RegistryObject<StructureType<?>> ARCANE_TOWER =
            registerStructure("arcane_tower", RuinedTowerStructure.CODEC);

    /** 模板片段（直接粘贴 .nbt） */
    public static final RegistryObject<StructurePieceType> ARCANE_TOWER_PIECE =
            STRUCTURE_PIECE_TYPES.register("arcane_tower",
                    () -> (context, tag) -> new ArcaneTowerPiece(context.structureTemplateManager(), tag));

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
