package com.michnhokn.mininggolem;

import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import com.michnhokn.mininggolem.inventory.MiningGolemMenu;
import com.michnhokn.mininggolem.item.ModItems;
import com.michnhokn.mininggolem.network.RenameGolemPayload;
import com.michnhokn.mininggolem.network.SetDirectionPayload;
import com.michnhokn.mininggolem.network.ToggleMiningPayload;
import com.michnhokn.mininggolem.spawn.SpawnRitualHandler;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiningGolem implements ModInitializer {
	public static final String MOD_ID = "mininggolem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<EntityType<?>> MINING_GOLEM_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, id("mining_golem"));
	public static final EntityType<MiningGolemEntity> MINING_GOLEM = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			MINING_GOLEM_KEY,
			EntityType.Builder.of(MiningGolemEntity::new, MobCategory.MISC)
					.sized(0.49F, 0.98F)
					.eyeHeight(0.8125F)
					.clientTrackingRange(10)
					.fireImmune()
					.build(MINING_GOLEM_KEY));

	public static final MenuType<MiningGolemMenu> MINING_GOLEM_MENU = Registry.register(
			BuiltInRegistries.MENU,
			id("mining_golem_menu"),
			new MenuType<>(MiningGolemMenu::createClient, FeatureFlags.DEFAULT_FLAGS));

	/** Keeps the golem's surrounding chunks loading and entity-ticking while a job runs. */
	public static final TicketType MINING_TICKET_TYPE = Registry.register(
			BuiltInRegistries.TICKET_TYPE,
			id("mining_ticket"),
			new TicketType(40L,
					TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

	/** Debug mode: slows the golem down and highlights break/place targets. Toggled via /mininggolem debug. */
	public static volatile boolean DEBUG = false;

	@Override
	public void onInitialize() {
		ModItems.initialize();
		FabricDefaultAttributeRegistry.register(MINING_GOLEM, MiningGolemEntity.createAttributes());
		ToggleMiningPayload.register();
		RenameGolemPayload.register();
		SetDirectionPayload.register();
		SpawnRitualHandler.register();
		registerCommands();
		LOGGER.info("Mining Golem initialized");
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("mininggolem")
						.requires(source -> source.permissions() instanceof LevelBasedPermissionSet levelSet
								&& levelSet.level().isEqualOrHigherThan(PermissionLevel.ADMINS))
						.then(Commands.literal("debug")
								.executes(context -> {
									DEBUG = !DEBUG;
									context.getSource().sendSuccess(
											() -> Component.literal("Mining Golem debug: " + (DEBUG ? "on" : "off")), true);
									return 1;
								})
								.then(Commands.argument("enabled", BoolArgumentType.bool())
										.executes(context -> {
											DEBUG = BoolArgumentType.getBool(context, "enabled");
											context.getSource().sendSuccess(
													() -> Component.literal("Mining Golem debug: " + (DEBUG ? "on" : "off")), true);
											return 1;
										})))));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
