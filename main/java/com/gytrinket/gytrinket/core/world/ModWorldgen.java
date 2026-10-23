package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 战场维度的世界生成注册：残塔结构类型与结构片段类型。
 */
public final class ModWorldgen {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, gytrinket.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, gytrinket.MODID);

    /** 残破高塔的结构类型（对应 data/gytrinket/worldgen/structure/ruined_tower.json） */
    public static final Supplier<StructureType<RuinedTowerStructure>> RUINED_TOWER =
            STRUCTURE_TYPES.register("ruined_tower", () -> () -> RuinedTowerStructure.CODEC);

    /** 手工塔结构类型 */
    public static final Supplier<StructureType<RuinedTowerStructure>> ARCANE_TOWER =
            STRUCTURE_TYPES.register("arcane_tower", () -> () -> RuinedTowerStructure.CODEC);

    /** 模板片段（直接粘贴 .nbt） */
    public static final Supplier<StructurePieceType> ARCANE_TOWER_PIECE =
            STRUCTURE_PIECE_TYPES.register("arcane_tower",
                    () -> (context, tag) -> new ArcaneTowerPiece(context.structureTemplateManager(), tag));

    /** 城镇 1：孤岛维度专属结构（只在原点区块生成一次） */
    public static final Supplier<StructureType<Town1Structure>> TOWN1 =
            STRUCTURE_TYPES.register("town1", () -> () -> Town1Structure.CODEC);

    /** 城镇 1 的模板片段 */
    public static final Supplier<StructurePieceType> TOWN1_PIECE =
            STRUCTURE_PIECE_TYPES.register("town1",
                    () -> (context, tag) -> new Town1Piece(context.structureTemplateManager(), tag));

    /** 残塔的单个结构片段 */
    public static final Supplier<StructurePieceType> RUINED_TOWER_PIECE =
            STRUCTURE_PIECE_TYPES.register("ruined_tower",
                    () -> (StructurePieceType) (context, tag) -> new RuinedTowerPiece(tag));

    private ModWorldgen() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
    }
}
