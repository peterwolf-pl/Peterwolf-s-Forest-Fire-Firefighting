package com.peterwolf.forestfire.client.nozzle;

import com.peterwolf.forestfire.firefighting.nozzle.FireHoseNozzleItem;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.NozzleLpmPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Client LPM (left mouse / attack key) → server nozzle water control.
 * Suppresses block-breaking while operating a connected nozzle.
 */
public final class NozzleClientInput {
	private static boolean wasAttackDown;

	private NozzleClientInput() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NozzleClientInput::onTick);
	}

	private static void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			if (wasAttackDown) {
				wasAttackDown = false;
				send(NozzleLpmPayload.RELEASE);
			}
			return;
		}
		if (!holdsConnectedNozzle(client)) {
			if (wasAttackDown) {
				wasAttackDown = false;
				send(NozzleLpmPayload.RELEASE);
			}
			return;
		}

		boolean down = client.options.keyAttack.isDown();
		if (down && !wasAttackDown) {
			send(NozzleLpmPayload.CLICK);
		}
		if (down) {
			// Keep momentary spray alive; also soft-cancel mining by resetting destroy progress
			send(NozzleLpmPayload.HOLD);
			if (client.gameMode != null) {
				client.gameMode.stopDestroyBlock();
			}
		}
		if (!down && wasAttackDown) {
			send(NozzleLpmPayload.RELEASE);
		}
		wasAttackDown = down;
	}

	private static boolean holdsConnectedNozzle(Minecraft client) {
		ItemStack main = client.player.getMainHandItem();
		if (main.is(ModItems.FIRE_HOSE_NOZZLE) && FireHoseNozzleItem.getEndpointId(main) != null) {
			return true;
		}
		ItemStack off = client.player.getOffhandItem();
		return off.is(ModItems.FIRE_HOSE_NOZZLE) && FireHoseNozzleItem.getEndpointId(off) != null;
	}

	private static void send(int action) {
		if (ClientPlayNetworking.canSend(NozzleLpmPayload.TYPE)) {
			ClientPlayNetworking.send(new NozzleLpmPayload(action));
		}
	}
}
