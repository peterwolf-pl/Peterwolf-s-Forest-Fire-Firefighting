package com.peterwolf.forestfire.fire.simulation;

/**
 * Lifecycle of a flammable block under wildfire simulation.
 * Compact ordinal values are stored in FireCell data.
 */
public enum BurnStage {
	UNBURNED,
	HEATING,
	IGNITED,
	FLAMING,
	FULLY_INVOLVED,
	SMOULDERING,
	CHARRED,
	EXTINGUISHED,
	WET,
	REIGNITION_RISK;

	public static BurnStage byOrdinal(int ordinal) {
		BurnStage[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return UNBURNED;
		}
		return values[ordinal];
	}

	public boolean isBurning() {
		return this == IGNITED || this == FLAMING || this == FULLY_INVOLVED || this == SMOULDERING;
	}

	public boolean hasVisibleFlames() {
		return this == FLAMING || this == FULLY_INVOLVED;
	}

	public boolean isHotspotRisk() {
		return this == SMOULDERING || this == REIGNITION_RISK || (this == CHARRED);
	}
}
