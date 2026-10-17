package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.NozzleLpmPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side LPM (left mouse) water control for connected nozzles.
 *
 * <ul>
 *   <li>Hold LPM — momentary water while key is down</li>
 *   <li>Quick double LPM — latch continuous flow until next LPM click</li>
 *   <li>LPM while continuous — turn off</li>
 * </ul>
 * Stream mode is changed with PPM (right-click), not here.
 */
public final class NozzleInputController {
	/** Max ticks between clicks to count as a double-click (~350 ms). */
	private static final int DOUBLE_CLICK_TICKS = 7;

	private static final Map<UUID, PlayerNozzleInput> INPUT = new HashMap<>();

	private NozzleInputController() {
	}

	public static void clear(UUID playerId) {
		INPUT.remove(playerId);
	}

	public static void clearAll() {
		INPUT.clear();
	}

	public static void handle(ServerPlayer player, NozzleLpmPayload payload) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
			stack = player.getOffhandItem();
		}
		if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
			return;
		}
		UUID endpointId = FireHoseNozzleItem.getEndpointId(stack);
		if (endpointId == null) {
			return;
		}
		HoseEndpointManager manager = HoseEndpointManager.get(level);
		HoseEndpoint ep = manager.get(endpointId).orElse(null);
		if (ep == null || !ep.connected) {
			return;
		}

		PlayerNozzleInput state = INPUT.computeIfAbsent(player.getUUID(), id -> new PlayerNozzleInput());
		long now = level.getGameTime();

		switch (payload.action()) {
			case NozzleLpmPayload.CLICK -> onClick(player, manager, ep, state, now);
			case NozzleLpmPayload.HOLD -> onHold(player, manager, ep, state);
			case NozzleLpmPayload.RELEASE -> onRelease(player, manager, ep, state);
			default -> {
			}
		}
	}

	private static void onClick(
		ServerPlayer player,
		HoseEndpointManager manager,
		HoseEndpoint ep,
		PlayerNozzleInput state,
		long now
	) {
		ep.location = NozzleLocationState.HELD;
		ep.operatorId = player.getUUID();

		// Continuous latched: any LPM click turns it off
		if (state.continuous) {
			state.continuous = false;
			state.holding = false;
			state.suppressHoldUntilRelease = true;
			state.lastClickTick = -999;
			ep.closeValve();
			manager.setDirty();
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_flow_off"));
			return;
		}

		// Quick double-click → latch continuous
		if (state.lastClickTick >= 0 && now - state.lastClickTick <= DOUBLE_CLICK_TICKS) {
			if (!ep.mode.allowsFlow()) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_shutoff"));
				state.lastClickTick = now;
				return;
			}
			state.continuous = true;
			state.holding = true;
			state.suppressHoldUntilRelease = false;
			state.lastClickTick = -999;
			ep.valve = NozzleValveState.OPEN;
			manager.setDirty();
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_flow_on"));
			return;
		}

		// First click of a potential double — arm momentary hold
		state.lastClickTick = now;
		state.holding = true;
		state.suppressHoldUntilRelease = false;
		if (ep.mode.allowsFlow()) {
			ep.valve = NozzleValveState.OPEN;
			manager.setDirty();
		} else {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_shutoff"));
		}
	}

	private static void onHold(
		ServerPlayer player,
		HoseEndpointManager manager,
		HoseEndpoint ep,
		PlayerNozzleInput state
	) {
		if (state.suppressHoldUntilRelease) {
			return;
		}
		ep.location = NozzleLocationState.HELD;
		ep.operatorId = player.getUUID();
		state.holding = true;
		if (state.continuous) {
			// already open
			if (ep.mode.allowsFlow() && ep.valve != NozzleValveState.OPEN) {
				ep.valve = NozzleValveState.OPEN;
				manager.setDirty();
			}
			return;
		}
		// Momentary: keep valve open while LPM held
		if (ep.mode.allowsFlow() && ep.valve != NozzleValveState.OPEN) {
			ep.valve = NozzleValveState.OPEN;
			manager.setDirty();
		}
	}

	private static void onRelease(
		ServerPlayer player,
		HoseEndpointManager manager,
		HoseEndpoint ep,
		PlayerNozzleInput state
	) {
		state.holding = false;
		state.suppressHoldUntilRelease = false;
		// Continuous stays open until next click
		if (state.continuous) {
			return;
		}
		if (ep.valve == NozzleValveState.OPEN) {
			ep.closeValve();
			manager.setDirty();
		}
	}

	/** True if this player currently wants water (hold or continuous latch). */
	public static boolean wantsWater(UUID playerId) {
		PlayerNozzleInput state = INPUT.get(playerId);
		return state != null && (state.continuous || state.holding);
	}

	public static boolean isContinuous(UUID playerId) {
		PlayerNozzleInput state = INPUT.get(playerId);
		return state != null && state.continuous;
	}

	private static final class PlayerNozzleInput {
		long lastClickTick = -999;
		boolean continuous;
		boolean holding;
		/** After unlatching continuous, ignore HOLD until the key is fully released. */
		boolean suppressHoldUntilRelease;
	}
}
