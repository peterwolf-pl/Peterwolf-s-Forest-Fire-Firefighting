package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.config.ForestFireConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side validation of automatic hose routes before commitment.
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
			return Result.fail(Failure.NO_PATH, "Cannot connect hose: path blocked");
		}
		int required = path.blockLength();
		if (required > maxLength) {
			return Result.fail(Failure.TOO_FAR, "Cannot connect hose: exceeds maximum length (" + maxLength + ")");
		}
		if (availableHose < required && ForestFireConfig.get().automaticHoseConsumesItems) {
			return Result.fail(Failure.INSUFFICIENT_HOSE,
				"Cannot connect hose: insufficient hose length (need " + required + ", have " + availableHose + ")");
		}
		// Sample segments for solid walls
		var pts = path.points();
		for (int i = 1; i < pts.size(); i++) {
			if (segmentBlocked(level, pts.get(i - 1).position(), pts.get(i).position())) {
				return Result.fail(Failure.PATH_BLOCKED, "Cannot connect hose: path blocked");
			}
		}
		if (requireWaterEndpoint) {
			if (waterOrNull == null || !level.isLoaded(waterOrNull)) {
				return Result.fail(Failure.INVALID_ENDPOINT, "Cannot connect intake: invalid water source");
			}
			if (!com.peterwolf.forestfire.firefighting.intake.IntakeSourceResolver.isValidSource(level, waterOrNull)) {
				return Result.fail(Failure.INVALID_ENDPOINT, "Cannot connect intake: invalid water source");
			}
			// Vertical lift check
			double lift = path.start().y - path.end().y;
			// For intake: pump is higher than water — lift is positive when pump.y > water.y
			// start is pump, end is water for intake paths we generate pump→water
			double verticalLift = Math.max(0.0, path.start().y - path.end().y);
			if (verticalLift > ForestFireConfig.get().maximumIntakeVerticalLift + 0.5) {
				return Result.fail(Failure.INVALID_ENDPOINT,
					"Cannot connect intake: vertical lift too high (" + (int) verticalLift + " > "
						+ ForestFireConfig.get().maximumIntakeVerticalLift + ")");
			}
			if (required > ForestFireConfig.get().maximumIntakeHoseLength) {
				return Result.fail(Failure.TOO_FAR,
					"Cannot connect intake: hose too long (max " + ForestFireConfig.get().maximumIntakeHoseLength + ")");
			}
		}
		return Result.success(path, required);
	}

	private static boolean segmentBlocked(ServerLevel level, Vec3 a, Vec3 b) {
		double dist = a.distanceTo(b);
		int steps = Math.max(2, (int) Math.ceil(dist * 2.0));
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			double x = a.x + (b.x - a.x) * t;
			double y = a.y + (b.y - a.y) * t;
			double z = a.z + (b.z - a.z) * t;
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!level.isLoaded(pos)) {
				return true;
			}
			BlockState state = level.getBlockState(pos);
			if (HosePathGenerator.isSolidObstacle(state)) {
				return true;
			}
		}
		return false;
	}
}
