package com.michnhokn.mininggolem.client.network;

import com.michnhokn.mininggolem.network.RenameGolemPayload;
import com.michnhokn.mininggolem.network.SetDirectionPayload;
import com.michnhokn.mininggolem.network.ToggleMiningPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void sendToggle() {
		ClientPlayNetworking.send(ToggleMiningPayload.INSTANCE);
	}

	public static void sendRename(String name) {
		ClientPlayNetworking.send(new RenameGolemPayload(name));
	}

	public static void sendSetDirection(int directionOrdinal) {
		ClientPlayNetworking.send(new SetDirectionPayload(directionOrdinal));
	}
}
