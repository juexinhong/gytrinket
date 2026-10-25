package com.gy_mod.gy_trinket.client.model;

// 由 tools/model_convert/geo2vanilla.py 从 geo/light_point_core_block_gy.geo.json 自动转换。
// 口径：Minecraft 1.20.1 原生模型（y_v = 24 - y_geo，x/y 旋转取反）。
// 不要手改：改 geo 数据或转换脚本后重新生成。

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class LightPointCoreBlockModel extends Model {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("gytrinket", "light_point_core_block_gy"), "main");

	/** 骨骼 {@code root} */
	public final ModelPart root;
	/** 骨骼 {@code bone25} */
	public final ModelPart bone25;
	/** 骨骼 {@code bone23} */
	public final ModelPart bone23;
	/** 骨骼 {@code bone22} */
	public final ModelPart bone22;
	/** 骨骼 {@code bone24} */
	public final ModelPart bone24;
	/** 骨骼 {@code bone9} */
	public final ModelPart bone9;
	/** 骨骼 {@code bone10} */
	public final ModelPart bone10;
	/** 骨骼 {@code bone13} */
	public final ModelPart bone13;
	/** 骨骼 {@code bone12} */
	public final ModelPart bone12;
	/** 骨骼 {@code bone30} */
	public final ModelPart bone30;
	/** 骨骼 {@code bone31} */
	public final ModelPart bone31;
	/** 骨骼 {@code bone32} */
	public final ModelPart bone32;
	/** 骨骼 {@code bone33} */
	public final ModelPart bone33;
	/** 骨骼 {@code bone34} */
	public final ModelPart bone34;
	/** 骨骼 {@code bone35} */
	public final ModelPart bone35;
	/** 骨骼 {@code bone36} */
	public final ModelPart bone36;
	/** 骨骼 {@code bone37} */
	public final ModelPart bone37;
	/** 骨骼 {@code bone8} */
	public final ModelPart bone8;
	/** 骨骼 {@code bone42} */
	public final ModelPart bone42;
	/** 骨骼 {@code bone43} */
	public final ModelPart bone43;
	/** 骨骼 {@code bone44} */
	public final ModelPart bone44;
	/** 骨骼 {@code bone45} */
	public final ModelPart bone45;
	/** 骨骼 {@code bone46} */
	public final ModelPart bone46;
	/** 骨骼 {@code bone47} */
	public final ModelPart bone47;
	/** 骨骼 {@code bone48} */
	public final ModelPart bone48;
	/** 骨骼 {@code bone49} */
	public final ModelPart bone49;
	/** 骨骼 {@code bone50} */
	public final ModelPart bone50;
	/** 骨骼 {@code bone7} */
	public final ModelPart bone7;
	/** 骨骼 {@code bone5} */
	public final ModelPart bone5;
	/** 骨骼 {@code bone4} */
	public final ModelPart bone4;
	/** 骨骼 {@code bone6} */
	public final ModelPart bone6;
	/** 骨骼 {@code bone3} */
	public final ModelPart bone3;
	/** 骨骼 {@code bone} */
	public final ModelPart bone;
	/** 骨骼 {@code bone18} */
	public final ModelPart bone18;
	/** 骨骼 {@code bone19} */
	public final ModelPart bone19;
	/** 骨骼 {@code bone20} */
	public final ModelPart bone20;
	/** 骨骼 {@code bone21} */
	public final ModelPart bone21;
	/** 骨骼 {@code bone2} */
	public final ModelPart bone2;
	/** 骨骼 {@code bone14} */
	public final ModelPart bone14;
	/** 骨骼 {@code bone15} */
	public final ModelPart bone15;
	/** 骨骼 {@code bone16} */
	public final ModelPart bone16;
	/** 骨骼 {@code bone17} */
	public final ModelPart bone17;
	/** 骨骼 {@code bone11} */
	public final ModelPart bone11;
	/** 骨骼 {@code bone26} */
	public final ModelPart bone26;
	/** 骨骼 {@code bone27} */
	public final ModelPart bone27;
	/** 骨骼 {@code bone28} */
	public final ModelPart bone28;
	/** 骨骼 {@code bone29} */
	public final ModelPart bone29;
	/** 骨骼 {@code bone38} */
	public final ModelPart bone38;
	/** 骨骼 {@code bone39} */
	public final ModelPart bone39;
	/** 骨骼 {@code bone40} */
	public final ModelPart bone40;
	/** 骨骼 {@code bone41} */
	public final ModelPart bone41;
	/** 骨骼 {@code bone51} */
	public final ModelPart bone51;

	public LightPointCoreBlockModel(ModelPart rootPart, java.util.function.Function<ResourceLocation, RenderType> renderType) {
		super(renderType);
		this.root = rootPart.getChild("root");
		this.bone25 = this.root.getChild("bone25");
		this.bone23 = this.root.getChild("bone23");
		this.bone22 = this.root.getChild("bone22");
		this.bone24 = this.root.getChild("bone24");
		this.bone9 = this.root.getChild("bone9");
		this.bone10 = this.root.getChild("bone10");
		this.bone13 = this.root.getChild("bone13");
		this.bone12 = this.root.getChild("bone12");
		this.bone30 = this.bone12.getChild("bone30");
		this.bone31 = this.bone12.getChild("bone31");
		this.bone32 = this.bone12.getChild("bone32");
		this.bone33 = this.bone12.getChild("bone33");
		this.bone34 = this.bone12.getChild("bone34");
		this.bone35 = this.bone12.getChild("bone35");
		this.bone36 = this.bone12.getChild("bone36");
		this.bone37 = this.bone12.getChild("bone37");
		this.bone8 = this.root.getChild("bone8");
		this.bone42 = this.bone8.getChild("bone42");
		this.bone43 = this.bone8.getChild("bone43");
		this.bone44 = this.bone8.getChild("bone44");
		this.bone45 = this.bone8.getChild("bone45");
		this.bone46 = this.bone8.getChild("bone46");
		this.bone47 = this.bone8.getChild("bone47");
		this.bone48 = this.bone8.getChild("bone48");
		this.bone49 = this.bone8.getChild("bone49");
		this.bone50 = this.bone8.getChild("bone50");
		this.bone7 = this.root.getChild("bone7");
		this.bone5 = this.bone7.getChild("bone5");
		this.bone4 = this.bone7.getChild("bone4");
		this.bone6 = this.bone7.getChild("bone6");
		this.bone3 = this.bone7.getChild("bone3");
		this.bone = this.root.getChild("bone");
		this.bone18 = this.bone.getChild("bone18");
		this.bone19 = this.bone.getChild("bone19");
		this.bone20 = this.bone.getChild("bone20");
		this.bone21 = this.bone.getChild("bone21");
		this.bone2 = this.root.getChild("bone2");
		this.bone14 = this.bone2.getChild("bone14");
		this.bone15 = this.bone2.getChild("bone15");
		this.bone16 = this.bone2.getChild("bone16");
		this.bone17 = this.bone2.getChild("bone17");
		this.bone11 = this.root.getChild("bone11");
		this.bone26 = this.bone11.getChild("bone26");
		this.bone27 = this.bone11.getChild("bone27");
		this.bone28 = this.bone11.getChild("bone28");
		this.bone29 = this.bone11.getChild("bone29");
		this.bone38 = this.root.getChild("bone38");
		this.bone39 = this.root.getChild("bone39");
		this.bone40 = this.root.getChild("bone40");
		this.bone41 = this.root.getChild("bone41");
		this.bone51 = this.root.getChild("bone51");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root_def = partdefinition.addOrReplaceChild("root", CubeListBuilder.create()
			.texOffs(52, 0).addBox(-15.0F, -1.0F, -1.0F, 16.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(20, 73).addBox(-15.0F, -2.0F, 14.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(54, 73).addBox(-15.0F, -2.0F, -1.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(52, 27).addBox(-15.0F, -2.0F, 0.0F, 1.0F, 1.0F, 14.0F, new CubeDeformation(0.0F))
			.texOffs(82, 27).addBox(0.0F, -2.0F, 0.0F, 1.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 24.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone25_def = root_def.addOrReplaceChild("bone25", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-1.0F, -14.0F, -12.0F, 1.0F, 15.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, 13.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone23_def = root_def.addOrReplaceChild("bone23", CubeListBuilder.create()
			.texOffs(26, 0).addBox(0.0F, -14.0F, 0.0F, 1.0F, 15.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.0F, -2.0F, 1.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone22_def = root_def.addOrReplaceChild("bone22", CubeListBuilder.create()
			.texOffs(0, 27).addBox(0.0F, -14.0F, -1.0F, 12.0F, 15.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-13.0F, -2.0F, 14.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone24_def = root_def.addOrReplaceChild("bone24", CubeListBuilder.create()
			.texOffs(26, 27).addBox(-12.0F, -14.0F, 0.0F, 12.0F, 15.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone9_def = root_def.addOrReplaceChild("bone9", CubeListBuilder.create()
			.texOffs(108, 57).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -7.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone10_def = root_def.addOrReplaceChild("bone10", CubeListBuilder.create()
			.texOffs(52, 70).addBox(-5.0F, -1.0F, -5.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(60, 70).addBox(-5.0F, -1.0F, 3.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(68, 70).addBox(3.0F, -1.0F, -5.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(76, 70).addBox(3.0F, -1.0F, 3.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone13_def = root_def.addOrReplaceChild("bone13", CubeListBuilder.create()
			.texOffs(100, 57).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone12_def = root_def.addOrReplaceChild("bone12", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, -3.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone30_def = bone12_def.addOrReplaceChild("bone30", CubeListBuilder.create()
			.texOffs(26, 76).addBox(0.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone31_def = bone12_def.addOrReplaceChild("bone31", CubeListBuilder.create()
			.texOffs(30, 76).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, 1.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone32_def = bone12_def.addOrReplaceChild("bone32", CubeListBuilder.create()
			.texOffs(34, 76).addBox(1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone33_def = bone12_def.addOrReplaceChild("bone33", CubeListBuilder.create()
			.texOffs(38, 76).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone34_def = bone12_def.addOrReplaceChild("bone34", CubeListBuilder.create()
			.texOffs(42, 76).addBox(-2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone35_def = bone12_def.addOrReplaceChild("bone35", CubeListBuilder.create()
			.texOffs(46, 76).addBox(0.0F, -1.0F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone36_def = bone12_def.addOrReplaceChild("bone36", CubeListBuilder.create()
			.texOffs(50, 76).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone37_def = bone12_def.addOrReplaceChild("bone37", CubeListBuilder.create()
			.texOffs(54, 76).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone8_def = root_def.addOrReplaceChild("bone8", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, -16.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone42_def = bone8_def.addOrReplaceChild("bone42", CubeListBuilder.create()
			.texOffs(48, 57).addBox(0.0F, -2.0F, 1.0F, 1.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 1.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone43_def = bone8_def.addOrReplaceChild("bone43", CubeListBuilder.create()
			.texOffs(74, 57).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, 0.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone44_def = bone8_def.addOrReplaceChild("bone44", CubeListBuilder.create()
			.texOffs(88, 73).addBox(0.0F, -1.0F, -1.0F, 12.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, 0.0F, -6.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone45_def = bone8_def.addOrReplaceChild("bone45", CubeListBuilder.create()
			.texOffs(0, 76).addBox(0.0F, -1.0F, 0.0F, 12.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone46_def = bone8_def.addOrReplaceChild("bone46", CubeListBuilder.create()
			.texOffs(58, 76).addBox(0.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 0.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone47_def = bone8_def.addOrReplaceChild("bone47", CubeListBuilder.create()
			.texOffs(62, 76).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone48_def = bone8_def.addOrReplaceChild("bone48", CubeListBuilder.create()
			.texOffs(66, 76).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone49_def = bone8_def.addOrReplaceChild("bone49", CubeListBuilder.create()
			.texOffs(70, 76).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone50_def = bone8_def.addOrReplaceChild("bone50", CubeListBuilder.create()
			.texOffs(0, 57).addBox(-12.0F, -1.0F, -12.0F, 12.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone7_def = root_def.addOrReplaceChild("bone7", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone5_def = bone7_def.addOrReplaceChild("bone5", CubeListBuilder.create()
			.texOffs(112, 27).addBox(-1.0F, -14.0F, 0.0F, 1.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone4_def = bone7_def.addOrReplaceChild("bone4", CubeListBuilder.create()
			.texOffs(116, 27).addBox(0.0F, -14.0F, 0.0F, 1.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone6_def = bone7_def.addOrReplaceChild("bone6", CubeListBuilder.create()
			.texOffs(120, 27).addBox(0.0F, -14.0F, -1.0F, 1.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 14.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone3_def = bone7_def.addOrReplaceChild("bone3", CubeListBuilder.create()
			.texOffs(124, 27).addBox(0.0F, -14.0F, -1.0F, 1.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.0F, 0.0F, 14.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone_def = root_def.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, -3.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone18_def = bone_def.addOrReplaceChild("bone18", CubeListBuilder.create()
			.texOffs(74, 76).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(84, 70).addBox(1.0F, -2.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(88, 70).addBox(0.0F, -2.0F, -2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.0F, 1.0F, 8.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone19_def = bone_def.addOrReplaceChild("bone19", CubeListBuilder.create()
			.texOffs(92, 70).addBox(0.0F, -2.0F, 1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(78, 76).addBox(0.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(96, 70).addBox(1.0F, -2.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.0F, 1.0F, -8.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone20_def = bone_def.addOrReplaceChild("bone20", CubeListBuilder.create()
			.texOffs(100, 70).addBox(-2.0F, -2.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(82, 76).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(104, 70).addBox(-1.0F, -2.0F, 1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.0F, 1.0F, -8.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone21_def = bone_def.addOrReplaceChild("bone21", CubeListBuilder.create()
			.texOffs(108, 70).addBox(-1.0F, -2.0F, -2.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(86, 76).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(112, 70).addBox(-2.0F, -2.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.0F, 1.0F, 8.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone2_def = root_def.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, -16.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone14_def = bone2_def.addOrReplaceChild("bone14", CubeListBuilder.create()
			.texOffs(116, 70).addBox(-1.0F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(90, 76).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(120, 70).addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone15_def = bone2_def.addOrReplaceChild("bone15", CubeListBuilder.create()
			.texOffs(124, 70).addBox(0.0F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 73).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(94, 76).addBox(-1.0F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone16_def = bone2_def.addOrReplaceChild("bone16", CubeListBuilder.create()
			.texOffs(4, 73).addBox(-1.0F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(98, 76).addBox(0.0F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(8, 73).addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -1.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone17_def = bone2_def.addOrReplaceChild("bone17", CubeListBuilder.create()
			.texOffs(12, 73).addBox(0.0F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(102, 76).addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(16, 73).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone11_def = root_def.addOrReplaceChild("bone11", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone26_def = bone11_def.addOrReplaceChild("bone26", CubeListBuilder.create()
			.texOffs(106, 76).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone27_def = bone11_def.addOrReplaceChild("bone27", CubeListBuilder.create()
			.texOffs(110, 76).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone28_def = bone11_def.addOrReplaceChild("bone28", CubeListBuilder.create()
			.texOffs(114, 76).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone29_def = bone11_def.addOrReplaceChild("bone29", CubeListBuilder.create()
			.texOffs(118, 76).addBox(0.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone38_def = root_def.addOrReplaceChild("bone38", CubeListBuilder.create()
			.texOffs(0, 70).addBox(-6.0F, -1.1F, 0.0F, 12.0F, 1.1F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone39_def = root_def.addOrReplaceChild("bone39", CubeListBuilder.create()
			.texOffs(26, 70).addBox(-6.0F, -1.1F, -1.0F, 12.0F, 1.1F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, 14.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone40_def = root_def.addOrReplaceChild("bone40", CubeListBuilder.create()
			.texOffs(48, 43).addBox(-1.0F, -1.1F, -6.0F, 1.0F, 1.1F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone41_def = root_def.addOrReplaceChild("bone41", CubeListBuilder.create()
			.texOffs(74, 43).addBox(0.0F, -1.1F, -6.0F, 1.0F, 1.1F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition bone51_def = root_def.addOrReplaceChild("bone51", CubeListBuilder.create()
			.texOffs(0, 43).addBox(-6.0F, -1.1F, -6.0F, 12.0F, 1.1F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
	                           float red, float green, float blue, float alpha) {
		this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
