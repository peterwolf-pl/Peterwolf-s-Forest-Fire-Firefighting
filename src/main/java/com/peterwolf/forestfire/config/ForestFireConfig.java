package com.peterwolf.forestfire.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * JSON configuration at config/peterwolfs_forestfire.json.
 * Tuned for dedicated-server multiplayer performance.
 */
public final class ForestFireConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("peterwolfs_forestfire.json");
	private static Data data = new Data();

	private ForestFireConfig() {
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						data = loaded;
					}
				}
			}
			save();
			ForestFireMod.LOGGER.info("Loaded config from {}", PATH);
		} catch (IOException exception) {
			ForestFireMod.LOGGER.error("Failed to load config, using defaults", exception);
			data = new Data();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException exception) {
			ForestFireMod.LOGGER.error("Failed to save config", exception);
		}
	}

	public static Data get() {
		return data;
	}

	public static Path path() {
		return PATH;
	}

	public static final class Data {
		// --- Wind ---
		public boolean windEnabled = true;
		public float minimumWindSpeed = 0.0F;
		public float maximumWindSpeed = 1.0F;
		public int windChangeIntervalTicks = 6000;
		public float windChangeStrength = 0.15F;

		// --- Fire simulation performance ---
		public int maxBurningBlocksPerWorld = 8000;
		public int fireTickInterval = 5;
		public int maxSpreadChecksPerTick = 400;
		public int maxEmbersPerTick = 8;
		public int maxActiveIncidents = 8;
		public int chunkUnloadRetentionRadius = 2;
		public boolean replaceVanillaFireInIncidents = true;
		public boolean allowNaturalIgnition = true;
		public float naturalIgnitionChancePerMinute = 0.02F;

		// --- Spread ---
		public float surfaceSpreadMultiplier = 1.0F;
		public float crownSpreadMultiplier = 1.35F;
		public float radiantHeatMultiplier = 1.0F;
		public float emberSpotFireChance = 0.08F;
		public float uphillSpreadBonus = 0.35F;
		public float downhillSpreadPenalty = 0.25F;

		// --- Water / wetness ---
		public int wetnessDurationTicks = 2400;
		public float waterHeatReduction = 12.0F;
		public float waterMoistureGain = 18.0F;
		public int maxHoseLength = 48;
		public float pressureLossPerSegment = 0.02F;
		public float basePumpPressure = 1.0F;
		public int smallTankCapacity = 2000;
		public int mediumTankCapacity = 6000;
		public int largeTankCapacity = 16000;
		public int backpackCapacity = 120;
		public int pumpFuelCapacity = 1000;
		public int pumpFuelPerSecond = 2;

		// --- Smoke / client ---
		public boolean smokeEnabled = true;
		public int maxSmokeParticles = 200;
		public float smokeDamagePerSecond = 0.5F;
		public boolean smokeCausesBlindness = true;
		public boolean smokeCausesWeakness = true;

		// --- Heat exposure ---
		public boolean heatExposureEnabled = true;
		public float heatDamageMultiplier = 1.0F;

		// --- Peat / underground (disabled by default) ---
		public boolean peatFireEnabled = false;
		public float peatSpreadChance = 0.01F;

		// --- Tree collapse ---
		public boolean treeCollapseEnabled = true;
		public float treeCollapseChance = 0.04F;

		// --- Rescue ---
		public boolean rescueMissionsEnabled = true;
		public boolean villagerPanicEnabled = true;

		// --- Scoring ---
		public float scoreStructuresSavedWeight = 25.0F;
		public float scoreLivesSavedWeight = 40.0F;
		public float scoreContainmentSpeedWeight = 15.0F;
		public float scoreWaterEfficiencyWeight = 10.0F;
		public float scoreTeamworkWeight = 10.0F;

		// --- Debug ---
		public boolean debugLogging = false;
		public boolean showDedicationOnTitle = true;
	}
}
