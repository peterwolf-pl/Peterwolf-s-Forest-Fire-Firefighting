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
	private static Data data = new Data();

	private ForestFireConfig() {
	}

	/** Lazy so pure unit tests can use defaults without a Fabric runtime. */
	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("peterwolfs_forestfire.json");
	}

	public static void load() {
		Path configPath = path();
		try {
			if (Files.exists(configPath)) {
				try (Reader reader = Files.newBufferedReader(configPath)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						data = loaded;
					}
				}
			}
			data.sanitize();
			save();
			ForestFireMod.LOGGER.info("Loaded config from {}", configPath);
		} catch (IOException | RuntimeException exception) {
			ForestFireMod.LOGGER.error("Failed to load config, using defaults", exception);
			data = new Data();
			try {
				data.sanitize();
				save();
			} catch (RuntimeException ignored) {
				// Offline unit tests may lack a Fabric environment.
			}
		}
	}

	public static void save() {
		Path configPath = path();
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException exception) {
			ForestFireMod.LOGGER.error("Failed to save config", exception);
		}
	}

	public static Data get() {
		return data;
	}

	/** Test hook: apply defaults without disk I/O. */
	public static void resetToDefaultsForTests() {
		data = new Data();
		data.sanitize();
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
		/** Base heat removed per water application unit (higher = faster extinguish). */
		public float waterHeatReduction = 22.0F;
		/** Moisture added per water unit (helps full extinguish + re-ignition prevention). */
		public float waterMoistureGain = 28.0F;
		/** Extra multiplier for pump-fed nozzle streams (not backpack). */
		public float pumpNozzleExtinguishMultiplier = 1.65F;
		public int maxHoseLength = 48;
		/** Extra walk distance past the last hose anchor while holding a connected nozzle. */
		public int nozzleFreeHoseBlocks = 16;
		public float pressureLossPerSegment = 0.02F;
		public float basePumpPressure = 1.0F;
		public boolean nozzleHudEnabled = true;

		// --- Automatic hose deployment ---
		/** Consume hose roll / segment items when auto-deploying. */
		public boolean automaticHoseConsumesItems = true;
		/** Creative players get infinite hose when true. */
		public boolean creativeModeInfiniteHose = true;
		public boolean automaticHoseRetraction = true;
		public boolean returnFullHoseLength = true;
		public boolean hoseDamageLossEnabled = false;
		public int maximumIntakeHoseLength = 24;
		public int maximumIntakeVerticalLift = 6;
		public boolean intakeLengthPressureLoss = true;
		/** If true, only one direct ATTACK line per pump (splitters still allowed). */
		public boolean singleAttackLinePerPump = false;
		/** Client hose render distance (blocks). */
		public int hoseRenderDistance = 96;
		/** Held-nozzle movement (blocks²) before regenerating final path segment. */
		public float hoseEndpointMoveThreshold = 0.25F;
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

		// --- Firefighting aircraft (requires Peterwolf's Planes) ---
		public FirefightingAircraft firefightingAircraft = new FirefightingAircraft();

		// --- Debug ---
		public boolean debugLogging = false;
		public boolean showDedicationOnTitle = true;

		public static final class FirefightingAircraft {
			public boolean enabled = true;
			public int tankCapacity = 12000;
			public int waterIntakeRatePerTick = 30;
			public int waterReleaseRatePerTick = 80;
			public double maximumWaterDistanceBlocks = 3.0;
			/** Horizontal speed (blocks/tick) — matches Planes physics units. */
			public double minimumScoopingSpeed = 0.25;
			public double maximumScoopingSpeed = 0.85;
			public float maximumScoopingRollDegrees = 20.0F;
			public float maximumScoopingPitchDegrees = 15.0F;
			/** Extra mass factor at full tank (added on top of 1.0 empty). */
			public float waterWeightPhysicsMultiplier = 1.0F;
			public float maxCargoMassBonus = 0.55F;
			public int wetnessDurationTicks = 2400;
			public int maximumSuppressionOperationsPerTick = 500;
			public boolean enableSubsystemDamage = false;
			public boolean autoRetractHoseAtUnsafeSpeed = true;
			public double autoRetractSpeed = 1.05;
			public int hoseNotOverWaterTimeoutTicks = 40;
			public float dropStrength = 1.35F;
			public float dropBaseRadius = 2.5F;
			public float dropRadiusPerAltitude = 0.12F;
			public int maxActiveWaterPayloads = 48;
			public int waterSyncThreshold = 25;
			public boolean finiteWaterExtraction = false;
			public boolean debugMetrics = false;

			void sanitize() {
				tankCapacity = Math.max(100, Math.min(500_000, tankCapacity));
				waterIntakeRatePerTick = Math.max(1, Math.min(5000, waterIntakeRatePerTick));
				waterReleaseRatePerTick = Math.max(1, Math.min(10000, waterReleaseRatePerTick));
				maximumWaterDistanceBlocks = clampD(maximumWaterDistanceBlocks, 0.5, 16.0);
				minimumScoopingSpeed = clampD(minimumScoopingSpeed, 0.0, 2.0);
				maximumScoopingSpeed = clampD(maximumScoopingSpeed, minimumScoopingSpeed, 4.0);
				maximumScoopingRollDegrees = clamp(maximumScoopingRollDegrees, 5.0F, 80.0F);
				maximumScoopingPitchDegrees = clamp(maximumScoopingPitchDegrees, 5.0F, 60.0F);
				waterWeightPhysicsMultiplier = clamp(waterWeightPhysicsMultiplier, 0.0F, 3.0F);
				maxCargoMassBonus = clamp(maxCargoMassBonus, 0.0F, 2.0F);
				wetnessDurationTicks = Math.max(20, Math.min(72000, wetnessDurationTicks));
				maximumSuppressionOperationsPerTick = Math.max(10, Math.min(5000, maximumSuppressionOperationsPerTick));
				autoRetractSpeed = clampD(autoRetractSpeed, maximumScoopingSpeed, 5.0);
				hoseNotOverWaterTimeoutTicks = Math.max(5, Math.min(200, hoseNotOverWaterTimeoutTicks));
				dropStrength = clamp(dropStrength, 0.1F, 10.0F);
				dropBaseRadius = clamp(dropBaseRadius, 0.5F, 12.0F);
				dropRadiusPerAltitude = clamp(dropRadiusPerAltitude, 0.0F, 1.0F);
				maxActiveWaterPayloads = Math.max(4, Math.min(256, maxActiveWaterPayloads));
				waterSyncThreshold = Math.max(1, Math.min(1000, waterSyncThreshold));
			}

			private static float clamp(float value, float minimum, float maximum) {
				return Math.max(minimum, Math.min(maximum, value));
			}

			private static double clampD(double value, double minimum, double maximum) {
				return Math.max(minimum, Math.min(maximum, value));
			}
		}

		private void sanitize() {
			if (firefightingAircraft == null) {
				firefightingAircraft = new FirefightingAircraft();
			}
			firefightingAircraft.sanitize();
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
