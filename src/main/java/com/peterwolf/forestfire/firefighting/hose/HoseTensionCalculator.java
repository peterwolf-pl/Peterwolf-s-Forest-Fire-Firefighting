package com.peterwolf.forestfire.firefighting.hose;

/**
 * Slack / tension from deployed hose length vs current path length.
 * Slack = Deployed − PathLength
 */
public final class HoseTensionCalculator {
	private HoseTensionCalculator() {
	}

	public static HoseTension fromSlack(double remainingSlack, int deployedLength) {
		if (deployedLength <= 0) {
			return HoseTension.MAXIMUM;
		}
		double ratio = remainingSlack / (double) deployedLength;
		if (remainingSlack <= 0.15) {
			return HoseTension.MAXIMUM;
		}
		if (ratio < 0.08 || remainingSlack < 1.0) {
			return HoseTension.TIGHT;
		}
		if (ratio < 0.25) {
			return HoseTension.NORMAL;
		}
		return HoseTension.SLACK;
	}

	public static double pathLength(HosePath path) {
		return path == null ? 0.0 : path.pathLength();
	}
}
