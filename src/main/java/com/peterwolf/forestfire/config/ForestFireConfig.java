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
			data.sanitize();
			save();
			ForestFireMod.LOGGER.info("Loaded config from {}", PATH);
		} catch (IOException | RuntimeException exception) {
			ForestFireMod.LOGGER.error("Failed to load config, using defaults", exception);
			data = new Data();
			save();
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
		/** true = gradually changing weather; false = hold configured speed and yaw. */
		public boolean dynamicWind = true;
		/** Initial wind speed and the speed held by fixed mode (0 = calm, 1 = strong). */
		public float configuredWindSpeed = 0.25F;
		/** Minecraft yaw: 0 = south, 90 = west, 180 = north, -90 = east. */
		public float configuredWindYawDegrees = 0.0F;
		public float minimumWindSpeed = 0.0F;
		public float maximumWindSpeed = 1.0F;
		public int windChangeIntervalTicks = 6000;
		/** Maximum random speed change at each weather update. */
		public float windChangeStrength = 0.15F;
		/** Maximum random direction change at each weather update. */
		public float windDirectionChangeDegrees = 21.0F;
		/** Per-tick interpolation factor toward the next weather state. */
		public float windSmoothingFactor = 0.02F;

		// --- Fire simulation performance ---
		public int maxBurningBlocksPerWorld = 8000;
		public int fireTickInterval = 5;
		public int maxSpreadChecksPerTick = 400;
		public int maxEmbersPerTick = 12;
		public int maxActiveIncidents = 8;
		/** Chunk radius retained around an active fire front; 0 disables retention. */
		public int chunkUnloadRetentionRadius = 2;
		/** Hard cap on non-overlapping active-fire ticket centres. */
		public int maxFireChunkTickets = 128;
		public boolean replaceVanillaFireInIncidents = true;
		public boolean allowNaturalIgnition = true;
		public float naturalIgnitionChancePerMinute = 0.02F;

		// --- Sparks / flying plant fragments ---
		/** Heat-lifted sparks from burning vegetation (embers + ash fragments). */
		public boolean sparksEnabled = true;
		/** Max concurrent sparks ≈ maxEmbersPerTick × this multiplier. */
		public int sparkPoolMultiplier = 10;
		/** Chance multiplier when a spark lands on fuel (0–1 base before material power). */
		public float emberSpotFireChance = 0.12F;
		/** Extra spark chance scale from cell heat (0–1). */
		public float sparkHeatBonus = 0.55F;

		// --- Spread ---
		public float surfaceSpreadMultiplier = 1.0F;
		public float crownSpreadMultiplier = 1.35F;
		public float radiantHeatMultiplier = 1.0F;
		public float uphillSpreadBonus = 0.35F;
		public float downhillSpreadPenalty = 0.25F;

		// --- Firestorm (large hot fire feeds itself + drives wind) ---
		public boolean firestormEnabled = true;
		/** Flaming blocks required before blow-up behaviour starts (user: ~20+). */
		public int firestormMinBurningBlocks = 20;
		/** Max spread speed multiplier at full firestorm intensity. */
		public float firestormSpreadMultiplierMax = 2.8F;
		/** Immediate spread bump once threshold is crossed. */
		public float firestormThresholdSpreadBonus = 0.35F;
		/** Max extra wind speed from fire convection. */
		public float firestormWindBoostMax = 0.65F;
		/** Immediate wind bump at threshold. */
		public float firestormThresholdWindBonus = 0.12F;
		/** Spark production multiplier at full intensity. */
		public float firestormSparkMultiplier = 2.2F;
		/** Extra heat fed into flaming cells during firestorm (self-heating). */
		public float firestormSelfHeat = 0.8F;

		// --- Water / wetness ---
		public int wetnessDurationTicks = 2400;
		public float waterHeatReduction = 12.0F;
		public float waterMoistureGain = 18.0F;
		public int maxHoseLength = 48;
		/** Extra walk distance past the last hose anchor while holding a connected nozzle. */
		public int nozzleFreeHoseBlocks = 16;
		public float pressureLossPerSegment = 0.02F;
		public float basePumpPressure = 1.0F;
		public boolean nozzleHudEnabled = true;
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

		// --- Tree collapse (prefers Realistic Tree Felling when installed) ---
		public boolean treeCollapseEnabled = true;
		/** Per-sim-tick chance once trunk integrity is low (higher when fully involved). */
		public float treeCollapseChance = 0.06F;

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

		private void sanitize() {
			minimumWindSpeed = finiteOr(minimumWindSpeed, 0.0F);
			maximumWindSpeed = finiteOr(maximumWindSpeed, 1.0F);
			minimumWindSpeed = clamp(minimumWindSpeed, 0.0F, 4.0F);
			maximumWindSpeed = clamp(maximumWindSpeed, minimumWindSpeed, 4.0F);
			configuredWindSpeed = clamp(
				finiteOr(configuredWindSpeed, 0.25F),
				minimumWindSpeed,
				maximumWindSpeed
			);
			configuredWindYawDegrees = wrapDegrees(finiteOr(configuredWindYawDegrees, 0.0F));
			windChangeIntervalTicks = Math.max(200, windChangeIntervalTicks);
			windChangeStrength = clamp(finiteOr(windChangeStrength, 0.15F), 0.0F, 4.0F);
			windDirectionChangeDegrees = clamp(
				finiteOr(windDirectionChangeDegrees, 21.0F),
				0.0F,
				180.0F
			);
			windSmoothingFactor = clamp(finiteOr(windSmoothingFactor, 0.02F), 0.001F, 1.0F);
			chunkUnloadRetentionRadius = Math.max(0, Math.min(4, chunkUnloadRetentionRadius));
			maxFireChunkTickets = Math.max(1, Math.min(512, maxFireChunkTickets));
		}

		private static float finiteOr(float value, float fallback) {
			return Float.isFinite(value) ? value : fallback;
		}

		private static float clamp(float value, float minimum, float maximum) {
			return Math.max(minimum, Math.min(maximum, value));
		}

		private static float wrapDegrees(float degrees) {
			float wrapped = degrees % 360.0F;
			if (wrapped >= 180.0F) {
				wrapped -= 360.0F;
			}
			if (wrapped < -180.0F) {
				wrapped += 360.0F;
			}
			return wrapped;
		}
	}
}
