package com.peterwolf.forestfire.compat.planes;

import com.peterwolf.forestfire.client.aircraft.FirefightingPlanesClient;

/**
 * Client bootstrap for Planes firefighting aircraft.
 * Loaded reflectively only when Planes is present.
 */
public final class PlanesCompatClient {
	private PlanesCompatClient() {
	}

	public static void init() {
		FirefightingPlanesClient.init();
	}
}
