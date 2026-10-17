package com.peterwolf.forestfire.firefighting.nozzle;

public enum NozzleValveState {
	CLOSED,
	OPEN;

	public static NozzleValveState byName(String raw) {
		try {
			return valueOf(raw.toUpperCase());
		} catch (Exception e) {
			return CLOSED;
		}
	}
}
