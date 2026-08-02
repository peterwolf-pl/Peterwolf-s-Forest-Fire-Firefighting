package com.peterwolf.forestfire;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.command.FireDangerCommands;
import com.peterwolf.forestfire.command.FireIncidentCommands;
import com.peterwolf.forestfire.command.FireTestCommands;
import com.peterwolf.forestfire.command.FirefighterCommands;
import com.peterwolf.forestfire.command.WindCommands;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.BurningTreeCollapse;
import com.peterwolf.forestfire.fire.simulation.FireWorldTicker;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleEvents;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.ModNetworking;
import com.peterwolf.forestfire.sound.ModSounds;
import com.peterwolf.forestfire.world.FireChunkTickets;
import com.peterwolf.forestfire.world.FirePersistenceHooks;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Peterwolf's Forest Fire & Firefighting — multiplayer-first wildfire simulator.
 * Server-authoritative simulation; clients receive compact sync and rendering hints.
 */
public final class ForestFireMod implements ModInitializer {
	public static final String MOD_ID = "peterwolfs_forestfire";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Dedication shown on title screen / mod info / first launch. */
	public static final String DEDICATION =
		"Dedicated to all firefighters who risk their lives protecting forests, wildlife and communities.";

	@Override
	public void onInitialize() {
		ForestFireConfig.load();
		FireChunkTickets.register();
		ModSounds.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModNetworking.register();
		FireIncidentCommands.register();
		FireDangerCommands.register();
		WindCommands.register();
		FirefighterCommands.register();
		FireTestCommands.register();
		FireWorldTicker.register();
		FirePersistenceHooks.register();
		NozzleEvents.register();
		BurningTreeCollapse.init();
		LOGGER.info("Peterwolf's Forest Fire & Firefighting initialized.");
		LOGGER.info(DEDICATION);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
