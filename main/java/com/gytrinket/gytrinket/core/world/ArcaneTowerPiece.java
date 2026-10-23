package com.gytrinket.gytrinket.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 直接粘贴手工塔模型（data/gytrinket/structure/arcane_tower.nbt）的结构片段。
 * <p>
 * 用模板片段而不是 jigsaw，是因为 jigsaw 会给结构加 128 格尺寸上限，
 * 而这座塔高 253 格；模板片段本身没有这个限制。
 */
public class ArcaneTowerPiece extends TemplateStructurePiece {

    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath("gytrinket", "arcane_tower");

    public ArcaneTowerPiece(StructureTemplateManager templateManager, BlockPos pos) {
        super(ModWorldgen.ARCANE_TOWER_PIECE.get(), 0, templateManager, TEMPLATE,
                "arcane_tower", settings(), pos);
    }

    public ArcaneTowerPiece(StructureTemplateManager templateManager, CompoundTag tag) {
        super(ModWorldgen.ARCANE_TOWER_PIECE.get(), tag, templateManager, ignored -> settings());
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
