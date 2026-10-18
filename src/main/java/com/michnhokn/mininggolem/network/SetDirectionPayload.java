package com.michnhokn.mininggolem.network;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import com.michnhokn.mininggolem.inventory.MiningGolemMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetDirectionPayload(int directionOrdinal) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetDirectionPayload> TYPE =
			new CustomPacketPayload.Type<>(MiningGolem.id("set_direction"));
	public static final StreamCodec<io.netty.buffer.ByteBuf, SetDirectionPayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, SetDirectionPayload::directionOrdinal, SetDirectionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof MiningGolemMenu menu && menu.getGolem() != null) {
				MiningGolemEntity golem = menu.getGolem();
				Direction[] values = Direction.values();
				int ordinal = payload.directionOrdinal();
				if (ordinal < 0 || ordinal >= values.length) return;
				Direction direction = values[ordinal];
				if (direction.getAxis().isVertical()) return;
				// a new direction invalidates the current plan
				if (golem.isJobActive()) {
					golem.setJobActive(false);
					golem.setStatus(MiningGolemEntity.GolemStatus.IDLE);
				}
				golem.resetJob();
				golem.setWorkDirection(direction);
			}
		});
	}
}
