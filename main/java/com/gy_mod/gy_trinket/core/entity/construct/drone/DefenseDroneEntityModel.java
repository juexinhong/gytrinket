package com.gy_mod.gy_trinket.core.entity.construct.drone;

// 由 tools/model_convert/geo2vanilla.py 从 geo/defense_drone.geo.json 自动转换
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

public class DefenseDroneEntityModel extends EntityModel<DroneConstructEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation("gytrinket", "defense_drone"), "main");

    private final ModelPart root;

    public DefenseDroneEntityModel(ModelPart root) {
        this.root = root.getChild("root");
    }

    /** 根部件（渲染器可改写其姿态 / 缩放做程序化动画） */
    public ModelPart getRootPart() {
        return this.root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root_def = partdefinition.addOrReplaceChild("root", CubeListBuilder.create()
			.texOffs(44, 17).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(0, 34).addBox(-1.0F, -4.0F, 2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(54, 17).addBox(-3.0F, -4.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(0, 24).addBox(1.0F, -4.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(10, 24).addBox(3.0F, -6.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(20, 24).addBox(-5.0F, -6.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(40, 38).addBox(-1.0F, -6.0F, 2.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(46, 38).addBox(-1.0F, -8.0F, 2.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(24, 38).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(32, 38).addBox(-1.0F, -9.0F, -3.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(52, 38).addBox(-1.0F, -6.0F, -2.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(58, 38).addBox(-1.0F, -8.0F, -2.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(8, 34).addBox(-3.0F, -6.0F, 2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(16, 34).addBox(1.0F, -6.0F, 2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(0, 38).addBox(1.0F, -8.0F, 2.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(6, 38).addBox(-2.0F, -8.0F, 2.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(12, 38).addBox(1.0F, -8.0F, -3.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(18, 38).addBox(-2.0F, -8.0F, -3.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(46, 29).addBox(-3.0F, -9.0F, -1.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(50, 29).addBox(2.0F, -9.0F, -1.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(28, 17).addBox(-4.0F, -11.0F, -1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(32, 17).addBox(3.0F, -11.0F, -1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(36, 17).addBox(-4.0F, -11.0F, 1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(54, 29).addBox(2.0F, -9.0F, 1.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(58, 29).addBox(-3.0F, -9.0F, 1.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(40, 17).addBox(3.0F, -11.0F, 1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(24, 34).addBox(-3.0F, -6.0F, -3.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(32, 34).addBox(1.0F, -6.0F, -3.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(40, 34).addBox(-1.0F, -4.0F, -3.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(30, 24).addBox(5.0F, -4.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(40, 24).addBox(-7.0F, -4.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(50, 24).addBox(7.0F, -6.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(0, 29).addBox(-9.0F, -6.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(10, 29).addBox(9.0F, -6.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(20, 29).addBox(-11.0F, -6.0F, -1.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(46, 0).addBox(11.0F, -7.0F, -2.0F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
			.texOffs(12, 17).addBox(11.0F, -11.0F, -1.0F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(30, 29).addBox(12.0F, -7.0F, -1.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(0, 42).addBox(12.0F, -10.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(48, 34).addBox(12.0F, -8.0F, -1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(50, 42).addBox(13.0F, -7.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 17).addBox(-12.0F, -7.0F, -2.0F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
			.texOffs(20, 17).addBox(-12.0F, -11.0F, -1.0F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(38, 29).addBox(-13.0F, -7.0F, -1.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(4, 42).addBox(-13.0F, -10.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(56, 34).addBox(-13.0F, -8.0F, -1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(54, 42).addBox(-14.0F, -7.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(8, 42).addBox(7.0F, -4.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(14, 42).addBox(-9.0F, -4.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(20, 42).addBox(3.0F, -4.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(58, 42).addBox(3.0F, -3.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 45).addBox(-4.0F, -3.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(26, 42).addBox(-5.0F, -4.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(32, 42).addBox(5.0F, -5.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(38, 42).addBox(-7.0F, -5.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(44, 42).addBox(-1.0F, -4.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 0).addBox(-11.0F, -21.0F, 0.0F, 22.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F));

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
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
