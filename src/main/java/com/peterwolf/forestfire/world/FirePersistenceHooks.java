package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;

public final class FirePersistenceHooks {
	private FirePersistenceHooks() {
	}

	public static void register() {
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			for (ServerLevel level : server.getAllLevels()) {
				try {
					IncidentManager.get(level).save();
				} catch (Exception ignored) {
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				try {
					IncidentManager.get(level).save();
				} catch (Exception ignored) {
				}
			}
			IncidentManager.clearAll();
			FireSimulation.clearAll();
		});
	}
}
