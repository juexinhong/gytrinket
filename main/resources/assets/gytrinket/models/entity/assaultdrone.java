// 由 tools/model_convert/geo2vanilla.py 从 geo/assaultdrone.geo.json 自动转换
// 口径：Minecraft 1.21.1 原生实体模型（y_v = 24 - y_geo，x/y 旋转取反）
// @package com.gytrinket.gytrinket.core.entity.construct.drone
// @class AssaultDroneEntityModel
// @entity com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity
// @render-type cutout

public class assaultdrone<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "assaultdrone"), "main");
	private final ModelPart bone7;

	public assaultdrone(ModelPart root) {
		this.bone7 = root.getChild("bone7");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone7_def = partdefinition.addOrReplaceChild("bone7", CubeListBuilder.create()
			.texOffs(0, 22).addBox(-6.6605F, -0.3704F, 1.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 0).addBox(-0.6605F, -1.3704F, -2.0802F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
			.texOffs(28, 0).addBox(-2.6605F, -1.3704F, -2.0802F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(4, 22).addBox(3.3395F, -0.3704F, -3.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 17).addBox(2.3395F, -0.3704F, -4.0803F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(46, 20).addBox(1.3395F, -2.3704F, -4.0803F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(6, 17).addBox(3.3395F, -2.3704F, -3.0802F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(8, 22).addBox(5.3395F, -0.3704F, 2.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(12, 22).addBox(-4.6605F, -2.3704F, 0.9197F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(16, 22).addBox(-4.6605F, 1.6296F, 0.9197F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(20, 22).addBox(-1.6605F, -2.3704F, 3.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(24, 22).addBox(-1.6605F, 1.6296F, 3.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(52, 20).addBox(3.3395F, -0.3704F, 3.9198F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(24, 13).addBox(3.8395F, -0.8704F, 5.4197F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(32, 13).addBox(-7.1605F, -0.8704F, -5.5803F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(28, 22).addBox(2.3395F, -0.3704F, 2.9198F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(32, 22).addBox(-3.5605F, -0.3704F, -6.0803F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(12, 17).addBox(-4.5605F, -0.3704F, -5.0803F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(36, 22).addBox(-3.6605F, -0.3704F, -3.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 13).addBox(-1.6605F, -2.3704F, -1.0802F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(12, 13).addBox(-1.6605F, 1.6296F, -1.0802F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(56, 13).addBox(-1.6605F, -1.3704F, 0.9197F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(58, 20).addBox(-7.6605F, -0.3704F, -3.0802F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(18, 17).addBox(2.3395F, -0.3704F, 5.9197F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(40, 22).addBox(4.3395F, -0.3704F, -2.0802F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(44, 22).addBox(1.3395F, -0.3704F, -5.0803F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(48, 22).addBox(-2.6605F, -0.3704F, 5.9197F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(16, 0).addBox(-7.6605F, -1.3704F, -2.0802F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(38, 0).addBox(-1.6605F, -1.3704F, 5.9197F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.1605F, 21.3704F, 0.0803F, 0.0F, -2.3562F, 0.0F));
		PartDefinition bone_def = bone7_def.addOrReplaceChild("bone", CubeListBuilder.create()
			.texOffs(24, 17).addBox(-2.0F, 0.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(34, 17).addBox(-2.0F, -0.5F, 0.1667F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(44, 17).addBox(-2.0F, -1.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6605F, 0.1296F, -4.2469F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone4_def = bone7_def.addOrReplaceChild("bone4", CubeListBuilder.create()
			.texOffs(54, 17).addBox(4.0F, -1.0F, -4.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(30, 20).addBox(6.0F, -1.0F, 2.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(50, 0).addBox(9.0F, -1.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(40, 13).addBox(3.0F, -1.0F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.6605F, -1.3704F, 0.9197F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone3_def = bone7_def.addOrReplaceChild("bone3", CubeListBuilder.create()
			.texOffs(0, 8).addBox(-1.1667F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(10, 8).addBox(-0.1667F, -1.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(20, 8).addBox(-0.1667F, 0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5062F, 0.1296F, 0.9197F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone5_def = bone7_def.addOrReplaceChild("bone5", CubeListBuilder.create()
			.texOffs(0, 20).addBox(-2.0F, -0.5F, -4.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(38, 20).addBox(0.0F, -0.5F, 2.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(30, 8).addBox(3.0F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(48, 13).addBox(-3.0F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6605F, 2.1296F, 0.9197F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone2_def = bone7_def.addOrReplaceChild("bone2", CubeListBuilder.create()
			.texOffs(10, 20).addBox(0.0F, -0.5F, 2.1667F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(20, 20).addBox(-2.0F, -0.5F, -0.8333F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3395F, 0.1296F, 5.7531F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone6_def = bone7_def.addOrReplaceChild("bone6", CubeListBuilder.create()
			.texOffs(40, 8).addBox(-3.1667F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(50, 8).addBox(-0.1667F, -0.5F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.4938F, 0.1296F, -0.0803F, 0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}
}
