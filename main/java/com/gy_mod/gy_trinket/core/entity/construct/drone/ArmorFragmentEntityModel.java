package com.gy_mod.gy_trinket.core.entity.construct.drone;

// 由 tools/model_convert/geo2vanilla.py 从 geo/armor_fragment.geo.json 自动转换
// 口径：Minecraft 1.20.1 原生实体模型（y_v = 24 - y_geo，x/y 旋转取反）
// 不要手改：改 geo 数据或转换脚本后重新生成。

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

public class ArmorFragmentEntityModel extends EntityModel<ArmorShardEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation("gytrinket", "armor_fragment"), "main");

    private final ModelPart bone3;

    public ArmorFragmentEntityModel(ModelPart root) {
        this.bone3 = root.getChild("bone3");
    }

    /** 根部件（渲染器可改写其姿态 / 缩放做程序化动画） */
    public ModelPart getRootPart() {
        return this.bone3;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone3_def = partdefinition.addOrReplaceChild("bone3", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 23.5F, -0.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone2_def = bone3_def.addOrReplaceChild("bone2", CubeListBuilder.create()
			.texOffs(4, 0).addBox(-1.0F, -1.0F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(8, 0).addBox(-1.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(12, 0).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 2).addBox(-2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 1.5F, 1.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone_def = bone3_def.addOrReplaceChild("bone", CubeListBuilder.create()
			.texOffs(4, 2).addBox(0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(8, 2).addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(12, 2).addBox(0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 4).addBox(1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -1.5F, -1.5F, 0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 16, 16);
    }

    @Override
    public void setupAnim(ArmorShardEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 纯几何模型：姿态动画由渲染器按实体状态直接改写 ModelPart 完成
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.bone3.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
