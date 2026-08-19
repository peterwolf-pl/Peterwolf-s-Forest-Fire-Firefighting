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
 * Aircraft dumps are intentionally much stronger than hand nozzles.
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

			// Stronger mid-air crown knockdown
			if (opsThisTick < budget && (level.getBlockState(pos).is(Blocks.FIRE)
				|| level.getBlockState(pos).is(Blocks.SOUL_FIRE))) {
				float midAir = payload.strength * cfg.aerialSuppressionMultiplier * 0.85F;
				if (ForestFireApi.applyWater(level, pos, midAir, 2.0F)) {
					opsThisTick++;
					payload.remainingUnits = Math.max(0, payload.remainingUnits - 2);
				}
				level.removeBlock(pos, false);
			}

			if (payload.isDead() || opsThisTick >= budget) {
				if (opsThisTick >= budget && !payload.isDead()) {
					int ops = suppress(level, pos, payload, Math.max(8, budget / 4));
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
		float radius = Math.max(payload.radius, cfg.dropBaseRadius * 0.85F);
		int r = Math.max(2, Math.round(radius));
		// Heavy water mass → high cooling; no soft 1.5× cap like the old formula
		float massScale = Mth.clamp(payload.remainingUnits / 40.0F, 0.75F, 4.0F);
		float strength = payload.strength * cfg.aerialSuppressionMultiplier * massScale;
		int used = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		Vec3 horiz = new Vec3(payload.velocity.x, 0.0D, payload.velocity.z);
		double horizLen = horiz.length();
		Vec3 trail = horizLen > 1.0E-4D ? horiz.normalize() : Vec3.ZERO;
		// Perpendicular for wider corridor
		Vec3 side = new Vec3(-trail.z, 0.0D, trail.x);

		int trailLen = r + 6;
		int halfWidth = Math.max(1, r / 2);

		for (int i = 0; i <= trailLen && used < remainingBudget; i++) {
			float alongFalloff = 1.0F - Math.min(0.75F, i * 0.06F);
			for (int s = -halfWidth; s <= halfWidth && used < remainingBudget; s++) {
				float sideFalloff = 1.0F - Math.min(0.7F, Math.abs(s) * 0.18F);
				Vec3 sample = new Vec3(impact.getX() + 0.5, impact.getY(), impact.getZ() + 0.5)
					.subtract(trail.scale(i * 0.9D))
					.add(side.scale(s * 0.95D));
				cursor.set(sample.x, sample.y, sample.z);

				float localStrength = strength * alongFalloff * sideFalloff;
				float localRadius = Math.max(1.5F, radius * 0.85F * sideFalloff);

				for (int dy = 3; dy >= -4; dy--) {
					BlockPos p = cursor.offset(0, dy, 0);
					boolean hit = ForestFireApi.applyWater(level, p, localStrength, localRadius);
					if (level.getBlockState(p).is(Blocks.FIRE) || level.getBlockState(p).is(Blocks.SOUL_FIRE)) {
						level.removeBlock(p, false);
						ForestFireApi.applyWater(level, p.below(), localStrength * 1.1F, localRadius);
						hit = true;
					}
					if (hit) {
						used++;
						// Soak neighbours once for hard knockdown
						if (used < remainingBudget) {
							ForestFireApi.applyWater(level, p.north(), localStrength * 0.55F, 1.2F);
							ForestFireApi.applyWater(level, p.south(), localStrength * 0.55F, 1.2F);
							ForestFireApi.applyWater(level, p.east(), localStrength * 0.55F, 1.2F);
							ForestFireApi.applyWater(level, p.west(), localStrength * 0.55F, 1.2F);
							used += 2;
						}
						break;
					}
				}
			}
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
