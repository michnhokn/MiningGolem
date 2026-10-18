package com.michnhokn.mininggolem.client;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.client.render.MiningGolemRenderer;
import com.michnhokn.mininggolem.client.screen.MiningGolemScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class MiningGolemClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(MiningGolem.MINING_GOLEM, MiningGolemRenderer::new);
		MenuScreens.register(MiningGolem.MINING_GOLEM_MENU, MiningGolemScreen::new);
	}
}
