package com.peterwolf.forestfire.firefighting.hose;

/**
 * Logical role of an automatic hose connection in the pump network.
 */
public enum HoseConnectionType {
	INTAKE,
	ATTACK,
	SUPPLY,
	SPLITTER_BRANCH;

	public static HoseConnectionType byName(String name) {
		if (name == null || name.isEmpty()) {
			return ATTACK;
		}
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return ATTACK;
		}
	}
}
