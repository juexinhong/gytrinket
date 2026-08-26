package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.17 or later with Mojang mappings

/**
 * 僚机构造体标准实体模型（Blockbench 导出，替代原 GeckoLib GeoModel）。
 */
public class WingmanEntityModel<T extends Entity> extends EntityModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("gytrinket", "wingman"), "main");
    private final ModelPart bone;

    public WingmanEntityModel(ModelPart root) {
        this.bone = root.getChild("bone");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(-7.3684F, -1.4974F, -9.6579F, 11.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(12, 27).addBox(3.6316F, -1.4974F, -9.6579F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(20, 27).addBox(7.6316F, -1.4974F, -5.6579F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 14).addBox(-8.3684F, -0.4974F, -10.6579F, 10.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(24, 25).addBox(-2.8684F, -0.4974F, -6.6579F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(8, 36).addBox(3.6316F, 0.5026F, 3.3421F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(13, 23).addBox(3.6316F, 0.5026F, 4.3421F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(28, 6).addBox(3.6316F, -0.4974F, 3.3421F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(34, 33).addBox(4.6316F, -0.4974F, 7.3421F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 35).addBox(3.6316F, -0.4974F, 6.3421F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(8, 36).addBox(3.6316F, -1.4974F, 3.3421F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(1, 37).addBox(3.6316F, -1.4974F, 4.3421F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(22, 17).addBox(-4.3684F, 0.5026F, -4.6579F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(24, 17).addBox(-7.3684F, 0.5026F, -5.6579F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 33).addBox(-6.3684F, -0.4974F, -5.6579F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(4, 35).addBox(-8.3684F, -0.4974F, -5.6579F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(28, 10).addBox(-7.3684F, -0.4974F, -4.6579F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 37).addBox(-4.3684F, -1.4974F, -4.6579F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(24, 20).addBox(-7.3684F, -1.4974F, -5.6579F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 16).addBox(-7.3684F, -1.4974F, -3.6579F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 23).addBox(-0.3684F, -1.4974F, 5.3421F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 41).addBox(0.6316F, -2.4974F, 5.3421F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(5, 41).addBox(-6.3684F, -2.4974F, -2.6579F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(9, 40).addBox(-6.3684F, 1.5026F, -2.6579F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 43).addBox(0.6316F, 1.5026F, 5.3421F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(8, 28).addBox(-1.3684F, -0.4974F, 6.3421F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(34, 31).addBox(-7.3684F, -0.4974F, 0.3421F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(12, 16).addBox(-3.3684F, -1.4974F, 0.3421F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(10, 36).addBox(-2.3684F, -2.0974F, 0.3421F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(18, 36).addBox(-2.3684F, 1.0026F, 0.3421F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(22, 32).addBox(0.6316F, -0.4974F, 0.3421F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 28).addBox(-0.3684F, -1.4974F, 2.3421F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(28, 31).addBox(-0.3684F, -1.4974F, 0.3421F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 34).addBox(-2.3684F, -0.4974F, -1.6579F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(24, 36).addBox(0.6316F, -0.4974F, -1.6579F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(16, 32).addBox(-2.3684F, -1.4974F, -0.6579F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(28, 0).addBox(-4.3684F, -1.4974F, -1.6579F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(28, 27).addBox(-4.3684F, -0.4974F, 0.3421F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(8, 32).addBox(-3.3684F, -0.4974F, 3.3421F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0316F, 22.4974F, 1.6579F, 0.0F, 0.7854F, 0.0F));

        PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(24, 23).addBox(-4.0F, -1.0F, -1.0F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6316F, 0.5026F, 1.8421F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r2 = bone.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 12).addBox(-9.0F, -1.0F, -1.0F, 10.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.6316F, 0.5026F, 7.3421F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r3 = bone.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 6).addBox(-10.0F, -3.0F, -1.0F, 11.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.6316F, 1.5026F, 6.3421F, 0.0F, -1.5708F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
