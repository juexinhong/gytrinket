// 由 tools/model_convert/geo2vanilla.py 从 geo/drone_beam.geo.json 自动转换
// 口径：Minecraft 1.21.1 原生实体模型（y_v = 24 - y_geo，x/y 旋转取反）
// @package com.gytrinket.gytrinket.core.entity.construct.drone
// @class DroneBeamEntityModel
// @entity com.gytrinket.gytrinket.core.entity.construct.drone.DroneBeamProjectile
// @render-type translucent

public class drone_beam<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "drone_beam"), "main");
	private final ModelPart bone;

	public drone_beam(ModelPart root) {
		this.bone = root.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone_def = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-1.0F, -1.0F, -320.0F, 2.0F, 2.0F, 320.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 23.0F, 160.0F, 0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 1024, 512);
	}
}
