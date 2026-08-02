package com.peterwolf.forestfire.fire.simulation;

/**
 * Dependency-free deterministic checks run by the Gradle verification lifecycle.
 */
public final class FireSpreadMathTest {
	private FireSpreadMathTest() {
	}

	public static void main(String[] args) {
		fractionalStateAccumulates();
		windCreatesHeadFlankAndBackingFire();
		slopePreservesUphillFlatDownhillOrder();
		moistureAndRainReduceHeatFlux();
		ignitionThresholdRespondsToFuelAndMoisture();
		probabilityScalesWithElapsedTime();
	}

	private static void fractionalStateAccumulates() {
		FireCell cell = new FireCell(FuelMaterial.GRASS, 1);
		cell.setHeat(50);
		for (int i = 0; i < 10; i++) {
			cell.addHeat(-0.3F);
		}
		assertEquals(47, cell.heat & 0xFF, "ten -0.3 heat steps");

		cell.setFuel(10);
		for (int i = 0; i < 100; i++) {
			cell.consumeFuel(0.1F);
		}
		assertEquals(0, cell.fuel & 0xFF, "fractional fuel eventually reaches zero");

		cell.setMoisture(20);
		cell.addMoisture(0.4F);
		FireCell copy = cell.copy();
		assertClose(cell.moistureRemainder, copy.moistureRemainder, 0.0001F, "copy keeps residues");
	}

	private static void windCreatesHeadFlankAndBackingFire() {
		float head = FireSpreadMath.windFactor(0.7F, 1.0F, 0.0F, 1.0F, 0.0F);
		float flank = FireSpreadMath.windFactor(0.7F, 1.0F, 0.0F, 0.0F, 1.0F);
		float backing = FireSpreadMath.windFactor(0.7F, 1.0F, 0.0F, -1.0F, 0.0F);
		assertGreater(head, flank, "head fire outruns flank");
		assertGreater(flank, backing, "flank outruns backing fire");

		float calmEast = FireSpreadMath.windFactor(0.0F, 1.0F, 0.0F, 1.0F, 0.0F);
		float calmNorth = FireSpreadMath.windFactor(0.0F, 1.0F, 0.0F, 0.0F, -1.0F);
		assertClose(calmEast, calmNorth, 0.0001F, "calm spread is rotationally symmetric");
	}

	private static void slopePreservesUphillFlatDownhillOrder() {
		float uphill = FireSpreadMath.slopeFactor(1, 1.0F, 0.35F, 0.25F);
		float flat = FireSpreadMath.slopeFactor(0, 1.0F, 0.35F, 0.25F);
		float downhill = FireSpreadMath.slopeFactor(-1, 1.0F, 0.35F, 0.25F);
		assertGreater(uphill, flat, "uphill preheating");
		assertGreater(flat, downhill, "downhill remains slower but reachable");
	}

	private static void moistureAndRainReduceHeatFlux() {
		float dry = heatFlux(0.10F, 0.25F, false);
		float wet = heatFlux(0.85F, 0.80F, false);
		float rainy = heatFlux(0.10F, 0.80F, true);
		assertGreater(dry, wet, "wet fuel absorbs more heat");
		assertGreater(dry, rainy, "rain suppresses exposed-fuel heating");
	}

	private static float heatFlux(float moisture, float humidity, boolean raining) {
		return FireSpreadMath.heatFlux(
			0.85F,
			0.65F,
			0.90F,
			1.0F,
			1.0F,
			1.0F,
			humidity,
			moisture,
			1.0F,
			1.0F,
			raining
		);
	}

	private static void ignitionThresholdRespondsToFuelAndMoisture() {
		float dryGrass = FireSpreadMath.ignitionThreshold(0.90F, 0.10F);
		float wetLivingLeaves = FireSpreadMath.ignitionThreshold(0.45F, 0.80F);
		assertGreater(wetLivingLeaves, dryGrass, "wet living fuel needs more heat");
	}

	private static void probabilityScalesWithElapsedTime() {
		float oneStep = FireSpreadMath.probabilityForStep(0.08F, 1.0F);
		float fiveSteps = FireSpreadMath.probabilityForStep(0.08F, 5.0F);
		assertGreater(fiveSteps, oneStep, "elapsed time increases cumulative hazard");
		assertClose(
			fiveSteps,
			1.0F - (float) Math.pow(1.0F - oneStep, 5.0F),
			0.0001F,
			"hazard scaling formula"
		);
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertGreater(float greater, float lesser, String label) {
		if (!(greater > lesser)) {
			throw new AssertionError(label + ": expected " + greater + " > " + lesser);
		}
	}

	private static void assertClose(float expected, float actual, float tolerance, String label) {
		if (Math.abs(expected - actual) > tolerance) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}
}
