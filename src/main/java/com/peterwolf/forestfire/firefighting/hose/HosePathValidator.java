package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side validation of automatic hose routes before commitment.
 * Hoses may lie on the ground and attach to equipment — only solid walls block.
 */
public final class HosePathValidator {
	public enum Failure {
		NONE,
		TOO_FAR,
		INSUFFICIENT_HOSE,
		PATH_BLOCKED,
		INVALID_ENDPOINT,
		PORT_OCCUPIED,
		ALREADY_CONNECTED,
		CROSS_DIMENSION,
		NO_PATH
	}

	public record Result(boolean ok, Failure failure, String message, HosePath path, int requiredLength) {
		public static Result fail(Failure f, String message) {
			return new Result(false, f, message, HosePath.empty(), 0);
		}

		public static Result success(HosePath path, int required) {
			return new Result(true, Failure.NONE, "ok", path, required);
		}
	}

	/** Ignore solids this close to path endpoints (pump body / water cell). */
	private static final double ENDPOINT_GRACE = 1.35;
	/** Fraction of interior samples that must be solid before rejecting the segment. */
	private static final float BLOCK_FRACTION_LIMIT = 0.55F;

	private HosePathValidator() {
	}

	public static Result validate(
		ServerLevel level,
		HosePath path,
		int availableHose,
		int maxLength,
		boolean requireWaterEndpoint,
		BlockPos waterOrNull
	) {
		if (path == null || path.isEmpty() || path.points().size() < 2) {
			return Result.fail(Failure.NO_PATH, "Cannot connect hose: no path");
		}
		int required = Math.max(1, path.blockLength());
		if (required > maxLength) {
			return Result.fail(Failure.TOO_FAR, "Cannot connect hose: exceeds maximum length (" + maxLength + ")");
		}
		if (availableHose < required && ForestFireConfig.get().automaticHoseConsumesItems) {
			return Result.fail(Failure.INSUFFICIENT_HOSE,
				"Cannot connect hose: insufficient hose length (need " + required + ", have " + availableHose + ")");
		}

		// Soft wall check — short open-ground paths almost always pass
		var pts = path.points();
		int hardBlocks = 0;
		int checks = 0;
		for (int i = 1; i < pts.size(); i++) {
			SegmentScan scan = scanSegment(level, pts.get(i - 1).position(), pts.get(i).position());
			checks += scan.samples();
			hardBlocks += scan.blockedSamples();
		}
		if (checks > 0 && hardBlocks / (float) checks > BLOCK_FRACTION_LIMIT && hardBlocks >= 3) {
			return Result.fail(Failure.PATH_BLOCKED, "Cannot connect hose: path blocked by solid wall");
		}

		if (requireWaterEndpoint) {
			if (waterOrNull == null || !level.isLoaded(waterOrNull)) {
				return Result.fail(Failure.INVALID_ENDPOINT, "Cannot connect intake: invalid water source");
			}
			if (!com.peterwolf.forestfire.firefighting.intake.IntakeSourceResolver.isValidSource(level, waterOrNull)) {
				return Result.fail(Failure.INVALID_ENDPOINT, "Cannot connect intake: invalid water source");
			}
			double verticalLift = Math.max(0.0, path.start().y - path.end().y);
			if (verticalLift > ForestFireConfig.get().maximumIntakeVerticalLift + 1.0) {
				return Result.fail(Failure.INVALID_ENDPOINT,
					"Cannot connect intake: vertical lift too high (" + (int) Math.ceil(verticalLift) + " > "
						+ ForestFireConfig.get().maximumIntakeVerticalLift + ")");
			}
			if (required > ForestFireConfig.get().maximumIntakeHoseLength) {
				return Result.fail(Failure.TOO_FAR,
					"Cannot connect intake: hose too long (max " + ForestFireConfig.get().maximumIntakeHoseLength + ")");
			}
		}
		return Result.success(path, required);
	}

	private record SegmentScan(int samples, int blockedSamples) {
	}

	private static SegmentScan scanSegment(ServerLevel level, Vec3 a, Vec3 b) {
		double dist = a.distanceTo(b);
		if (dist < 0.05) {
			return new SegmentScan(0, 0);
		}
		int steps = Math.max(2, (int) Math.ceil(dist * 1.5));
		int samples = 0;
		int blocked = 0;
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			double x = a.x + (b.x - a.x) * t;
			double y = a.y + (b.y - a.y) * t;
			double z = a.z + (b.z - a.z) * t;
			Vec3 p = new Vec3(x, y, z);
			// Near pump / water / nozzle endpoints the hose is allowed through equipment
			if (p.distanceTo(a) < ENDPOINT_GRACE || p.distanceTo(b) < ENDPOINT_GRACE) {
				continue;
			}
			// Sample slightly above the hose rest height so we don't hit the floor block
			BlockPos pos = BlockPos.containing(x, y + 0.15, z);
			if (!level.isLoaded(pos)) {
				// Unloaded = not a hard fail for short outdoor lines
				continue;
			}
			samples++;
			if (isWallObstacle(level, pos)) {
				blocked++;
			}
		}
		return new SegmentScan(samples, blocked);
	}

	/**
	 * Only tall solid walls / full cubes block hose. Floors, fluids, plants, equipment: OK.
	 */
	public static boolean isWallObstacle(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (isNonBlocking(state)) {
			return false;
		}
		// Full cube solid that is not a floor the hose rests on
		return state.isSolid() && !isThinOrPassableShape(state);
	}

	private static boolean isNonBlocking(BlockState state) {
		if (state.isAir() || state.canBeReplaced()) {
			return true;
		}
		if (!state.getFluidState().isEmpty() || state.getFluidState().is(FluidTags.WATER)) {
			return true;
		}
		// Our equipment — hose attaches to these
		if (state.is(ModBlocks.PORTABLE_PUMP)
			|| state.is(ModBlocks.HOSE_SPLITTER)
			|| state.is(ModBlocks.INTAKE_STRAINER)
			|| state.is(ModBlocks.GROUND_NOZZLE)
			|| state.is(ModBlocks.PORTABLE_SPRINKLER)
			|| state.is(ModBlocks.WATER_TANK_SMALL)
			|| state.is(ModBlocks.WATER_TANK_MEDIUM)
			|| state.is(ModBlocks.WATER_TANK_LARGE)) {
			return true;
		}
		if (state.is(BlockTags.LEAVES)
			|| state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.REPLACEABLE)
			|| state.is(BlockTags.FENCES)
			|| state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.WALLS)
			|| state.is(BlockTags.DOORS)
			|| state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.SLABS)
			|| state.is(BlockTags.STAIRS)) {
			return true;
		}
		return !state.isSolid();
	}

	private static boolean isThinOrPassableShape(BlockState state) {
		// Non-full cubes (slabs, stairs already handled) — allow hose over them
		try {
			return !state.isCollisionShapeFullBlock(null, BlockPos.ZERO);
		} catch (Exception ignored) {
			return false;
		}
	}
}
