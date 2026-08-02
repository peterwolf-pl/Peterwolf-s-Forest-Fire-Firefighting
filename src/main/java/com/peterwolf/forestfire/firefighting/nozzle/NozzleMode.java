package com.peterwolf.forestfire.firefighting.nozzle;

/**
 * Handheld / ground nozzle spray patterns.
 * Pressure consumption and flow affect pump load and spray performance.
 */
public enum NozzleMode {
	SHUTOFF(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, "Shutoff"),
	STRAIGHT_STREAM(14.0F, 0.55F, 0.55F, 0.85F, 0.55F, "Straight Stream"),
	NARROW_FOG(8.0F, 1.5F, 1.0F, 0.65F, 0.85F, "Narrow Fog"),
	WIDE_FOG(4.0F, 2.6F, 1.25F, 0.40F, 1.15F, "Wide Fog");

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
