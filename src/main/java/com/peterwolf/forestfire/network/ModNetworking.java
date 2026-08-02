package com.peterwolf.forestfire.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compact multiplayer sync. Server authority; clients receive HUD snapshots.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(IncidentHudPayload.TYPE, IncidentHudPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WindSyncPayload.TYPE, WindSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NozzleHudPayload.TYPE, NozzleHudPayload.CODEC);
	}

	public static void sendHud(ServerPlayer player, IncidentHudPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void sendWind(ServerPlayer player, WindSyncPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void sendNozzleHud(ServerPlayer player, NozzleHudPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
