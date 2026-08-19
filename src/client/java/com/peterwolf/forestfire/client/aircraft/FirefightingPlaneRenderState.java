package com.peterwolf.forestfire.client.aircraft;

import com.piotrek.peterwolfsplanes.client.PlaneRenderState;

public class FirefightingPlaneRenderState extends PlaneRenderState {
	public float hoseProgress;
	public float doorProgress;
	public float tankFill;
	public boolean releasing;
	/** Blocks above water surface; NaN if no water below. */
	public float waterAgl = Float.NaN;
}
