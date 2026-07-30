package com.peterwolf.forestfire.fire.simulation;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentStatus;
import com.peterwolf.forestfire.fire.spread.EmberSystem;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import com.peterwolf.forestfire.world.FirebreakTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative wildfire simulation.
 * Stores minimum per-block state and ticks only active cells with hard caps.
 */
public final class FireSimulation {
	private static final Map<ServerLevel, FireSimulation> CACHE = new HashMap<>();
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	private static final Direction[] NEIGHBOURS = Direction.values();

	private final ServerLevel level;
	private final Map<Long, FireCell> cells = new ConcurrentHashMap<>();
	private final EmberSystem embers = new EmberSystem();
	private final FirebreakTracker firebreaks = new FirebreakTracker();
	private int tickCounter;

	private FireSimulation(ServerLevel level) {
		this.level = level;
	}

	public static FireSimulation get(ServerLevel level) {
		return CACHE.computeIfAbsent(level, FireSimulation::new);
	}

	public static void invalidate(ServerLevel level) {
		CACHE.remove(level);
	}

	public static void clearAll() {
		CACHE.clear();
	}

	public FirebreakTracker firebreaks() {
		return firebreaks;
	}

	public Map<Long, FireCell> cells() {
		return cells;
	}

	public FireCell getCell(BlockPos pos) {
		return cells.get(pos.asLong());
	}

	public FireCell getOrCreate(BlockPos pos, FuelMaterial material, int incidentId) {
		return cells.computeIfAbsent(pos.asLong(), key -> new FireCell(material, incidentId));
	}

	public void loadCells(List<FireCellRecord> records) {
		cells.clear();
		for (FireCellRecord record : records) {
			cells.put(record.pos().asLong(), record.toCell());
		}
	}

	public List<FireCellRecord> exportCells() {
		List<FireCellRecord> list = new ArrayList<>(cells.size());
		for (Map.Entry<Long, FireCell> entry : cells.entrySet()) {
			list.add(FireCellRecord.from(BlockPos.of(entry.getKey()), entry.getValue()));
		}
		return list;
	}

	public int igniteArea(BlockPos center, int radius, int incidentId) {
		int count = 0;
		int r = Math.max(1, radius);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = -2; dy <= 6; dy++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (tryIgnite(cursor.immutable(), incidentId, 55 + level.getRandom().nextInt(30), true)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	public boolean tryIgnite(BlockPos pos, int incidentId, int heat, boolean force) {
		if (cells.size() >= ForestFireConfig.get().maxBurningBlocksPerWorld && !cells.containsKey(pos.asLong())) {
			return false;
		}
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		FuelMaterial material = FuelMaterial.of(state);
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
		if (cell.stage() == BurnStage.WET && cell.moisture01() > 0.55F && !force) {
			return false;
		}
		cell.incidentId = incidentId > 0 ? incidentId : cell.incidentId;
		cell.addHeat(heat);
		if (cell.stage() == BurnStage.UNBURNED || cell.stage() == BurnStage.WET || cell.stage() == BurnStage.EXTINGUISHED) {
			cell.setStage(BurnStage.HEATING);
		}
		if (cell.heat01() > 0.35F * (1.0F + cell.moisture01()) || force) {
			cell.setStage(BurnStage.IGNITED);
			cell.heat = (byte) Math.max(cell.heat & 0xFF, 50);
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
					if (cell == null && material.flammable()) {
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
	}

	public void extinguishIncident(int incidentId) {
		for (FireCell cell : cells.values()) {
			if (cell.incidentId == incidentId) {
				cell.heat = 0;
				cell.addMoisture(40);
				cell.setStage(BurnStage.EXTINGUISHED);
				cell.reignitionTimer = 0;
			}
		}
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

	public void tick(IncidentManager manager) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		tickCounter++;
		if (tickCounter % Math.max(1, cfg.fireTickInterval) != 0) {
			return;
		}

		firebreaks.tick();
		WindSystem wind = manager.wind();
		RandomSource random = level.getRandom();
		int spreadBudget = cfg.maxSpreadChecksPerTick;
		int emberBudget = cfg.maxEmbersPerTick;

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
			tickCell(pos, cell, wind, manager, random, cfg);

			BurnStage stage = cell.stage();
			if (stage.hasVisibleFlames() && spreadBudget > 0) {
				spreadBudget -= spreadFrom(pos, cell, wind, manager, random, cfg);
			}
			if (stage.hasVisibleFlames() && emberBudget > 0 && cell.material().emberRate > 0) {
				if (random.nextFloat() < cell.material().emberRate * wind.speed() * 0.5F) {
					if (embers.spawn(level, pos, wind, cell.incidentId, cfg)) {
						emberBudget--;
					}
				}
			}

			// Visual: light smoke for hotspots / flames
			if (cfg.smokeEnabled && stage.isBurning() && random.nextInt(8) == 0) {
				level.sendParticles(
					stage.hasVisibleFlames() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
					1, 0.1, 0.2, 0.1, 0.01
				);
			}

			// Cleanup dead cells
			if ((stage == BurnStage.UNBURNED || stage == BurnStage.EXTINGUISHED || stage == BurnStage.CHARRED)
				&& (cell.heat & 0xFF) == 0 && cell.reignitionTimer <= 0) {
				if (stage == BurnStage.CHARRED || stage == BurnStage.EXTINGUISHED) {
					// keep charred briefly then drop
					if (random.nextInt(40) == 0 && stage == BurnStage.EXTINGUISHED) {
						cells.remove(key);
					}
				}
			}
		}

		embers.tick(level, this, cfg);
	}

	private void tickCell(
		BlockPos pos,
		FireCell cell,
		WindSystem wind,
		IncidentManager manager,
		RandomSource random,
		ForestFireConfig.Data cfg
	) {
		FuelMaterial material = cell.material();
		BurnStage stage = cell.stage();

		// Rain wetness
		if (WeatherFireModel.isRainingAt(level, pos)) {
			cell.addMoisture(2.0F);
			cell.addHeat(-3.0F);
		}

		// Dry wet blocks
		if (stage == BurnStage.WET) {
			float dryRate = 0.4F + wind.speed() * 0.6F + WeatherFireModel.temperature01(level, pos);
			if (!WeatherFireModel.isRainingAt(level, pos)) {
				cell.addMoisture(-dryRate);
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
				cell.addHeat(-0.3F);
			} else if (random.nextFloat() < 0.01F * (1.0F - cell.moisture01())) {
				cell.setStage(BurnStage.IGNITED);
				cell.heat = 45;
			} else if (cell.reignitionTimer <= 0 && (cell.heat & 0xFF) < 8) {
				cell.setStage(BurnStage.EXTINGUISHED);
			}
			return;
		}

		if (stage == BurnStage.HEATING) {
			// Living leaves dry when heated
			if (material == FuelMaterial.LIVING_LEAVES) {
				cell.addMoisture(-0.8F);
			}
			float igniteThreshold = 30.0F + cell.moisture01() * 40.0F;
			if ((cell.heat & 0xFF) >= igniteThreshold * (1.0F - material.igniteEase * 0.4F)) {
				cell.setStage(BurnStage.IGNITED);
			} else {
				cell.addHeat(-0.5F); // cool if not fed
				if ((cell.heat & 0xFF) <= 0) {
					cell.setStage(BurnStage.UNBURNED);
				}
			}
			return;
		}

		if (stage == BurnStage.IGNITED) {
			cell.addHeat(2.0F + material.flameIntensity * 3.0F);
			cell.addMoisture(-1.5F);
			if ((cell.heat & 0xFF) > 50) {
				cell.setStage(BurnStage.FLAMING);
				ensureFireVisual(pos);
			}
			return;
		}

		if (stage == BurnStage.FLAMING || stage == BurnStage.FULLY_INVOLVED) {
			float burnRate = stage == BurnStage.FULLY_INVOLVED ? 1.6F : 1.0F;
			cell.consumeFuel(burnRate * (0.6F + material.flameIntensity));
			cell.addMoisture(-2.0F);
			cell.addHeat(material.flameIntensity * 1.5F - 0.5F);
			if ((cell.heat & 0xFF) > 75 && stage == BurnStage.FLAMING) {
				cell.setStage(BurnStage.FULLY_INVOLVED);
			}
			// Structural damage for trunks / buildings
			if (material.longBurn) {
				int integrity = (cell.integrity & 0xFF) - 1;
				cell.integrity = (byte) Math.max(0, integrity);
				if (cfg.treeCollapseEnabled && material == FuelMaterial.TRUNK
					&& integrity < 25 && random.nextFloat() < cfg.treeCollapseChance) {
					collapseTree(pos, cell, wind, random);
				}
			}
			ensureFireVisual(pos);
			if ((cell.fuel & 0xFF) <= 0) {
				cell.setStage(material.longBurn ? BurnStage.SMOULDERING : BurnStage.CHARRED);
				cell.heat = (byte) (material.longBurn ? 40 : 10);
				charBlock(pos, material);
				clearFireVisual(pos);
			}
			return;
		}

		if (stage == BurnStage.SMOULDERING) {
			cell.addHeat(-0.8F);
			cell.consumeFuel(0.2F);
			if ((cell.heat & 0xFF) < 12) {
				cell.setStage(BurnStage.REIGNITION_RISK);
				cell.reignitionTimer = (short) (300 + random.nextInt(600));
				charBlock(pos, material);
			} else if (random.nextFloat() < 0.02F && cell.moisture01() < 0.3F) {
				// flare-up
				cell.setStage(BurnStage.FLAMING);
				cell.heat = 55;
			}
			return;
		}

		if (stage == BurnStage.CHARRED) {
			cell.addHeat(-1.0F);
		}
	}

	private int spreadFrom(
		BlockPos pos,
		FireCell cell,
		WindSystem wind,
		IncidentManager manager,
		RandomSource random,
		ForestFireConfig.Data cfg
	) {
		int checks = 0;
		FireDangerLevel danger = WeatherFireModel.danger(level, pos, wind);
		float dangerMul = danger.spreadMultiplier();
		float humidity = WeatherFireModel.humidity01(level, pos);

		// Direct + radiant to 6 neighbours and some windward extras
		for (Direction dir : NEIGHBOURS) {
			checks++;
			BlockPos target = pos.relative(dir);
			attemptSpread(pos, target, cell, wind, dangerMul, humidity, cfg, random, false);
		}

		// Crown fire: horizontal leaf-to-leaf jumps
		if (cell.material().crownFuel) {
			for (Direction dir : HORIZONTAL) {
				checks++;
				BlockPos target = pos.relative(dir, 1 + (wind.speed() > 0.5F ? 1 : 0));
				// Bias with wind
				float biasX = wind.dx() * wind.speed();
				float biasZ = wind.dz() * wind.speed();
				if (dir.getStepX() * biasX + dir.getStepZ() * biasZ < -0.1F && random.nextFloat() > 0.35F) {
					continue;
				}
				attemptSpread(pos, target, cell, wind, dangerMul * cfg.crownSpreadMultiplier, humidity, cfg, random, true);
			}
		}

		// Surface fire along ground
		if (cell.material() == FuelMaterial.GRASS || cell.material() == FuelMaterial.DEAD_BUSH) {
			for (Direction dir : HORIZONTAL) {
				checks++;
				BlockPos target = pos.relative(dir);
				attemptSpread(pos, target, cell, wind, dangerMul * cfg.surfaceSpreadMultiplier, humidity, cfg, random, false);
			}
		}

		return checks;
	}

	private void attemptSpread(
		BlockPos from,
		BlockPos to,
		FireCell source,
		WindSystem wind,
		float dangerMul,
		float humidity,
		ForestFireConfig.Data cfg,
		RandomSource random,
		boolean crown
	) {
		if (!level.isLoaded(to)) {
			return;
		}
		if (firebreaks.blocksSpread(from, to, wind.speed(), source.heat01())) {
			return;
		}
		BlockState state = level.getBlockState(to);
		FuelMaterial material = FuelMaterial.of(state);
		if (!material.flammable()) {
			// Try vegetation on top of solid blocks
			BlockPos above = to.above();
			if (level.isLoaded(above)) {
				material = FuelMaterial.of(level.getBlockState(above));
				if (material.flammable()) {
					to = above;
					state = level.getBlockState(to);
				} else {
					return;
				}
			} else {
				return;
			}
		}

		int dx = Integer.signum(to.getX() - from.getX());
		int dz = Integer.signum(to.getZ() - from.getZ());
		float slope = WeatherFireModel.slopeFactor(level, from, dx, dz);
		if (slope > 1.0F) {
			slope = 1.0F + (slope - 1.0F) * cfg.uphillSpreadBonus;
		} else if (slope < 1.0F) {
			slope = 1.0F - (1.0F - slope) * cfg.downhillSpreadPenalty;
		}

		// Wind alignment
		float windAlign = 1.0F + wind.speed() * (dx * wind.dx() + dz * wind.dz());
		windAlign = Mth.clamp(windAlign, 0.35F, 2.2F);

		float chance = source.material().flameIntensity * material.igniteEase * dangerMul * slope * windAlign;
		chance *= (1.0F - humidity * 0.55F);
		chance *= source.heat01();
		if (crown) {
			chance *= 1.2F;
		}
		if (WeatherFireModel.isRainingAt(level, to)) {
			chance *= 0.25F;
		}
		if (random.nextFloat() > Math.min(0.85F, chance * 0.35F)) {
			return;
		}

		float heatTransfer = 8.0F + source.heat01() * 18.0F * (crown ? 1.3F : 1.0F);
		// Radiant heat even if not igniting fully
		FireCell existing = cells.get(to.asLong());
		if (existing != null) {
			existing.addHeat(heatTransfer * 0.5F * cfg.radiantHeatMultiplier);
			if (existing.stage() == BurnStage.UNBURNED) {
				existing.setStage(BurnStage.HEATING);
			}
		} else {
			tryIgnite(to, source.incidentId, Math.round(heatTransfer), false);
		}
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

	private void collapseTree(BlockPos pos, FireCell cell, WindSystem wind, RandomSource random) {
		// Simplified staged collapse: remove a column of logs in wind direction, damage nearby
		level.playSound(null, pos, SoundEvents.BAMBOO_WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		Direction fall = wind.cardinal();
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int y = 0; y < 8; y++) {
			cursor.set(pos.getX(), pos.getY() + y, pos.getZ());
			if (!level.isLoaded(cursor)) {
				break;
			}
			BlockState state = level.getBlockState(cursor);
			if (!state.is(BlockTags.LOGS) && y > 0) {
				break;
			}
			if (state.is(BlockTags.LOGS)) {
				BlockPos fallPos = cursor.relative(fall, 1 + y / 2);
				level.removeBlock(cursor, false);
				if (level.getBlockState(fallPos).isAir()) {
					level.setBlock(fallPos, Blocks.COAL_BLOCK.defaultBlockState(), 3);
					tryIgnite(fallPos, cell.incidentId, 40, true);
				}
			}
		}
		cell.integrity = 0;
		cell.setStage(BurnStage.CHARRED);
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
