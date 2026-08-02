package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

/**
 * Temporary, non-persistent chunk loading for an active wildfire front.
 *
 * <p>The timeout is a safety net. FireSimulation refreshes and explicitly
 * removes its tickets, so a crash or interrupted lifecycle cannot leave chunks
 * permanently force-loaded.</p>
 */
public final class FireChunkTickets {
	private static final long SAFETY_TIMEOUT_TICKS = 200L;

	public static final TicketType ACTIVE_FIRE = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		ForestFireMod.id("active_fire"),
		new TicketType(SAFETY_TIMEOUT_TICKS, TicketType.FLAG_LOADING)
	);

	private FireChunkTickets() {
	}

	public static void register() {
		// Static initialization performs registration.
	}
}
