// 由 tools/model_convert/geo2vanilla.py 从 geo/armor_fragment.geo.json 自动转换
// 口径：Minecraft 1.21.1 原生实体模型（y_v = 24 - y_geo，x/y 旋转取反）
// @package com.gytrinket.gytrinket.core.entity.construct.drone
// @class ArmorFragmentEntityModel
// @entity com.gytrinket.gytrinket.core.entity.construct.drone.ArmorShardEntity
// @render-type cutout

public class armor_fragment<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "armor_fragment"), "main");
	private final ModelPart bone3;

	public armor_fragment(ModelPart root) {
		this.bone3 = root.getChild("bone3");
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
}
