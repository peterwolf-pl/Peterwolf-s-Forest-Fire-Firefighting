package com.peterwolf.forestfire.simulation.water_drop;

import com.peterwolf.forestfire.api.ForestFireApi;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side aerial water payloads with a per-tick suppression budget.
 */
public final class WaterDropSimulator {
	private static final Map<ServerLevel, WaterDropSimulator> INSTANCES = new ConcurrentHashMap<>();

	private final List<AerialWaterPayload> payloads = new ArrayList<>();
	private int opsThisTick;
	private int totalAffectedBlocks;
	private int collectionChecks;
	private int tankSyncEvents;

	public static WaterDropSimulator get(ServerLevel level) {
		return INSTANCES.computeIfAbsent(level, ignored -> new WaterDropSimulator());
	}

	public static void getAndTick(ServerLevel level) {
		get(level).tick(level);
	}

	public static void clear(ServerLevel level) {
		INSTANCES.remove(level);
	}

	public void spawn(AerialWaterPayload payload) {
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		if (payloads.size() >= cfg.maxActiveWaterPayloads) {
			// Drop oldest to keep budget
			payloads.remove(0);
		}
		payloads.add(payload);
	}

	public void tick(ServerLevel level) {
		opsThisTick = 0;
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		int budget = cfg.maximumSuppressionOperationsPerTick;
		WindSystem wind = IncidentManager.get(level).wind();

		Iterator<AerialWaterPayload> it = payloads.iterator();
		while (it.hasNext()) {
			AerialWaterPayload payload = it.next();
			payload.age++;

			// Gravity + light drag + wind drift
			double windScale = 0.012D * wind.speed();
			Vec3 windVec = new Vec3(wind.dx() * windScale, 0.0D, wind.dz() * windScale);
			payload.velocity = payload.velocity.add(0.0D, -0.04D, 0.0D).scale(0.99D).add(windVec);
			payload.position = payload.position.add(payload.velocity);

			BlockPos pos = BlockPos.containing(payload.position);
			boolean hitGround = !level.getBlockState(pos).isAir()
				&& !level.getBlockState(pos).is(Blocks.FIRE)
				&& !level.getBlockState(pos).is(Blocks.SOUL_FIRE)
				&& level.getBlockState(pos).blocksMotion();
			boolean nearSurface = payload.velocity.y < 0.0D
				&& level.getBlockState(pos.below()).blocksMotion();

			if (hitGround || nearSurface || payload.position.y <= pos.getY() + 0.15D) {
				int ops = suppress(level, pos, payload, budget - opsThisTick);
				opsThisTick += ops;
				totalAffectedBlocks += ops;
				it.remove();
				continue;
			}

			// Mid-air pass through burning crown: light cooling along path
			if (opsThisTick < budget && level.getBlockState(pos).is(Blocks.FIRE)) {
				if (ForestFireApi.applyWater(level, pos, payload.strength * 0.35F, 1.0F)) {
					opsThisTick++;
					payload.remainingUnits = Math.max(0, payload.remainingUnits - 4);
				}
			}

			if (payload.isDead() || opsThisTick >= budget) {
				if (opsThisTick >= budget && !payload.isDead()) {
					// Force impact with remaining water when budget exhausted this tick
					int ops = suppress(level, pos, payload, Math.max(1, budget / 8));
					opsThisTick += ops;
					totalAffectedBlocks += ops;
				}
				it.remove();
			}
		}
	}

	private int suppress(ServerLevel level, BlockPos impact, AerialWaterPayload payload, int remainingBudget) {
		if (remainingBudget <= 0 || payload.remainingUnits <= 0) {
			return 0;
		}
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		float radius = payload.radius;
		int r = Math.max(1, Math.round(radius));
		float strength = payload.strength * Mth.clamp(payload.remainingUnits / 80.0F, 0.25F, 1.5F);
		int used = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// Elongated footprint along residual horizontal velocity
		Vec3 horiz = new Vec3(payload.velocity.x, 0.0D, payload.velocity.z);
		double horizLen = horiz.length();
		Vec3 trail = horizLen > 1.0E-4D ? horiz.normalize() : Vec3.ZERO;

		for (int i = 0; i <= r + 2 && used < remainingBudget; i++) {
			Vec3 sample = new Vec3(impact.getX() + 0.5, impact.getY(), impact.getZ() + 0.5)
				.subtract(trail.scale(i * 0.85D));
			cursor.set(sample.x, sample.y, sample.z);
			// Find solid / surface near sample
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos p = cursor.offset(0, dy, 0);
				if (ForestFireApi.applyWater(level, p, strength * (1.0F - i * 0.08F), radius * 0.65F)) {
					used++;
					break;
				}
				if (level.getBlockState(p).is(Blocks.FIRE) || level.getBlockState(p).is(Blocks.SOUL_FIRE)) {
					level.removeBlock(p, false);
					ForestFireApi.applyWater(level, p.below(), strength, radius * 0.5F);
					used++;
					break;
				}
			}
		}

		// Wetness duration override for aerial drops
		if (cfg.wetnessDurationTicks > 0) {
			// applyWater already marks wet via FireSimulation; config wetness is shared
		}
		payload.remainingUnits = 0;
		return used;
	}

	public int activePayloads() {
		return payloads.size();
	}

	public int opsThisTick() {
		return opsThisTick;
	}

	public int totalAffectedBlocks() {
		return totalAffectedBlocks;
	}

	public void recordCollectionCheck() {
		collectionChecks++;
	}

	public void recordTankSync() {
		tankSyncEvents++;
	}

	public int collectionChecks() {
		return collectionChecks;
	}

	public int tankSyncEvents() {
		return tankSyncEvents;
	}

	public String debugSummary() {
		return String.format(
			"payloads=%d opsTick=%d affected=%d collectChecks=%d tankSync=%d",
			payloads.size(), opsThisTick, totalAffectedBlocks, collectionChecks, tankSyncEvents
		);
	}
}
