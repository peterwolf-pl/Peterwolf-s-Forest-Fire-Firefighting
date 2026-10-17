package com.peterwolf.forestfire.firefighting.intake;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.hose.HosePath;

/**
 * Simplified suction efficiency from hose length, vertical lift and bends.
 */
public final class IntakeEfficiencyCalculator {
	private IntakeEfficiencyCalculator() {
	}

	/**
	 * @return efficiency 0..1
	 */
	public static float calculate(HosePath path, double verticalLift, boolean hasStrainer) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		float efficiency = 1.0F;

		int length = path.blockLength();
		if (cfg.intakeLengthPressureLoss) {
			efficiency -= length * 0.012F;
		}

		// Vertical lift is the main suction cost
		double liftRatio = verticalLift / Math.max(1.0, cfg.maximumIntakeVerticalLift);
		efficiency -= (float) (liftRatio * 0.35);

		// Bends / control points
		int bends = Math.max(0, path.points().size() - 2);
		efficiency -= bends * 0.015F;

		if (!hasStrainer) {
			efficiency -= 0.05F;
		}

		return Math.max(0.15F, Math.min(1.0F, efficiency));
	}

	public static double verticalLift(double pumpY, double waterY) {
		return Math.max(0.0, pumpY - waterY);
	}
}
