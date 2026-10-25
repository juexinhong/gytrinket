// 由 tools/model_convert/geo2vanilla.py 从 geo/swarm.geo.json 自动转换
// 口径：Minecraft 1.21.1 原生实体模型（y_v = 24 - y_geo，x/y 旋转取反）
// @package com.gytrinket.gytrinket.core.entity.construct.swarm
// @class SwarmEntityModel
// @entity com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmConstructEntity
// @render-type cutout

public class swarm<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "swarm"), "main");
	private final ModelPart bone;

	public swarm(ModelPart root) {
		this.bone = root.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone_def = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-3.5F, -4.5F, -7.5F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(16, 0).addBox(-3.0F, -4.0F, -4.0F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 24.5F, 3.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone4_def = bone_def.addOrReplaceChild("bone4", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone4_cube1 = bone4_def.addOrReplaceChild("cube_r1", CubeListBuilder.create()
				.texOffs(0, 8).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(10, 14).addBox(-1.0F, -1.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(20, 20).addBox(-1.0F, -2.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1745F, -0.1745F, 0.0F));
		PartDefinition bone2_def = bone_def.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.0F, -3.0F, -0.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone2_cube1 = bone2_def.addOrReplaceChild("cube_r1", CubeListBuilder.create()
				.texOffs(20, 14).addBox(0.0F, -2.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(10, 8).addBox(-1.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(24, 20).addBox(0.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1745F, 0.1745F, 0.0F));
		PartDefinition bone5_def = bone_def.addOrReplaceChild("bone5", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone5_cube1 = bone5_def.addOrReplaceChild("cube_r1", CubeListBuilder.create()
				.texOffs(0, 20).addBox(0.0F, -1.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(20, 8).addBox(-1.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(28, 20).addBox(0.0F, -2.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1745F, 0.1745F, 0.0F));
		PartDefinition bone3_def = bone_def.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -3.0F, -0.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone3_cube1 = bone3_def.addOrReplaceChild("cube_r1", CubeListBuilder.create()
				.texOffs(0, 14).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(10, 20).addBox(-1.0F, -2.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(0, 25).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1745F, -0.1745F, 0.0F));

		return LayerDefinition.create(meshdefinition, 32, 32);
	}
}
