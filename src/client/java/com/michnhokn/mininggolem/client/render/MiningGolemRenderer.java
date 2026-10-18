package com.michnhokn.mininggolem.client.render;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class MiningGolemRenderer extends MobRenderer<MiningGolemEntity, MiningGolemRenderState, MiningGolemModel> {
	private static final Identifier TEXTURE = MiningGolem.id("textures/entity/mining_golem.png");
	private static final Identifier EYES_TEXTURE = MiningGolem.id("textures/entity/mining_golem_eyes.png");

	public MiningGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new MiningGolemModel(MiningGolemModel.createBodyLayer().bakeRoot()), 0.5F);
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(new LivingEntityEmissiveLayer<>(
				this,
				state -> EYES_TEXTURE,
				(state, ageInTicks) -> 1.0F,
				new MiningGolemModel(MiningGolemModel.createBodyLayer().bakeRoot()),
				RenderTypes::eyes,
				false));
	}

	@Override
	public Identifier getTextureLocation(MiningGolemRenderState state) {
		return TEXTURE;
	}

	@Override
	public MiningGolemRenderState createRenderState() {
		return new MiningGolemRenderState();
	}

	@Override
	public void extractRenderState(MiningGolemEntity entity, MiningGolemRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTick);
		state.mining = entity.isMiningAnim();
	}
}
