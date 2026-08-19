package com.peterwolf.forestfire.fire.simulation;

/**
 * Pure wildfire spread calculations. Keeping these independent of world access
 * makes the directional and moisture behaviour deterministic and testable.
 */
public final class FireSpreadMath {
	private FireSpreadMath() {
	}

	/**
	 * Converts rates tuned for the default five-game-tick simulation step to the
	 * configured step length.
	 */
	public static float timeScale(int fireTickInterval) {
		return Math.max(1, fireTickInterval) / 5.0F;
	}

	/**
	 * Head fires run fastest, cross-wind fire keeps its base rate, and backing
	 * fire remains possible without an artificial minimum ignition chance.
	 */
	public static float windFactor(
		float windSpeed,
		float windX,
		float windZ,
		float travelX,
		float travelZ
	) {
		float travelLength = (float) Math.sqrt(travelX * travelX + travelZ * travelZ);
		if (travelLength < 1.0E-4F || windSpeed <= 0.0F) {
			return 1.0F;
		}
		float windLength = (float) Math.sqrt(windX * windX + windZ * windZ);
		if (windLength < 1.0E-4F) {
			return 1.0F;
		}
		float alignment = (travelX * windX + travelZ * windZ) / (travelLength * windLength);
		alignment = clamp(alignment, -1.0F, 1.0F);
		float speed = clamp(windSpeed, 0.0F, 1.65F);
		if (alignment >= 0.0F) {
			return clamp(1.0F + alignment * speed * 1.6F, 1.0F, 3.0F);
		}
		return clamp(1.0F / (1.0F + -alignment * speed * 1.6F), 0.35F, 1.0F);
	}

	/**
	 * Positive grade preheats uphill fuel; a descent remains reachable but slower.
	 */
	public static float slopeFactor(
		int verticalDelta,
		float horizontalDistance,
		float uphillBonus,
		float downhillPenalty
	) {
		if (verticalDelta == 0 || horizontalDistance < 1.0E-4F) {
			return 1.0F;
		}
		float grade = Math.abs(verticalDelta) / horizontalDistance;
		float normalizedGrade = Math.min(1.0F, grade);
		if (verticalDelta > 0) {
			return 1.0F + normalizedGrade * Math.max(0.0F, uphillBonus);
		}
		return Math.max(0.35F, 1.0F - normalizedGrade * Math.max(0.0F, downhillPenalty));
	}

	/**
	 * Heat delivered to a target during one default simulation step.
	 *
	 * <p>This is continuous preheating, not an ignition roll. Moist fuel absorbs
	 * much of the energy and rain strongly suppresses exposed fine fuels.</p>
	 */
	public static float heatFlux(
		float sourceHeat,
		float sourceFlameIntensity,
		float targetIgniteEase,
		float dangerMultiplier,
		float windMultiplier,
		float slopeMultiplier,
		float atmosphericHumidity,
		float targetMoisture,
		float transferMultiplier,
		float distance,
		boolean raining
	) {
		float sourcePower = (0.35F + clamp(sourceHeat, 0.0F, 1.0F) * 0.65F)
			* (0.40F + clamp(sourceFlameIntensity, 0.0F, 1.0F) * 0.60F);
		float fuelAbsorption = 0.55F + clamp(targetIgniteEase, 0.0F, 1.0F) * 0.55F;
		float moistureResistance = 1.0F
			- clamp(atmosphericHumidity, 0.0F, 1.0F) * 0.25F
			- clamp(targetMoisture, 0.0F, 1.0F) * 0.60F;
		moistureResistance = clamp(moistureResistance, 0.08F, 1.0F);
		float rainFactor = raining ? 0.22F : 1.0F;
		float attenuation = 1.0F / Math.max(1.0F, distance);

		float heat = 4.0F
			* sourcePower
			* fuelAbsorption
			* clamp(dangerMultiplier, 0.25F, 3.0F)
			* clamp(windMultiplier, 0.25F, 3.0F)
			* clamp(slopeMultiplier, 0.35F, 2.0F)
			* moistureResistance
			* Math.max(0.0F, transferMultiplier)
			* attenuation
			* rainFactor;
		return clamp(heat, 0.0F, 12.0F);
	}

	public static float ignitionThreshold(float igniteEase, float moisture) {
		float raw = 30.0F + clamp(moisture, 0.0F, 1.0F) * 40.0F;
		return raw * (1.0F - clamp(igniteEase, 0.0F, 1.0F) * 0.40F);
	}

	/**
	 * Scales a probability defined for one default step without introducing a
	 * per-attempt floor.
	 */
	public static float probabilityForStep(float baseProbability, float timeScale) {
		float probability = clamp(baseProbability, 0.0F, 1.0F);
		if (probability == 0.0F || timeScale <= 0.0F) {
			return 0.0F;
		}
		if (probability == 1.0F) {
			return 1.0F;
		}
		return (float) (1.0 - Math.pow(1.0 - probability, timeScale));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
