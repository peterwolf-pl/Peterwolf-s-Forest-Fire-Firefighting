package com.peterwolf.forestfire.firefighting.hose;

public enum HoseTension {
	SLACK,
	NORMAL,
	TIGHT,
	MAXIMUM;

	public static HoseTension fromRemaining(int remaining, int maxReach) {
		if (maxReach <= 0) {
			return MAXIMUM;
		}
		float ratio = remaining / (float) maxReach;
		if (ratio > 0.45F) {
			return SLACK;
		}
		if (ratio > 0.20F) {
			return NORMAL;
		}
		if (ratio > 0.02F) {
			return TIGHT;
		}
		return MAXIMUM;
	}
}
