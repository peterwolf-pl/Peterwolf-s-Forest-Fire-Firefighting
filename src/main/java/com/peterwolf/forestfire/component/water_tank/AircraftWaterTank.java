package com.peterwolf.forestfire.component.water_tank;

import com.peterwolf.forestfire.config.ForestFireConfig;
import net.minecraft.util.Mth;

/**
 * Server-authoritative water tank for firefighting aircraft.
 * Amount is stored on the entity; this helper centralizes capacity math.
 */
public final class AircraftWaterTank {
	public enum IntakeStatus {
		IDLE,
		FILLING,
		TOO_SLOW,
		TOO_FAST,
		WATER_OUT_OF_RANGE,
		EXCESSIVE_BANK,
		EXCESSIVE_PITCH,
		RETRACTED,
		TANK_FULL,
		NOT_AIRBORNE,
		NO_FORWARD_SPEED
	}

	private AircraftWaterTank() {
	}

	public static int capacity() {
		return ForestFireConfig.get().firefightingAircraft.tankCapacity;
	}

	public static int intakeRate() {
		return ForestFireConfig.get().firefightingAircraft.waterIntakeRatePerTick;
	}

	public static int releaseRate() {
		return ForestFireConfig.get().firefightingAircraft.waterReleaseRatePerTick;
	}

	public static int clampAmount(int amount) {
		return Mth.clamp(amount, 0, capacity());
	}

	public static int add(int current, int delta) {
		return clampAmount(current + Math.max(0, delta));
	}

	public static int remove(int current, int delta) {
		return clampAmount(current - Math.max(0, delta));
	}

	public static float fillRatio(int amount) {
		int cap = capacity();
		if (cap <= 0) {
			return 0.0F;
		}
		return Mth.clamp(amount / (float) cap, 0.0F, 1.0F);
	}

	/**
	 * Cargo mass factor: 1.0 empty → 1.0 + bonus * fill * weightMultiplier when full.
	 */
	public static float cargoMassFactor(int amount) {
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		float fill = fillRatio(amount);
		return 1.0F + cfg.maxCargoMassBonus * fill * cfg.waterWeightPhysicsMultiplier;
	}

	public static String formatHud(int amount) {
		return String.format("WATER: %,d / %,d", amount, capacity());
	}
}
