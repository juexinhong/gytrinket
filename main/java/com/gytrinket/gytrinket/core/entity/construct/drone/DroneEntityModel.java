package com.gytrinket.gytrinket.core.entity.construct.drone;

// 由 tools/model_convert/geo2vanilla.py 从 geo/drone.geo.json 自动转换
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

public class DroneEntityModel extends EntityModel<DroneConstructEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation("gytrinket", "drone"), "main");

    private final ModelPart bone7;

    public DroneEntityModel(ModelPart root) {
        this.bone7 = root.getChild("bone7");
    }

    /** 根部件（渲染器可改写其姿态 / 缩放做程序化动画） */
    public ModelPart getRootPart() {
        return this.bone7;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone7_def = partdefinition.addOrReplaceChild("bone7", CubeListBuilder.create()
			.texOffs(42, 19).addBox(-5.6605F, -0.3704F, 1.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 0).addBox(-0.6605F, -1.3704F, -2.0802F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
			.texOffs(16, 0).addBox(-2.6605F, -1.3704F, -2.0802F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(46, 19).addBox(3.3395F, -0.3704F, -3.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(44, 13).addBox(2.3395F, -0.3704F, -4.0803F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(36, 19).addBox(1.3395F, -2.3704F, -4.0803F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(50, 13).addBox(3.3395F, -2.3704F, -3.0802F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(50, 19).addBox(3.3395F, -0.3704F, 3.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(54, 19).addBox(2.3395F, -0.3704F, 2.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(58, 19).addBox(-4.6605F, -0.3704F, -4.0803F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 21).addBox(-3.6605F, -0.3704F, -3.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 13).addBox(-1.6605F, -2.3704F, -1.0802F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(12, 13).addBox(-1.6605F, 1.6296F, -1.0802F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(40, 13).addBox(-1.6605F, -1.3704F, 0.9197F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(4, 21).addBox(-5.6605F, -0.3704F, -3.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(8, 21).addBox(2.3395F, -0.3704F, 4.9197F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(12, 21).addBox(4.3395F, -0.3704F, 2.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(16, 21).addBox(-3.6605F, -0.3704F, -5.0803F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(20, 21).addBox(4.3395F, -0.3704F, -2.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(24, 21).addBox(1.3395F, -0.3704F, -5.0803F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(28, 21).addBox(-2.6605F, -0.3704F, 4.9197F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1605F, 21.3704F, 0.0803F, 0.0F, -2.3562F, 0.0F));
		PartDefinition bone_def = bone7_def.addOrReplaceChild("bone", CubeListBuilder.create()
			.texOffs(0, 17).addBox(-2.0F, 0.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(10, 17).addBox(-2.0F, -0.5F, 0.1667F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(20, 17).addBox(-2.0F, -1.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6605F, 0.1296F, -4.2469F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone4_def = bone7_def.addOrReplaceChild("bone4", CubeListBuilder.create()
			.texOffs(30, 17).addBox(4.0F, -1.0F, -4.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(20, 19).addBox(6.0F, -1.0F, 2.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(26, 0).addBox(9.0F, -1.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(24, 13).addBox(3.0F, -1.0F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.6605F, -1.3704F, 0.9197F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone3_def = bone7_def.addOrReplaceChild("bone3", CubeListBuilder.create()
			.texOffs(36, 0).addBox(-1.1667F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(46, 0).addBox(-0.1667F, -1.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(0, 8).addBox(-0.1667F, 0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5062F, 0.1296F, 0.9197F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone5_def = bone7_def.addOrReplaceChild("bone5", CubeListBuilder.create()
			.texOffs(40, 17).addBox(-2.0F, -0.5F, -4.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(28, 19).addBox(0.0F, -0.5F, 2.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(10, 8).addBox(3.0F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(32, 13).addBox(-3.0F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6605F, 2.1296F, 0.9197F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone2_def = bone7_def.addOrReplaceChild("bone2", CubeListBuilder.create()
			.texOffs(50, 17).addBox(-2.0F, 0.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 19).addBox(-2.0F, -0.5F, 0.1667F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(10, 19).addBox(-2.0F, -1.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3395F, 0.1296F, 5.7531F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone6_def = bone7_def.addOrReplaceChild("bone6", CubeListBuilder.create()
			.texOffs(20, 8).addBox(-1.1667F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(30, 8).addBox(-1.1667F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(40, 8).addBox(-0.1667F, -1.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(50, 8).addBox(-0.1667F, 0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.4938F, 0.1296F, -0.0803F, 0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(DroneConstructEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 纯几何模型：姿态动画由渲染器按实体状态直接改写 ModelPart 完成
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.bone7.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
