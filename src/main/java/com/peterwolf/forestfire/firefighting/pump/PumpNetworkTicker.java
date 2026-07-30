package com.peterwolf.forestfire.firefighting.pump;

import net.minecraft.server.level.ServerLevel;

/**
 * Reserved for global pump bookkeeping. Per-pump logic runs in block entity tickers.
 */
public final class PumpNetworkTicker {
	private PumpNetworkTicker() {
	}

	public static void tick(ServerLevel level) {
		// Intentionally empty — pump BEs tick themselves.
	}
}
