package com.peterwolf.forestfire.firefighting.nozzle;

/**
 * Handheld / ground nozzle spray patterns.
 * Pressure consumption and flow affect pump load and spray performance.
 */
public enum NozzleMode {
	SHUTOFF(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, "Shutoff"),
	/** Long reach jet — strong point extinguish. */
	STRAIGHT_STREAM(18.0F, 0.75F, 1.15F, 0.80F, 0.55F, "Straight Stream"),
	/** Medium cone — vegetation / structure cooling. */
	NARROW_FOG(11.0F, 1.9F, 1.55F, 0.60F, 0.90F, "Narrow Fog"),
	/** Short wide shield — wet lines and radiant protection. */
	WIDE_FOG(6.0F, 3.2F, 1.75F, 0.40F, 1.20F, "Wide Fog");

	/** Max block reach at full pressure. */
	public final float range;
	/** Area radius / cone half-width. */
	public final float radius;
	/** Cooling strength multiplier. */
	public final float cooling;
	/** Relative pressure demand (0–1+). */
	public final float pressureDemand;
	/** Relative water flow demand. */
	public final float flowDemand;
	public final String label;

	NozzleMode(float range, float radius, float cooling, float pressureDemand, float flowDemand, String label) {
		this.range = range;
		this.radius = radius;
		this.cooling = cooling;
		this.pressureDemand = pressureDemand;
		this.flowDemand = flowDemand;
		this.label = label;
	}

	public NozzleMode next() {
		NozzleMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	/** Cycle only among spraying modes + shutoff, skipping nothing. */
	public NozzleMode cycleCombat() {
		return switch (this) {
			case SHUTOFF -> STRAIGHT_STREAM;
			case STRAIGHT_STREAM -> NARROW_FOG;
			case NARROW_FOG -> WIDE_FOG;
			case WIDE_FOG -> SHUTOFF;
		};
	}

	public static NozzleMode byOrdinal(int ordinal) {
		NozzleMode[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return STRAIGHT_STREAM;
		}
		return values[ordinal];
	}

	public static NozzleMode byName(String raw) {
		try {
			return valueOf(raw.toUpperCase());
		} catch (Exception e) {
			return STRAIGHT_STREAM;
		}
	}

	public boolean allowsFlow() {
		return this != SHUTOFF && range > 0.0F;
	}
}
