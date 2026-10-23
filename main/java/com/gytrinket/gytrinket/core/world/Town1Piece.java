package com.gytrinket.gytrinket.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 城镇 1 的模板片段：直接粘贴 {@code data/gytrinket/structure/town1.nbt}。
 * <p>
 * 模板尺寸 291×253×291，远超 jigsaw 的 128 格上限，因此与 {@link ArcaneTowerPiece}
 * 一样使用 {@link TemplateStructurePiece}；模板已剔除空气，不会覆盖孤岛地形。
 */
public class Town1Piece extends TemplateStructurePiece {

    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath("gytrinket", "town1");

    public Town1Piece(StructureTemplateManager templateManager, BlockPos pos) {
        super(ModWorldgen.TOWN1_PIECE.get(), 0, templateManager, TEMPLATE,
                "town1", settings(), pos);
    }

    public Town1Piece(StructureTemplateManager templateManager, CompoundTag tag) {
        super(ModWorldgen.TOWN1_PIECE.get(), tag, templateManager, ignored -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // 模板里没有数据标记
    }
}
