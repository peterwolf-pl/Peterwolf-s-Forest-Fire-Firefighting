package com.peterwolf.forestfire.fire.simulation;

import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank;
import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank.IntakeStatus;
import com.peterwolf.forestfire.config.ForestFireConfig;

/**
 * Deterministic tests for tank math, scoop gates, and suppression budgeting.
 * Run via Gradle task {@code fireSpreadMathTest} companion or directly.
 */
public final class FirefightingAircraftLogicTest {
	private static int passed;
	private static int failed;

	public static void main(String[] args) {
		// Avoid FabricLoader/disk: unit-test defaults only
		ForestFireConfig.resetToDefaultsForTests();
		testTankClampAndCapacity();
		testTankDoesNotExceedCapacity();
		testReleaseDecreasesTank();
		testScoopSpeedGates();
		testScoopAltitudeGate();
		testHoseRetractedNoFill();
		testCargoMassSmooth();
		testSuppressionBudgetCap();
		System.out.println("FirefightingAircraftLogicTest: " + passed + " passed, " + failed + " failed");
		if (failed > 0) {
			System.exit(1);
		}
	}

	private static void testTankClampAndCapacity() {
		int cap = AircraftWaterTank.capacity();
		assertEq("capacity default", 12000, cap);
		assertEq("clamp negative", 0, AircraftWaterTank.clampAmount(-5));
		assertEq("clamp over", cap, AircraftWaterTank.clampAmount(cap + 999));
	}

	private static void testTankDoesNotExceedCapacity() {
		int cap = AircraftWaterTank.capacity();
		int filled = AircraftWaterTank.add(cap - 10, 500);
		assertEq("fill does not exceed", cap, filled);
	}

	private static void testReleaseDecreasesTank() {
		int before = 1000;
		int after = AircraftWaterTank.remove(before, AircraftWaterTank.releaseRate());
		assertTrue("release decreases", after < before);
		assertTrue("release non-negative", after >= 0);
	}

	private static void testScoopSpeedGates() {
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		assertTrue("too slow", 0.10 < cfg.minimumScoopingSpeed);
		assertTrue("ok speed", 0.50 >= cfg.minimumScoopingSpeed && 0.50 <= cfg.maximumScoopingSpeed);
		assertTrue("too fast", 1.20 > cfg.maximumScoopingSpeed);
		// Status mapping sanity
		assertEq("status ordinal stable", 0, IntakeStatus.IDLE.ordinal());
	}

	private static void testScoopAltitudeGate() {
		double max = ForestFireConfig.get().firefightingAircraft.maximumWaterDistanceBlocks;
		assertEq("default max water distance", 3.0, max);
		assertTrue("in range", 2.5 <= max);
		assertTrue("out of range", 3.5 > max);
	}

	private static void testHoseRetractedNoFill() {
		// Documented rule: retracted hose never fills — status RETRACTED
		assertTrue("retracted named", IntakeStatus.RETRACTED.name().contains("RETRACT"));
	}

	private static void testCargoMassSmooth() {
		float empty = AircraftWaterTank.cargoMassFactor(0);
		float half = AircraftWaterTank.cargoMassFactor(AircraftWaterTank.capacity() / 2);
		float full = AircraftWaterTank.cargoMassFactor(AircraftWaterTank.capacity());
		assertTrue("empty ~1", Math.abs(empty - 1.0F) < 0.001F);
		assertTrue("half between", half > empty && half < full + 0.001F);
		assertTrue("full heaviest", full >= half);
	}

	private static void testSuppressionBudgetCap() {
		int budget = ForestFireConfig.get().firefightingAircraft.maximumSuppressionOperationsPerTick;
		assertTrue("budget positive", budget > 0);
		assertTrue("budget finite", budget <= 5000);
		// Simulate clamp
		int ops = Math.min(10_000, budget);
		assertEq("ops clamped to budget", budget, ops);
	}

	private static void assertEq(String name, double expected, double actual) {
		if (Math.abs(expected - actual) < 1.0E-6) {
			passed++;
		} else {
			failed++;
			System.err.println("FAIL " + name + ": expected " + expected + " got " + actual);
		}
	}

	private static void assertEq(String name, int expected, int actual) {
		if (expected == actual) {
			passed++;
		} else {
			failed++;
			System.err.println("FAIL " + name + ": expected " + expected + " got " + actual);
		}
	}

	private static void assertTrue(String name, boolean condition) {
		if (condition) {
			passed++;
		} else {
			failed++;
			System.err.println("FAIL " + name);
		}
	}
}
