package com.peterwolf.forestfire.fire.simulation;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentStatus;
import com.peterwolf.forestfire.fire.spread.EmberSystem;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.FirestormState;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import com.peterwolf.forestfire.world.FireChunkTickets;
import com.peterwolf.forestfire.world.FirebreakTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative wildfire simulation.
 * Stores minimum per-block state and ticks only active cells with hard caps.
 */
public final class FireSimulation {
	private static final Map<ServerLevel, FireSimulation> CACHE = new HashMap<>();
	private static final Direction[] NEIGHBOURS = Direction.values();
	private static final int[][] HORIZONTAL_OFFSETS = {
		{-1, -1}, {0, -1}, {1, -1},
		{-1, 0},            {1, 0},
		{-1, 1},  {0, 1},   {1, 1}
	};
	private static final int[] HEIGHT_SEARCH = {0, 1, -1, 2, -2};

	private final ServerLevel level;
	private final Map<Long, FireCell> cells = new ConcurrentHashMap<>();
	private final EmberSystem embers = new EmberSystem();
	private final FirebreakTracker firebreaks = new FirebreakTracker();
	/** Ticket centre -> radius used when the ticket was added. */
	private final Map<Long, Integer> activeFireChunkTickets = new HashMap<>();
	private int tickCounter;
	private int activeCellCount;
	/** Cached each fire sim tick — large fires run hot and drive wind. */
	private FirestormState firestorm = FirestormState.INACTIVE;

	private FireSimulation(ServerLevel level) {
		this.level = level;
	}

	public static FireSimulation get(ServerLevel level) {
		return CACHE.computeIfAbsent(level, FireSimulation::new);
	}

	public static void invalidate(ServerLevel level) {
		FireSimulation simulation = CACHE.remove(level);
		if (simulation != null) {
			simulation.releaseChunkTickets();
		}
	}

	public static void clearAll() {
		for (FireSimulation simulation : CACHE.values()) {
			simulation.releaseChunkTickets();
		}
		CACHE.clear();
	}

	public FirebreakTracker firebreaks() {
		return firebreaks;
	}

	public Map<Long, FireCell> cells() {
		return cells;
	}

	public int activeFireChunkTicketCount() {
		return activeFireChunkTickets.size();
	}

	public int activeCellCount() {
		return activeCellCount;
	}

	public FireCell getCell(BlockPos pos) {
		return cells.get(pos.asLong());
	}

	public FireCell getOrCreate(BlockPos pos, FuelMaterial material, int incidentId) {
		long key = pos.asLong();
		FireCell existing = cells.get(key);
		if (existing != null) {
			return existing;
		}
		FireCell created = cells.computeIfAbsent(key, ignored -> {
			FireCell cell = new FireCell(material, incidentId);
			float initialMoisture = WeatherFireModel.initialFuelMoisture01(
				level,
				pos,
				material.baseMoisture
			);
			cell.setMoisture(Math.round(initialMoisture * 100.0F));
			return cell;
		});
		activeCellCount++;
		return created;
	}

	public void loadCells(List<FireCellRecord> records) {
		cells.clear();
		for (FireCellRecord record : records) {
			cells.put(record.pos().asLong(), record.toCell());
		}
		recountActiveCells();
	}

	public List<FireCellRecord> exportCells() {
		List<FireCellRecord> list = new ArrayList<>(cells.size());
		for (Map.Entry<Long, FireCell> entry : cells.entrySet()) {
			list.add(FireCellRecord.from(BlockPos.of(entry.getKey()), entry.getValue()));
		}
		return list;
	}

	/**
	 * Start a wildfire at the lowest exposed fuel in each column. Canopy and upper
	 * trunks are deliberately not force-lit; convection and ladder fuels must carry
	 * a surface fire upward.
	 */
	public int igniteArea(BlockPos center, int radius, int incidentId) {
		int count = 0;
		int r = Math.max(1, radius);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				BlockPos columnBase = new BlockPos(x, center.getY(), z);
				if (!level.isLoaded(columnBase)) {
					continue;
				}
				BlockPos fuel = findLowestIgnitionFuel(x, z, center.getY());
				if (fuel != null
					&& tryIgnite(fuel, incidentId, 70 + level.getRandom().nextInt(25), true)) {
					count++;
				}
			}
		}

		// Guarantee a core attempt for sparse terrain without igniting air.
		if (count == 0) {
			BlockPos fuel = findLowestIgnitionFuel(center.getX(), center.getZ(), center.getY());
			if (fuel != null && tryIgnite(fuel, incidentId, 90, true)) {
				count++;
			}
		}
		return count;
	}

	private BlockPos findLowestIgnitionFuel(int x, int z, int referenceY) {
		BlockPos column = new BlockPos(x, referenceY, z);
		if (!level.isLoaded(column)) {
			return null;
		}
		int top = level.getHeight(
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
			x,
			z
		);
		int yMin = Math.min(referenceY - 3, top - 24);
		int yMax = Math.max(referenceY + 5, top);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, yMin, z);
		for (int y = yMin; y <= yMax; y++) {
			cursor.setY(y);
			FuelMaterial material = FuelMaterial.of(level.getBlockState(cursor));
			if (isSpreadFuel(material)) {
				return cursor.immutable();
			}
		}
		return null;
	}

	public boolean tryIgnite(BlockPos pos, int incidentId, int heat, boolean force) {
		FireCell tracked = cells.get(pos.asLong());
		boolean needsActiveSlot = tracked == null
			|| tracked.stage() == BurnStage.CHARRED
			|| tracked.stage() == BurnStage.EXTINGUISHED;
		if (activeCellCount >= ForestFireConfig.get().maxBurningBlocksPerWorld && needsActiveSlot) {
			return false;
		}
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		FuelMaterial material = FuelMaterial.of(state);
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		if (material == FuelMaterial.PEAT && !cfg.peatFireEnabled) {
			return false;
		}
		if (!material.flammable()) {
			// Vanilla fire block: convert underlying fuel if present
			if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
				BlockPos below = pos.below();
				BlockState under = level.getBlockState(below);
				FuelMaterial underMat = FuelMaterial.of(under);
				if (underMat.flammable()) {
					return tryIgnite(below, incidentId, heat, force);
				}
			}
			return false;
		}
		if (firebreaks.isFirebreak(pos) && !force) {
			return false;
		}
		FireCell cell = getOrCreate(pos, material, incidentId);
		boolean wasTerminal = cell.stage() == BurnStage.CHARRED || cell.stage() == BurnStage.EXTINGUISHED;
		if (cell.stage() == BurnStage.WET && cell.moisture01() > 0.55F && !force) {
			return false;
		}
		cell.incidentId = incidentId > 0 ? incidentId : cell.incidentId;
		cell.addHeat(heat);
		// Refresh fuel if the cell was nearly spent but is being re-ignited by an incident
		if (force && (cell.fuel & 0xFF) < material.initialFuelPercent() / 2) {
			cell.setFuel(material.initialFuelPercent());
		}
		if (cell.stage() == BurnStage.UNBURNED || cell.stage() == BurnStage.WET || cell.stage() == BurnStage.EXTINGUISHED
			|| cell.stage() == BurnStage.CHARRED) {
			cell.setStage(BurnStage.HEATING);
		}
		if (force) {
			// Admin / incident ignition: skip the slow HEATING wait and go straight to flames
			cell.setMoisture(Math.min(cell.moisture & 0xFF, 20));
			cell.setHeat(Math.max(cell.heat & 0xFF, 75));
			if (!cell.stage().hasVisibleFlames()) {
				cell.setStage(BurnStage.FLAMING);
			}
			ensureFireVisual(pos);
		} else if ((cell.heat & 0xFF) >= FireSpreadMath.ignitionThreshold(material.igniteEase, cell.moisture01())) {
			cell.setStage(BurnStage.IGNITED);
			cell.setHeat(Math.max(cell.heat & 0xFF, 50));
		}
		if (wasTerminal && cell.stage() != BurnStage.CHARRED && cell.stage() != BurnStage.EXTINGUISHED) {
			activeCellCount++;
		}
		return true;
	}

	/**
	 * Apply water cooling. Returns water units effectively used (0–1 scale per hit).
	 */
	public float applyWater(BlockPos pos, float strength, float radius) {
		float used = 0.0F;
		int r = Math.max(0, Math.round(radius));
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r + 1) {
						continue;
					}
					cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					FireCell cell = cells.get(cursor.asLong());
					BlockState state = level.getBlockState(cursor);
					FuelMaterial material = FuelMaterial.of(state);
					if (cell == null && material.flammable()
						&& activeCellCount < cfg.maxBurningBlocksPerWorld) {
						cell = getOrCreate(cursor.immutable(), material, 0);
					}
					if (cell == null) {
						continue;
					}
					float falloff = 1.0F - Math.min(1.0F, Mth.sqrt(dx * dx + dy * dy + dz * dz) / (r + 1.0F));
					float amount = strength * falloff;
					int prevHeat = cell.heat & 0xFF;
					cell.addHeat(-cfg.waterHeatReduction * amount);
					cell.addMoisture(cfg.waterMoistureGain * amount);
					used += amount * 0.25F;

					if ((cell.heat & 0xFF) < 20 && cell.stage().hasVisibleFlames()) {
						cell.setStage(BurnStage.SMOULDERING);
					}
					if ((cell.heat & 0xFF) < 8 && cell.moisture01() > 0.55F) {
						if (cell.material().longBurn && (cell.fuel & 0xFF) > 15) {
							cell.setStage(BurnStage.REIGNITION_RISK);
							cell.reignitionTimer = (short) (200 + level.getRandom().nextInt(400));
						} else {
							cell.setStage(BurnStage.EXTINGUISHED);
						}
					}
					if (cell.moisture01() > 0.7F) {
						firebreaks.markWet(cursor.immutable(), cfg.wetnessDurationTicks);
						if (!cell.stage().isBurning()) {
							cell.setStage(BurnStage.WET);
						}
					}
					if (prevHeat > 60 && (cell.heat & 0xFF) < prevHeat) {
						// Steam
						level.sendParticles(ParticleTypes.CLOUD,
							cursor.getX() + 0.5, cursor.getY() + 0.8, cursor.getZ() + 0.5,
							2, 0.15, 0.1, 0.15, 0.01);
					}
					// Remove vanilla fire above
					BlockPos above = cursor.above();
					if (level.getBlockState(above).is(Blocks.FIRE) || level.getBlockState(above).is(Blocks.SOUL_FIRE)) {
						level.removeBlock(above, false);
					}
					if (level.getBlockState(cursor).is(Blocks.FIRE) || level.getBlockState(cursor).is(Blocks.SOUL_FIRE)) {
						level.removeBlock(cursor, false);
					}
				}
			}
		}
		return used;
	}

	public void clearIncident(int incidentId) {
		Iterator<Map.Entry<Long, FireCell>> it = cells.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue().incidentId == incidentId) {
				it.remove();
			}
		}
		recountActiveCells();
	}

	public void extinguishIncident(int incidentId) {
		for (FireCell cell : cells.values()) {
			if (cell.incidentId == incidentId) {
				cell.setHeat(0);
				cell.addMoisture(40);
				cell.setStage(BurnStage.EXTINGUISHED);
				cell.reignitionTimer = 0;
			}
		}
		recountActiveCells();
	}

	/**
	 * Admin douse: extinguish simulation cells and remove vanilla fire in a sphere around {@code center}.
	 * @return number of fire cells extinguished
	 */
	public int extinguishInRadius(BlockPos center, int radius) {
		int r = Math.max(0, radius);
		int r2 = r * r;
		int extinguished = 0;
		Iterator<Map.Entry<Long, FireCell>> it = cells.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, FireCell> entry = it.next();
			BlockPos pos = BlockPos.of(entry.getKey());
			int dx = pos.getX() - center.getX();
			int dy = pos.getY() - center.getY();
			int dz = pos.getZ() - center.getZ();
			if (dx * dx + dy * dy + dz * dz > r2) {
				continue;
			}
			FireCell cell = entry.getValue();
			if (cell.stage().isBurning() || cell.stage().hasVisibleFlames()
				|| cell.stage() == BurnStage.REIGNITION_RISK
				|| cell.stage() == BurnStage.HEATING
				|| cell.stage() == BurnStage.IGNITED
				|| (cell.heat & 0xFF) > 0) {
				extinguished++;
			}
			cell.setHeat(0);
			cell.addMoisture(60);
			cell.setStage(BurnStage.EXTINGUISHED);
			cell.reignitionTimer = 0;
			clearFireVisual(pos);
			if (level.getBlockState(pos).is(Blocks.FIRE) || level.getBlockState(pos).is(Blocks.SOUL_FIRE)) {
				level.removeBlock(pos, false);
			}
		}
		// Also clear vanilla fire even without sim cells
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > r2) {
						continue;
					}
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (!level.isLoaded(cursor)) {
						continue;
					}
					if (level.getBlockState(cursor).is(Blocks.FIRE) || level.getBlockState(cursor).is(Blocks.SOUL_FIRE)) {
						level.removeBlock(cursor, false);
					}
				}
			}
		}
		recountActiveCells();
		return extinguished;
	}

	public void updateIncidentStats(FireIncident incident) {
		int burning = 0;
		int hotspots = 0;
		float intensity = 0.0F;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Map.Entry<Long, FireCell> entry : cells.entrySet()) {
			FireCell cell = entry.getValue();
			if (cell.incidentId != incident.id) {
				continue;
			}
			BurnStage stage = cell.stage();
			if (stage.isBurning()) {
				burning++;
				intensity += cell.material().flameIntensity * cell.heat01();
				BlockPos pos = BlockPos.of(entry.getKey());
				minX = Math.min(minX, pos.getX());
				maxX = Math.max(maxX, pos.getX());
				minZ = Math.min(minZ, pos.getZ());
				maxZ = Math.max(maxZ, pos.getZ());
			}
			if (stage.isHotspotRisk() && (cell.heat & 0xFF) > 10) {
				hotspots++;
			}
		}
		incident.burningBlocks = burning;
		incident.hotspotCount = hotspots;
		incident.peakBurningBlocks = Math.max(incident.peakBurningBlocks, burning);
		incident.fireIntensity = burning == 0 ? 0.0F : intensity / burning;
		if (burning > 0) {
			incident.estimatedFireArea = (maxX - minX + 1) * (maxZ - minZ + 1);
			// Containment: extinguished vs peak
			float controlled = 1.0F - (float) burning / Math.max(1, incident.peakBurningBlocks);
			incident.containmentPercent = Mth.clamp(controlled * 100.0F, 0.0F, 99.0F);
		} else if (hotspots == 0) {
			incident.containmentPercent = 100.0F;
			incident.estimatedFireArea = 0.0F;
		}

		// Approximate threatened structures near ignition
		int structures = 0;
		BlockPos center = incident.ignitionPos;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -24; dx <= 24; dx += 4) {
			for (int dz = -24; dz <= 24; dz += 4) {
				for (int dy = -2; dy <= 6; dy += 2) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (!level.isLoaded(cursor)) {
						continue;
					}
					BlockState state = level.getBlockState(cursor);
					if (FuelMaterial.of(state) == FuelMaterial.WOODEN_STRUCTURE) {
						structures++;
					}
				}
			}
		}
		incident.threatenedStructures = structures;
	}

	public FirestormState firestorm() {
		return firestorm;
	}

	public void tick(IncidentManager manager) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		tickCounter++;
		if (tickCounter % Math.max(1, cfg.fireTickInterval) != 0) {
			return;
		}

		firebreaks.tick(Math.max(1, cfg.fireTickInterval));
		recountActiveCells();
		refreshChunkTickets(cfg);
		// Large flaming area → firestorm: self-heating, faster run, fire-driven wind
		firestorm = FirestormState.evaluate(level, cells);
		WindSystem wind = manager.wind();
		wind.applyFirestorm(firestorm);

		RandomSource random = level.getRandom();
		int spreadBudget = cfg.maxSpreadChecksPerTick;
		int emberBudget = cfg.maxEmbersPerTick;
		float timeScale = FireSpreadMath.timeScale(cfg.fireTickInterval);

		List<Long> keys = new ArrayList<>(cells.keySet());
		// Shuffle lightly by rotating start index for fairness
		int start = keys.isEmpty() ? 0 : (int) (level.getGameTime() % Math.max(1, keys.size()));

		for (int i = 0; i < keys.size(); i++) {
			long key = keys.get((i + start) % keys.size());
			BlockPos pos = BlockPos.of(key);
			if (!level.isLoaded(pos)) {
				continue;
			}
			FireCell cell = cells.get(key);
			if (cell == null) {
				continue;
			}
			boolean stormAffectsCell = firestorm.affects(key, cell);
			float effectiveWindSpeed = stormAffectsCell ? wind.firestormSpeed() : wind.speed();
			tickCell(pos, cell, wind, manager, random, cfg, timeScale, effectiveWindSpeed);

			BurnStage stage = cell.stage();
			// Firestorm self-heating: the mass of fire dries and heats fuel harder
			if (stormAffectsCell && stage.hasVisibleFlames()) {
				cell.addHeat(cfg.firestormSelfHeat * firestorm.intensity * timeScale);
				cell.addMoisture(-0.6F * firestorm.intensity * timeScale);
			}

			if (stage.hasVisibleFlames() && spreadBudget > 0) {
				spreadBudget -= spreadFrom(pos, cell, wind, cfg, timeScale, spreadBudget);
			}
			// Heat-lifted plant sparks — amplified in firestorm
			if (stage.hasVisibleFlames() && emberBudget > 0 && cfg.sparksEnabled) {
				float emberBase = Math.max(0.08F, cell.material().emberRate);
				float heatBonus = cell.heat01() * cfg.sparkHeatBonus;
				float fullyInvolved = stage == BurnStage.FULLY_INVOLVED ? 1.35F : 1.0F;
				float windFactor = 0.35F + effectiveWindSpeed * 0.9F;
				float chance = emberBase * windFactor * fullyInvolved * (0.45F + heatBonus);
				if (cell.material().crownFuel) {
					chance *= 1.45F;
				}
				if (stormAffectsCell) {
					chance *= firestorm.sparkMultiplier;
				}
				float stepChance = FireSpreadMath.probabilityForStep(Math.min(0.92F, chance), timeScale);
				if (random.nextFloat() < stepChance) {
					if (embers.spawn(
						level,
						pos,
						wind,
						effectiveWindSpeed,
						cell.incidentId,
						cfg,
						cell.heat01(),
						cell.material().flameIntensity,
						cell.material()
					)) {
						emberBudget--;
					}
				}
			}

			// Visual: light smoke for hotspots / flames (heavier column in firestorm)
			if (cfg.smokeEnabled && stage.isBurning() && random.nextInt(stormAffectsCell ? 4 : 8) == 0) {
				level.sendParticles(
					stage.hasVisibleFlames() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
					stormAffectsCell ? 2 : 1, 0.1, 0.25, 0.1, 0.01
				);
			}

			// Unheated fuel does not need a retained simulation cell. Spent and
			// extinguished cells stay as fuel-history markers and cannot reset.
			if (stage == BurnStage.UNBURNED && (cell.heat & 0xFF) == 0 && cell.reignitionTimer <= 0) {
				cells.remove(key);
				activeCellCount = Math.max(0, activeCellCount - 1);
			}
		}

		embers.tick(level, this, cfg);
		BurningTreeCollapse.tickCleanup(level);
	}

	/**
	 * Keep a bounded buffer of chunks around the active fire front loaded.
	 *
	 * <p>Without this, every spread resolver stops at {@link ServerLevel#isLoaded}
	 * and a wildfire forms an artificial straight edge on the chunk boundary.
	 * Overlapping ticket regions are coalesced before the hard centre limit is
	 * applied.</p>
	 */
	private void refreshChunkTickets(ForestFireConfig.Data cfg) {
		int radius = cfg.chunkUnloadRetentionRadius;
		if (radius <= 0 || cells.isEmpty()) {
			releaseChunkTickets();
			return;
		}

		Map<Long, ChunkActivity> activeChunks = new HashMap<>();
		for (Map.Entry<Long, FireCell> entry : cells.entrySet()) {
			FireCell cell = entry.getValue();
			int priority = chunkActivityPriority(cell);
			if (priority <= 0) {
				continue;
			}
			long chunkKey = ChunkPos.pack(BlockPos.of(entry.getKey()));
			int heat = cell.heat & 0xFF;
			activeChunks.merge(
				chunkKey,
				new ChunkActivity(chunkKey, priority, heat),
				(left, right) -> new ChunkActivity(
					chunkKey,
					Math.max(left.priority, right.priority),
					Math.max(left.heat, right.heat)
				)
			);
		}

		if (activeChunks.isEmpty()) {
			releaseChunkTickets();
			return;
		}

		List<ChunkActivity> ordered = new ArrayList<>(activeChunks.values());
		ordered.sort(
			Comparator.comparingInt(ChunkActivity::priority).reversed()
				.thenComparing(Comparator.comparingInt(ChunkActivity::heat).reversed())
				.thenComparingLong(ChunkActivity::chunkKey)
		);

		int coverageMargin = Math.max(0, radius - 1);
		List<Long> selected = new ArrayList<>();
		Set<Long> covered = new HashSet<>();
		for (ChunkActivity activity : ordered) {
			if (selected.size() >= cfg.maxFireChunkTickets) {
				break;
			}
			if (!covered.contains(activity.chunkKey)) {
				selected.add(activity.chunkKey);
				int centreX = ChunkPos.getX(activity.chunkKey);
				int centreZ = ChunkPos.getZ(activity.chunkKey);
				for (int dx = -coverageMargin; dx <= coverageMargin; dx++) {
					for (int dz = -coverageMargin; dz <= coverageMargin; dz++) {
						covered.add(ChunkPos.pack(centreX + dx, centreZ + dz));
					}
				}
			}
		}
		Set<Long> desired = new HashSet<>(selected);

		Iterator<Map.Entry<Long, Integer>> iterator = activeFireChunkTickets.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, Integer> existing = iterator.next();
			if (!desired.contains(existing.getKey()) || existing.getValue() != radius) {
				level.getChunkSource().removeTicketWithRadius(
					FireChunkTickets.ACTIVE_FIRE,
					ChunkPos.unpack(existing.getKey()),
					existing.getValue()
				);
				iterator.remove();
			}
		}

		for (long chunkKey : selected) {
			// Re-adding the same type/level resets the safety timeout.
			level.getChunkSource().addTicketWithRadius(
				FireChunkTickets.ACTIVE_FIRE,
				ChunkPos.unpack(chunkKey),
				radius
			);
			activeFireChunkTickets.put(chunkKey, radius);
		}
	}

	private void releaseChunkTickets() {
		if (activeFireChunkTickets.isEmpty()) {
			return;
		}
		for (Map.Entry<Long, Integer> entry : activeFireChunkTickets.entrySet()) {
			level.getChunkSource().removeTicketWithRadius(
				FireChunkTickets.ACTIVE_FIRE,
				ChunkPos.unpack(entry.getKey()),
				entry.getValue()
			);
		}
		activeFireChunkTickets.clear();
	}

	private static int chunkActivityPriority(FireCell cell) {
		BurnStage stage = cell.stage();
		if (stage.hasVisibleFlames()) {
			return 4;
		}
		if (stage == BurnStage.IGNITED) {
			return 3;
		}
		if (stage == BurnStage.HEATING && (cell.heat & 0xFF) > 0) {
			return 2;
		}
		if (stage == BurnStage.SMOULDERING || stage == BurnStage.REIGNITION_RISK
			|| (stage == BurnStage.WET && (cell.heat & 0xFF) > 0)) {
			return 1;
		}
		return 0;
	}

	private record ChunkActivity(long chunkKey, int priority, int heat) {
	}

	private void tickCell(
		BlockPos pos,
		FireCell cell,
		WindSystem wind,
		IncidentManager manager,
		RandomSource random,
		ForestFireConfig.Data cfg,
		float timeScale,
		float effectiveWindSpeed
	) {
		FuelMaterial material = cell.material();
		BurnStage stage = cell.stage();

		// Rain wetness — mild; must not wipe a wildfire in seconds
		if (WeatherFireModel.isRainingAt(level, pos) && stage.hasVisibleFlames()) {
			cell.addMoisture(0.6F * timeScale);
			cell.addHeat(-0.8F * timeScale);
		} else if (WeatherFireModel.isRainingAt(level, pos)) {
			cell.addMoisture(1.2F * timeScale);
			cell.addHeat(-1.5F * timeScale);
		}

		// Dry wet blocks
		if (stage == BurnStage.WET) {
			float dryRate = 0.4F + effectiveWindSpeed * 0.6F + WeatherFireModel.temperature01(level, pos);
			if (!WeatherFireModel.isRainingAt(level, pos)) {
				cell.addMoisture(-dryRate * timeScale);
			}
			if (cell.moisture01() < 0.35F) {
				cell.setStage(BurnStage.UNBURNED);
			}
			return;
		}

		// Reignition risk countdown
		if (stage == BurnStage.REIGNITION_RISK) {
			if (cell.reignitionTimer > 0) {
				cell.reignitionTimer--;
			}
			// Residual heat
			if ((cell.heat & 0xFF) > 5) {
				cell.addHeat(-0.3F * timeScale);
			} else if (random.nextFloat() < FireSpreadMath.probabilityForStep(
				0.01F * (1.0F - cell.moisture01()),
				timeScale
			)) {
				cell.setStage(BurnStage.IGNITED);
				cell.setHeat(45);
			} else if (cell.reignitionTimer <= 0 && (cell.heat & 0xFF) < 8) {
				cell.setStage(BurnStage.EXTINGUISHED);
			}
			return;
		}

		if (stage == BurnStage.HEATING) {
			// Living leaves dry when heated
			if (material == FuelMaterial.LIVING_LEAVES) {
				cell.addMoisture(-0.8F * timeScale);
			}
			float igniteThreshold = FireSpreadMath.ignitionThreshold(material.igniteEase, cell.moisture01());
			if ((cell.heat & 0xFF) >= igniteThreshold) {
				cell.setStage(BurnStage.IGNITED);
			} else {
				cell.addHeat(-0.5F * timeScale); // cool if not fed
				if ((cell.heat & 0xFF) <= 0) {
					cell.setStage(BurnStage.UNBURNED);
				}
			}
			return;
		}

		if (stage == BurnStage.IGNITED) {
			cell.addHeat((2.0F + material.flameIntensity * 3.0F) * timeScale);
			cell.addMoisture(-1.5F * timeScale);
			if ((cell.heat & 0xFF) > 50) {
				cell.setStage(BurnStage.FLAMING);
				ensureFireVisual(pos);
			}
			return;
		}

		if (stage == BurnStage.FLAMING || stage == BurnStage.FULLY_INVOLVED) {
			float burnRate = stage == BurnStage.FULLY_INVOLVED ? 1.25F : 1.0F;
			// Slow fuel burn so leaves/logs last for a real firefight, not ~10s
			cell.consumeFuel(material.fuelConsumePerTick() * burnRate * timeScale);
			cell.addMoisture(-1.2F * timeScale);
			// Sustain heat while fuel remains
			float heatDelta = material.flameIntensity * 1.2F + 0.4F;
			if ((cell.fuel & 0xFF) < 15) {
				heatDelta -= 0.8F;
			}
			cell.addHeat(heatDelta * timeScale);
			if ((cell.heat & 0xFF) > 75 && stage == BurnStage.FLAMING) {
				cell.setStage(BurnStage.FULLY_INVOLVED);
			}
			// Structural damage for trunks — weakens then falls via Realistic Tree Felling
			if (material == FuelMaterial.TRUNK || material == FuelMaterial.LOG) {
				int integrityLoss = stage == BurnStage.FULLY_INVOLVED ? 2 : 1;
				if (random.nextFloat() < FireSpreadMath.probabilityForStep(0.5F, timeScale)) {
					int integrity = Math.max(0, (cell.integrity & 0xFF) - integrityLoss);
					cell.integrity = (byte) integrity;
				}
				int integrity = cell.integrity & 0xFF;
				// Cracking warning when near collapse
				if (cfg.treeCollapseEnabled && integrity > 0 && integrity < 40
					&& random.nextFloat() < FireSpreadMath.probabilityForStep(0.025F, timeScale)) {
					level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.45F, 0.5F + integrity * 0.01F);
				}
				float collapseChance = cfg.treeCollapseChance;
				if (stage == BurnStage.FULLY_INVOLVED) {
					collapseChance *= 2.0F;
				}
				if (integrity < 15) {
					collapseChance *= 2.5F;
				}
				if (cfg.treeCollapseEnabled
					&& integrity < 30
					&& (cell.fuel & 0xFF) < 70
					&& random.nextFloat() < FireSpreadMath.probabilityForStep(collapseChance, timeScale)) {
					BurningTreeCollapse.tryCollapse(level, this, pos, cell, wind, random);
				}
			}
			ensureFireVisual(pos);
			if ((cell.fuel & 0xFF) <= 0) {
				cell.setStage(material.longBurn ? BurnStage.SMOULDERING : BurnStage.CHARRED);
				cell.setHeat(material.longBurn ? 45 : 15);
				charBlock(pos, material);
				clearFireVisual(pos);
			}
			return;
		}

		if (stage == BurnStage.SMOULDERING) {
			cell.addHeat(-0.8F * timeScale);
			cell.consumeFuel(0.2F * timeScale);
			if ((cell.heat & 0xFF) < 12) {
				cell.setStage(BurnStage.REIGNITION_RISK);
				cell.reignitionTimer = (short) (300 + random.nextInt(600));
				charBlock(pos, material);
			} else if (random.nextFloat() < FireSpreadMath.probabilityForStep(0.02F, timeScale)
				&& cell.moisture01() < 0.3F) {
				// flare-up
				cell.setStage(BurnStage.FLAMING);
				cell.setHeat(55);
			}
			return;
		}

		if (stage == BurnStage.CHARRED) {
			cell.addHeat(-1.0F * timeScale);
		}
	}

	private int spreadFrom(
		BlockPos pos,
		FireCell cell,
		WindSystem wind,
		ForestFireConfig.Data cfg,
		float timeScale,
		int maxChecks
	) {
		int checks = 0;
		boolean stormAffectsSource = firestorm.affects(pos.asLong(), cell);
		float effectiveWindSpeed = stormAffectsSource ? wind.firestormSpeed() : wind.speed();
		FireDangerLevel danger = WeatherFireModel.danger(level, pos, effectiveWindSpeed);
		float dangerMul = danger.spreadMultiplier()
			* (stormAffectsSource ? firestorm.spreadMultiplier : 1.0F);
		float humidity = WeatherFireModel.humidity01(level, pos);
		// Large fires dry the air locally
		if (stormAffectsSource) {
			humidity *= 1.0F - 0.35F * firestorm.intensity;
		}

		boolean surfaceSource = isSurfaceFuel(cell.material());
		for (int[] offset : HORIZONTAL_OFFSETS) {
			if (checks >= maxChecks) {
				return checks;
			}
			BlockPos target = findHorizontalFuel(pos, offset[0], offset[1]);
			if (target != null) {
				checks++;
				attemptSpread(
					pos,
					target,
					cell,
					wind,
					effectiveWindSpeed,
					dangerMul,
					humidity,
					cfg,
					timeScale,
					surfaceSource ? SpreadPath.SURFACE : SpreadPath.CONTACT
				);
			}
		}

		// Convection carries heat upward through gaps; downward radiation is much weaker.
		BlockPos upward = findVerticalFuel(pos, Direction.UP, 4);
		if (upward != null && checks < maxChecks) {
			checks++;
			attemptSpread(
				pos,
				upward,
				cell,
				wind,
				effectiveWindSpeed,
				dangerMul,
				humidity,
				cfg,
				timeScale,
				SpreadPath.CONVECTION_UP
			);
		}
		BlockPos downward = findVerticalFuel(pos, Direction.DOWN, 2);
		if (downward != null && checks < maxChecks) {
			checks++;
			attemptSpread(
				pos,
				downward,
				cell,
				wind,
				effectiveWindSpeed,
				dangerMul,
				humidity,
				cfg,
				timeScale,
				SpreadPath.CONVECTION_DOWN
			);
		}

		// Crown-to-crown flame transfer only crosses a one-block canopy gap.
		// Wider gaps are handled by airborne embers, not teleporting direct fire.
		if (cell.material().crownFuel && cell.heat01() >= 0.65F) {
			for (int[] offset : HORIZONTAL_OFFSETS) {
				if (checks >= maxChecks) {
					return checks;
				}
				BlockPos target = findCrownFuel(pos, offset[0] * 2, offset[1] * 2);
				if (target != null) {
					checks++;
					attemptSpread(
						pos,
						target,
						cell,
						wind,
						effectiveWindSpeed,
						dangerMul,
						humidity,
						cfg,
						timeScale,
						SpreadPath.CROWN
					);
				}
			}
		}
		return checks;
	}

	private void attemptSpread(
		BlockPos from,
		BlockPos to,
		FireCell source,
		WindSystem wind,
		float effectiveWindSpeed,
		float dangerMul,
		float humidity,
		ForestFireConfig.Data cfg,
		float timeScale,
		SpreadPath path
	) {
		if (to.equals(from) || !level.isLoaded(to)) {
			return;
		}

		BlockState state = level.getBlockState(to);
		FuelMaterial material = FuelMaterial.of(state);
		if (!material.flammable() || (material == FuelMaterial.PEAT && !cfg.peatFireEnabled)) {
			return;
		}
		if (path == SpreadPath.CROWN && !material.crownFuel) {
			return;
		}

		FireCell target = cells.get(to.asLong());
		if (target != null && (target.stage().hasVisibleFlames() || (target.fuel & 0xFF) <= 0)) {
			return;
		}
		boolean needsActiveSlot = target == null
			|| target.stage() == BurnStage.CHARRED
			|| target.stage() == BurnStage.EXTINGUISHED;
		if (needsActiveSlot && activeCellCount >= cfg.maxBurningBlocksPerWorld) {
			return;
		}

		// Resolve the actual fuel block before checking a wet line or firebreak.
		float breakWind = effectiveWindSpeed
			+ (firestorm.affects(from.asLong(), source) ? firestorm.intensity * 0.4F : 0.0F);
		if (firebreaks.blocksSpread(from, to, breakWind, source.heat01())) {
			return;
		}

		float travelX = to.getX() - from.getX();
		float travelZ = to.getZ() - from.getZ();
		float horizontalDistance = (float) Math.sqrt(travelX * travelX + travelZ * travelZ);
		float distance = (float) Math.sqrt(
			travelX * travelX
				+ (to.getY() - from.getY()) * (float) (to.getY() - from.getY())
				+ travelZ * travelZ
		);
		float windMultiplier = horizontalDistance > 0.0F
			? FireSpreadMath.windFactor(effectiveWindSpeed, wind.dx(), wind.dz(), travelX, travelZ)
			: 1.0F;
		float slopeMultiplier = path == SpreadPath.SURFACE
			? FireSpreadMath.slopeFactor(
				to.getY() - from.getY(),
				horizontalDistance,
				cfg.uphillSpreadBonus,
				cfg.downhillSpreadPenalty
			)
			: 1.0F;
		float targetMoisture = target == null
			? WeatherFireModel.initialFuelMoisture01(level, to, material.baseMoisture)
			: target.moisture01();
		float transferMultiplier = switch (path) {
			case SURFACE -> cfg.surfaceSpreadMultiplier;
			case CONTACT -> 0.72F;
			case CONVECTION_UP -> 1.55F;
			case CONVECTION_DOWN -> 0.25F;
			case CROWN -> cfg.crownSpreadMultiplier * 1.10F;
		};
		if (material == FuelMaterial.PEAT) {
			transferMultiplier *= Mth.clamp(cfg.peatSpreadChance * 10.0F, 0.02F, 0.25F);
		}

		float heatTransfer = FireSpreadMath.heatFlux(
			source.heat01(),
			source.material().flameIntensity,
			material.igniteEase,
			dangerMul,
			windMultiplier,
			slopeMultiplier,
			humidity,
			targetMoisture,
			transferMultiplier,
			distance,
			WeatherFireModel.isRainingAt(level, to)
		);
		heatTransfer *= (0.35F + 0.65F * (float) Math.sqrt(source.fuel01()));
		heatTransfer *= cfg.radiantHeatMultiplier * timeScale;
		if (heatTransfer < 0.05F) {
			return;
		}

		if (target == null) {
			target = getOrCreate(to, material, source.incidentId);
		}
		target.incidentId = source.incidentId > 0 ? source.incidentId : target.incidentId;
		target.addMoisture(-heatTransfer * 0.025F);
		target.addHeat(heatTransfer);

		BurnStage targetStage = target.stage();
		if (targetStage == BurnStage.UNBURNED || targetStage == BurnStage.EXTINGUISHED
			|| (targetStage == BurnStage.WET && target.moisture01() <= 0.55F)) {
			target.setStage(BurnStage.HEATING);
			if (targetStage == BurnStage.EXTINGUISHED) {
				activeCellCount++;
			}
		}
	}

	private BlockPos findHorizontalFuel(BlockPos from, int dx, int dz) {
		for (int dy : HEIGHT_SEARCH) {
			BlockPos candidate = from.offset(dx, dy, dz);
			if (!level.isLoaded(candidate)) {
				continue;
			}
			FuelMaterial material = FuelMaterial.of(level.getBlockState(candidate));
			if (isSpreadFuel(material) && canReceiveHeat(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private BlockPos findVerticalFuel(BlockPos from, Direction direction, int maxDistance) {
		for (int distance = 1; distance <= maxDistance; distance++) {
			BlockPos candidate = from.relative(direction, distance);
			if (!level.isLoaded(candidate)) {
				return null;
			}
			BlockState state = level.getBlockState(candidate);
			FuelMaterial material = FuelMaterial.of(state);
			if (isSpreadFuel(material) && canReceiveHeat(candidate)) {
				return candidate;
			}
			if (!state.isAir() && !state.canBeReplaced()
				&& !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) {
				return null;
			}
		}
		return null;
	}

	private BlockPos findCrownFuel(BlockPos from, int dx, int dz) {
		for (int dy : HEIGHT_SEARCH) {
			BlockPos candidate = from.offset(dx, dy, dz);
			if (!level.isLoaded(candidate)) {
				continue;
			}
			FuelMaterial material = FuelMaterial.of(level.getBlockState(candidate));
			if (material.crownFuel && isSpreadFuel(material) && canReceiveHeat(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean isSpreadFuel(FuelMaterial material) {
		return material.flammable()
			&& (material != FuelMaterial.PEAT || ForestFireConfig.get().peatFireEnabled);
	}

	private boolean canReceiveHeat(BlockPos pos) {
		FireCell cell = cells.get(pos.asLong());
		return cell == null || (!cell.stage().hasVisibleFlames() && (cell.fuel & 0xFF) > 0);
	}

	private static boolean isSurfaceFuel(FuelMaterial material) {
		return material == FuelMaterial.GRASS
			|| material == FuelMaterial.DEAD_BUSH
			|| material == FuelMaterial.DRY_LEAVES
			|| material == FuelMaterial.PEAT;
	}

	private void recountActiveCells() {
		int count = 0;
		for (FireCell cell : cells.values()) {
			BurnStage stage = cell.stage();
			if (stage != BurnStage.CHARRED && stage != BurnStage.EXTINGUISHED) {
				count++;
			}
		}
		activeCellCount = count;
	}

	private enum SpreadPath {
		SURFACE,
		CONTACT,
		CONVECTION_UP,
		CONVECTION_DOWN,
		CROWN
	}

	private void ensureFireVisual(BlockPos pos) {
		BlockPos above = pos.above();
		if (level.isLoaded(above) && level.getBlockState(above).isAir()) {
			// Lightweight visual only — simulation owns the burn, not vanilla fire spread
			level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
		}
	}

	private void clearFireVisual(BlockPos pos) {
		BlockPos above = pos.above();
		if (level.isLoaded(above) && (level.getBlockState(above).is(Blocks.FIRE) || level.getBlockState(above).is(Blocks.SOUL_FIRE))) {
			level.removeBlock(above, false);
		}
	}

	private void charBlock(BlockPos pos, FuelMaterial material) {
		BlockState state = level.getBlockState(pos);
		if (material == FuelMaterial.GRASS || material == FuelMaterial.DEAD_BUSH) {
			if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
				|| state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH) || state.is(BlockTags.FLOWERS)) {
				level.removeBlock(pos, false);
			} else if (state.is(Blocks.GRASS_BLOCK)) {
				level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
			}
			return;
		}
		if (material == FuelMaterial.DRY_LEAVES || material == FuelMaterial.LIVING_LEAVES) {
			if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) {
				// Leave a chance of remaining scorched leaves, else remove
				if (level.getRandom().nextBoolean()) {
					level.removeBlock(pos, false);
				}
			}
			return;
		}
		if (material == FuelMaterial.LOG || material == FuelMaterial.TRUNK) {
			if (state.is(BlockTags.LOGS)) {
				// Prefer coal-like outcome without instant full destruction
				if (level.getRandom().nextFloat() < 0.35F) {
					level.setBlock(pos, Blocks.COAL_BLOCK.defaultBlockState(), 3);
				}
				// else remain as weakened log visually (state kept) — integrity already reduced
			}
		}
	}

	/** Hook for vanilla fire conversion inside incident areas. */
	public boolean shouldSuppressVanillaFire(BlockPos pos) {
		if (!ForestFireConfig.get().replaceVanillaFireInIncidents) {
			return false;
		}
		// If near any active cell, take over
		for (Direction dir : NEIGHBOURS) {
			if (cells.containsKey(pos.relative(dir).asLong()) || cells.containsKey(pos.asLong())) {
				return true;
			}
		}
		return cells.containsKey(pos.below().asLong());
	}

	public void adoptVanillaFire(BlockPos firePos) {
		BlockPos fuel = firePos.below();
		FireCell managed = getCell(fuel);
		if (managed != null && managed.stage().hasVisibleFlames()) {
			// This is the visual fire placed by ensureFireVisual. Its vanilla
			// tick is cancelled by the mixin; it must never refuel its owner.
			return;
		}
		int incidentId = 0;
		// Find nearest incident
		IncidentManager manager = IncidentManager.get(level);
		double best = Double.MAX_VALUE;
		for (FireIncident incident : manager.openIncidents()) {
			double d = incident.ignitionPos.distSqr(fuel);
			if (d < best) {
				best = d;
				incidentId = incident.id;
			}
		}
		if (incidentId == 0 && ForestFireConfig.get().allowNaturalIgnition) {
			// Create small unreported fire if far from incidents
			if (best > 64 * 64 && manager.openIncidents().size() < ForestFireConfig.get().maxActiveIncidents) {
				try {
					FireIncident created = manager.createAt(fuel, 2);
					incidentId = created.id;
					created.status = IncidentStatus.UNREPORTED;
				} catch (IllegalStateException ignored) {
				}
			}
		}
		tryIgnite(fuel, incidentId, 60, true);
		// Remove vanilla fire so we own the simulation
		if (level.getBlockState(firePos).is(Blocks.FIRE) || level.getBlockState(firePos).is(Blocks.SOUL_FIRE)) {
			// keep visual if fuel is flaming after ignite
			FireCell cell = getCell(fuel);
			if (cell == null || !cell.stage().hasVisibleFlames()) {
				level.removeBlock(firePos, false);
			}
		}
	}
}
