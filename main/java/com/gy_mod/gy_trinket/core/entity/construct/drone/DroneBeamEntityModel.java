package com.gy_mod.gy_trinket.core.entity.construct.drone;

// 由 tools/model_convert/geo2vanilla.py 从 geo/drone_beam.geo.json 自动转换
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
import net.minecraft.client.renderer.RenderType;

public class DroneBeamEntityModel extends EntityModel<DroneBeamProjectile> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation("gytrinket", "drone_beam"), "main");

    private final ModelPart bone;

    public DroneBeamEntityModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.bone = root.getChild("bone");
    }

    /** 根部件（渲染器可改写其姿态 / 缩放做程序化动画） */
    public ModelPart getRootPart() {
        return this.bone;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone_def = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-1.0F, -1.0F, -320.0F, 2.0F, 2.0F, 320.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 23.0F, 160.0F, 0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 1024, 512);
    }

    @Override
    public void setupAnim(DroneBeamProjectile entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // 纯几何模型：姿态动画由渲染器按实体状态直接改写 ModelPart 完成
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
