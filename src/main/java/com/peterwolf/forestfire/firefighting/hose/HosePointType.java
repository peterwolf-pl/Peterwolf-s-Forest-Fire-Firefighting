package com.peterwolf.forestfire.firefighting.hose;

/**
 * Semantic role of a control point along an automatic hose path.
 */
public enum HosePointType {
	PUMP_PORT,
	GROUND_ANCHOR,
	TERRAIN_POINT,
	SPLITTER_PORT,
	PLAYER_ENDPOINT,
	NOZZLE_ENDPOINT,
	WATER_ENDPOINT;

	public static HosePointType byName(String name) {
		if (name == null || name.isEmpty()) {
			return TERRAIN_POINT;
		}
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return TERRAIN_POINT;
		}
	}
}
