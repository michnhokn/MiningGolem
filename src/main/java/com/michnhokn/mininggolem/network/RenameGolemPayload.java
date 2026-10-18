package com.michnhokn.mininggolem.network;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.inventory.MiningGolemMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RenameGolemPayload(String name) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RenameGolemPayload> TYPE =
			new CustomPacketPayload.Type<>(MiningGolem.id("rename_golem"));
	public static final StreamCodec<io.netty.buffer.ByteBuf, RenameGolemPayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.stringUtf8(64), RenameGolemPayload::name, RenameGolemPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof MiningGolemMenu menu && menu.getGolem() != null) {
				String name = payload.name().trim();
				if (name.isEmpty()) {
					menu.getGolem().setCustomName(null);
					menu.getGolem().setCustomNameVisible(false);
				} else {
					menu.getGolem().setCustomName(Component.literal(name));
					// always show the tag above the golem, like a named pet
					menu.getGolem().setCustomNameVisible(true);
				}
			}
		});
	}
}
