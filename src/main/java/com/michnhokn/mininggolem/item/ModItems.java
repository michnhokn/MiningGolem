package com.michnhokn.mininggolem.item;

import com.michnhokn.mininggolem.MiningGolem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class ModItems {
	public static final ResourceKey<Item> MINING_GOLEM_SPAWN_EGG_KEY =
			ResourceKey.create(Registries.ITEM, MiningGolem.id("mining_golem_spawn_egg"));
	public static final Item MINING_GOLEM_SPAWN_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			MINING_GOLEM_SPAWN_EGG_KEY,
			new SpawnEggItem(new Item.Properties()
					.setId(MINING_GOLEM_SPAWN_EGG_KEY)
					.spawnEgg(MiningGolem.MINING_GOLEM)));

	public static void initialize() {
		ResourceKey<CreativeModeTab> spawnEggsTab =
				ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
		CreativeModeTabEvents.modifyOutputEvent(spawnEggsTab)
				.register(entries -> entries.accept(MINING_GOLEM_SPAWN_EGG));
	}
}
