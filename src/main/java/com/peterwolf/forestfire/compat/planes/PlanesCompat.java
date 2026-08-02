package com.peterwolf.forestfire.compat.planes;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneEntity;
import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneRegistry;
import com.peterwolf.forestfire.command.FireplaneCommands;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.network.firefighting.FireplaneActionPayload;
import com.peterwolf.forestfire.simulation.water_drop.WaterDropSimulator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soft integration with Peterwolf's Planes.
 * Loaded via reflection only when {@code peterwolfs_planes} is present.
 */
public final class PlanesCompat {
	private PlanesCompat() {
	}

	public static void init() {
		if (!ForestFireConfig.get().firefightingAircraft.enabled) {
			ForestFireMod.LOGGER.info("Firefighting aircraft disabled in config.");
			return;
		}

		FirefightingPlaneRegistry.register();
		FireplaneCommands.register();

		PayloadTypeRegistry.serverboundPlay().register(FireplaneActionPayload.TYPE, FireplaneActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FireplaneActionPayload.TYPE, (payload, context) ->
			context.server().execute(() -> handleAction(context.player(), payload))
		);

		ServerTickEvents.END_LEVEL_TICK.register(WaterDropSimulator::getAndTick);

		ForestFireMod.LOGGER.info("Planes compat: firefighting water bomber ready.");
	}

	private static void handleAction(ServerPlayer player, FireplaneActionPayload payload) {
		if (!(player.getVehicle() instanceof FirefightingPlaneEntity plane)) {
			return;
		}
		// Only the active pilot may control firefighting systems (validated in entity methods).
		switch (payload.action()) {
			case FireplaneActionPayload.TOGGLE_DROP_ARMED -> plane.toggleDropArmed(player);
			case FireplaneActionPayload.SET_RELEASING -> plane.setReleaseActive(payload.active(), player);
			case FireplaneActionPayload.TOGGLE_HOSE -> plane.toggleHose(player);
			default -> {
			}
		}
	}
}
