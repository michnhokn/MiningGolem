package com.michnhokn.mininggolem.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.definitions.CopperGolemAnimation;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public class MiningGolemModel extends EntityModel<MiningGolemRenderState> implements ArmedModel<MiningGolemRenderState> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation walkWithItemAnimation;

	public MiningGolemModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.walkAnimation = CopperGolemAnimation.COPPER_GOLEM_WALK.bake(root);
		this.walkWithItemAnimation = CopperGolemAnimation.COPPER_GOLEM_WALK_ITEM.bake(root);
	}

	/** Same geometry as the vanilla copper golem body layer. */
	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create()
						.texOffs(0, 15)
						.addBox(-4.0F, -6.0F, -3.0F, 8, 6, 6, CubeDeformation.NONE),
				PartPose.offset(0.0F, -5.0F, 0.0F));
		body.addOrReplaceChild("head",
				CubeListBuilder.create()
						.texOffs(0, 0)
						.addBox(-4.0F, -5.0F, -5.0F, 8, 5, 10, new CubeDeformation(0.015F))
						.texOffs(56, 0)
						.addBox(-1.0F, -2.0F, -6.0F, 2, 3, 2, CubeDeformation.NONE)
						.texOffs(37, 8)
						.addBox(-1.0F, -9.0F, -1.0F, 2, 4, 2, new CubeDeformation(-0.015F))
						.texOffs(37, 0)
						.addBox(-2.0F, -13.0F, -2.0F, 4, 4, 4, new CubeDeformation(-0.015F)),
				PartPose.offset(0.0F, -6.0F, 0.0F));
		body.addOrReplaceChild("right_arm",
				CubeListBuilder.create()
						.texOffs(36, 16)
						.addBox(-3.0F, -1.0F, -2.0F, 3, 10, 4, CubeDeformation.NONE),
				PartPose.offset(-4.0F, -6.0F, 0.0F));
		body.addOrReplaceChild("left_arm",
				CubeListBuilder.create()
						.texOffs(50, 16)
						.addBox(0.0F, -1.0F, -2.0F, 3, 10, 4, CubeDeformation.NONE),
				PartPose.offset(4.0F, -6.0F, 0.0F));
		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create()
						.texOffs(0, 27)
						.addBox(-4.0F, 0.0F, -2.0F, 4, 5, 4, CubeDeformation.NONE),
				PartPose.offset(0.0F, -5.0F, 0.0F));
		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create()
						.texOffs(16, 27)
						.addBox(0.0F, 0.0F, -2.0F, 4, 5, 4, CubeDeformation.NONE),
				PartPose.offset(0.0F, -5.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(MiningGolemRenderState state) {
		super.setupAnim(state);
		this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
		this.head.yRot = state.yRot * (float) (Math.PI / 180.0);

		if (state.rightHandItemStack.isEmpty() && state.leftHandItemStack.isEmpty()) {
			this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
		} else {
			this.walkWithItemAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
		}

		if (state.mining) {
			// chopping swing on the right arm while digging
			float chop = state.ageInTicks * 0.45F;
			this.rightArm.xRot = -1.6F + Mth.cos(chop) * 0.7F;
			this.rightArm.zRot = -0.15F;
			this.leftArm.xRot = -0.5F + Mth.cos(chop + Mth.PI) * 0.2F;
		}
	}

	@Override
	public void translateToHand(MiningGolemRenderState state, HumanoidArm arm, PoseStack poseStack) {
		this.root.translateAndRotate(poseStack);
		this.body.translateAndRotate(poseStack);
		ModelPart activeArm = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
		activeArm.translateAndRotate(poseStack);
		poseStack.rotateDegrees(Axis.YP, arm == HumanoidArm.RIGHT ? -90.0F : 90.0F);
		poseStack.translate(0.0F, 0.0F, 0.125F);
	}
}
