package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import com.peterwolf.forestfire.item.ModItems;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Lifecycle safety for connected nozzles: death, logout, tick, drop prevention.
 */
public final class NozzleEvents {
	private NozzleEvents() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level instanceof ServerLevel serverLevel) {
				com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager.get(serverLevel).tick(serverLevel);
				HoseEndpointManager.get(serverLevel).tick(serverLevel);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (player.level() instanceof ServerLevel level) {
				com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager.get(level).syncToPlayer(player);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			NozzleInputController.clear(player.getUUID());
			if (player.level() instanceof ServerLevel level) {
				HoseEndpointManager.get(level).releaseOperator(level, player.getUUID(), true);
			}
		});

		// Connected nozzle: LPM must not mine blocks / hit entities (water only, no attack)
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (holdsConnectedNozzle(player.getItemInHand(hand)) || holdsConnectedNozzle(player.getMainHandItem())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (holdsConnectedNozzle(player.getItemInHand(hand)) || holdsConnectedNozzle(player.getMainHandItem())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
				// Place nozzle on ground at death site — do not leave held item duplicates
				clearHeldEndpointItems(player);
				HoseEndpointManager.get(level).releaseOperator(level, player.getUUID(), true);
			}
		});

		// After respawn: strip any lingering connected nozzle stacks from inventory
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clearHeldEndpointItems(newPlayer));
	}

	/** Called when player tries to drop a connected nozzle. */
	public static boolean handleDropAttempt(ServerPlayer player, ItemStack stack) {
		if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
			return false;
		}
		String raw = stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
		if (raw == null || raw.isEmpty()) {
			return false; // free nozzle — allow normal drop
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return true;
		}
		try {
			UUID id = UUID.fromString(raw);
			HoseEndpointManager manager = HoseEndpointManager.get(level);
			HoseEndpoint ep = manager.get(id).orElse(null);
			if (ep == null) {
				return false;
			}
			// Block drop — instruct place or disconnect
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_no_drop"));
			// Optionally place at feet
			if (ep.valve == NozzleValveState.CLOSED) {
				net.minecraft.core.BlockPos feet = player.blockPosition();
				if (level.getBlockState(feet).isAir() || level.getBlockState(feet).canBeReplaced()) {
					manager.placeFromHand(level, player, ep, feet, player.getYRot(), player.getXRot());
				}
			}
			return true; // consume drop
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void clearHeldEndpointItems(ServerPlayer player) {
		NozzleInputController.clear(player.getUUID());
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
				String id = stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
				if (id != null && !id.isEmpty()) {
					player.getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
		}
	}

	private static boolean holdsConnectedNozzle(ItemStack stack) {
		if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
			return false;
		}
		String id = stack.get(ModDataComponents.NOZZLE_ENDPOINT_ID);
		return id != null && !id.isEmpty();
	}
}
