package com.michnhokn.mininggolem.network;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.inventory.MiningGolemMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToggleMiningPayload() implements CustomPacketPayload {
	public static final ToggleMiningPayload INSTANCE = new ToggleMiningPayload();
	public static final CustomPacketPayload.Type<ToggleMiningPayload> TYPE =
			new CustomPacketPayload.Type<>(MiningGolem.id("toggle_mining"));
	public static final StreamCodec<ByteBuf, ToggleMiningPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof MiningGolemMenu menu && menu.getGolem() != null) {
				menu.getGolem().toggleMining(context.player());
			}
		});
	}
}
