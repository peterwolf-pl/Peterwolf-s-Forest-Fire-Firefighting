package com.peterwolf.forestfire.client;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.client.hud.FirefighterHud;
import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.WindSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class ForestFireClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(IncidentHudPayload.TYPE, (payload, context) ->
			context.client().execute(() -> FirefighterHud.updateIncident(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(WindSyncPayload.TYPE, (payload, context) ->
			context.client().execute(() -> FirefighterHud.updateWind(payload))
		);
		HudElementRegistry.addLast(ForestFireMod.id("firefighter_hud"), FirefighterHud::render);
		ForestFireMod.LOGGER.info("Forest Fire client initialized. {}", ForestFireMod.DEDICATION);
	}
}
