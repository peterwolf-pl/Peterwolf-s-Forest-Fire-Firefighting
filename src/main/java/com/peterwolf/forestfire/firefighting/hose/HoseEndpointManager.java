package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleLocationState;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleValveState;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import com.peterwolf.forestfire.firefighting.water.NozzleWaterSimulation;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.network.ModNetworking;
import com.peterwolf.forestfire.network.NozzleHudPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative hose endpoint / nozzle lifecycle manager.
 */
public final class HoseEndpointManager extends SavedData {
	private static final Codec<HoseEndpointManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		HoseEndpoint.CODEC.listOf().optionalFieldOf("endpoints", List.of()).forGetter(m -> new ArrayList<>(m.endpoints.values()))
	).apply(instance, HoseEndpointManager::new));

	public static final SavedDataType<HoseEndpointManager> TYPE = new SavedDataType<>(
		ForestFireMod.id("hose_endpoints"),
		HoseEndpointManager::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private static final Map<ServerLevel, HoseEndpointManager> CACHE = new HashMap<>();

	private final Map<UUID, HoseEndpoint> endpoints = new HashMap<>();

	public HoseEndpointManager() {
	}

	public HoseEndpointManager(List<HoseEndpoint> list) {
		for (HoseEndpoint ep : list) {
			endpoints.put(ep.id, ep);
		}
	}

	public static HoseEndpointManager get(ServerLevel level) {
		return CACHE.computeIfAbsent(level, l -> {
			HoseEndpointManager data = l.getDataStorage().computeIfAbsent(TYPE);
			return data;
		});
	}

	public static void invalidate(ServerLevel level) {
		CACHE.remove(level);
	}

	public static void clearAll() {
		CACHE.clear();
	}

	public Optional<HoseEndpoint> get(UUID id) {
		return Optional.ofNullable(endpoints.get(id));
	}

	/** Register an already-constructed endpoint (used by automatic hose deployment). */
	public void putEndpoint(HoseEndpoint ep) {
		endpoints.put(ep.id, ep);
		setDirty();
	}

	public Optional<HoseEndpoint> byOperator(UUID playerId) {
		for (HoseEndpoint ep : endpoints.values()) {
			if (ep.isHeldBy(playerId)) {
				return Optional.of(ep);
			}
		}
		return Optional.empty();
	}

	public Optional<HoseEndpoint> byGroundPos(BlockPos pos) {
		long key = pos.asLong();
		for (HoseEndpoint ep : endpoints.values()) {
			if (ep.location == NozzleLocationState.GROUND && ep.endpointPos.asLong() == key) {
				return Optional.of(ep);
			}
		}
		return Optional.empty();
	}

	/**
	 * Create a new endpoint attached to a hose anchor and place ground nozzle.
	 */
	public HoseEndpoint createGround(ServerLevel level, BlockPos groundPos, BlockPos pumpPos, float yaw, float pitch, NozzleMode mode) {
		HoseEndpoint ep = new HoseEndpoint(UUID.randomUUID(), pumpPos, groundPos);
		ep.bindPump(pumpPos);
		ep.location = NozzleLocationState.GROUND;
		ep.mode = mode == null ? NozzleMode.STRAIGHT_STREAM : mode;
		ep.valve = NozzleValveState.CLOSED;
		ep.yaw = yaw;
		ep.pitch = pitch;
		refreshMetrics(level, ep);
		endpoints.put(ep.id, ep);
		setDirty();
		return ep;
	}

	public boolean pickup(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
		if (ep.location == NozzleLocationState.HELD) {
			if (ep.operatorId != null && !ep.operatorId.equals(player.getUUID())) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_in_use"));
				return false;
			}
		}
		// Remove ground block if present
		if (ep.location == NozzleLocationState.GROUND) {
			BlockState state = level.getBlockState(ep.endpointPos);
			if (state.is(ModBlocks.GROUND_NOZZLE)) {
				level.removeBlock(ep.endpointPos, false);
			}
		}
		ep.closeValve();
		ep.location = NozzleLocationState.HELD;
		ep.operatorId = player.getUUID();
		ep.endpointPos = player.blockPosition();
		// Keep automatic hose network; only move dynamic endpoint
		HoseConnectionManager.get(level).onNozzleMoved(
			level, ep.id, HosePathGenerator.playerHandEndpoint(player), HosePointType.PLAYER_ENDPOINT, player.blockPosition()
		);
		refreshMetrics(level, ep);

		// Put single connected nozzle in main hand (no duplicates)
		ItemStack stack = connectedNozzleStack(ep);
		ItemStack main = player.getMainHandItem();
		if (main.isEmpty()) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		} else if (main.is(ModItems.FIRE_HOSE_NOZZLE)) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		} else {
			// Swap: move held item to inventory or drop
			if (!player.getInventory().add(main.copy())) {
				player.drop(main.copy(), false);
			}
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		}
		// Strip any other stacks with same endpoint id
		stripDuplicateEndpoints(player, ep.id, true);
		setDirty();
		player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_picked_up"));
		return true;
	}

	public boolean placeFromHand(ServerLevel level, ServerPlayer player, HoseEndpoint ep, BlockPos placePos, float yaw, float pitch) {
		if (!ep.isHeldBy(player.getUUID())) {
			return false;
		}
		ep.closeValve();
		ep.location = NozzleLocationState.GROUND;
		ep.operatorId = null;
		ep.endpointPos = placePos.immutable();
		ep.yaw = yaw;
		ep.pitch = pitch;
		// Preserve hose connection; regenerate final section to ground nozzle
		Vec3 tip = new Vec3(placePos.getX() + 0.5, placePos.getY() + 0.25, placePos.getZ() + 0.5);
		HoseConnectionManager.get(level).onNozzleMoved(level, ep.id, tip, HosePointType.NOZZLE_ENDPOINT, placePos);
		refreshMetrics(level, ep);

		// Place block
		level.setBlock(placePos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
		BlockEntity be = level.getBlockEntity(placePos);
		if (be instanceof com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity ground) {
			ground.setEndpointId(ep.id);
			ground.setYaw(yaw);
		}

		// Remove held nozzle item(s)
		stripDuplicateEndpoints(player, ep.id, false);
		setDirty();
		player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_placed"));
		return true;
	}

	/**
	 * Connect a free nozzle item to the nearest hose and place it on the ground.
	 */
	@Nullable
	public HoseEndpoint connectAndPlace(
		ServerLevel level,
		ServerPlayer player,
		BlockPos placePos,
		BlockPos hoseAnchor,
		float yaw,
		float pitch,
		NozzleMode mode
	) {
		HoseEndpoint ep = createGround(level, placePos, hoseAnchor, yaw, pitch, mode);
		level.setBlock(placePos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
		BlockEntity be = level.getBlockEntity(placePos);
		if (be instanceof com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity ground) {
			ground.setEndpointId(ep.id);
			ground.setYaw(yaw);
		}
		// Consume one nozzle from hand
		ItemStack hand = player.getMainHandItem();
		if (hand.is(ModItems.FIRE_HOSE_NOZZLE)) {
			hand.shrink(1);
		}
		return ep;
	}

	public boolean disconnect(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
		if (ep.valve == NozzleValveState.OPEN) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_close_first"));
			return false;
		}
		// Retract automatic attack hose and return material
		HoseConnectionManager.get(level).disconnectByNozzleEndpoint(level, player, ep.id);
		ep.connected = false;
		ep.location = NozzleLocationState.DISCONNECTED;
		ep.closeValve();
		UUID id = ep.id;
		// Convert held item to free nozzle
		if (ep.operatorId != null && ep.operatorId.equals(player.getUUID())) {
			stripDuplicateEndpoints(player, id, false);
			ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
			if (!player.getInventory().add(free)) {
				player.drop(free, false);
			}
		} else if (ep.location == NozzleLocationState.GROUND
			|| level.getBlockState(ep.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
			level.removeBlock(ep.endpointPos, false);
			ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
			ItemEntity entity = new ItemEntity(level, ep.endpointPos.getX() + 0.5, ep.endpointPos.getY() + 0.5, ep.endpointPos.getZ() + 0.5, free);
			level.addFreshEntity(entity);
		}
		endpoints.remove(id);
		setDirty();
		player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_disconnected"));
		return true;
	}

	public void releaseOperator(ServerLevel level, UUID playerId, boolean placeOnGround) {
		byOperator(playerId).ifPresent(ep -> {
			ep.closeValve();
			if (placeOnGround) {
				BlockPos pos = ep.endpointPos;
				// Find safe place
				if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) {
					pos = pos.above();
				}
				if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
					ep.location = NozzleLocationState.GROUND;
					ep.operatorId = null;
					ep.endpointPos = pos.immutable();
					level.setBlock(pos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
					BlockEntity be = level.getBlockEntity(pos);
					if (be instanceof com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity ground) {
						ground.setEndpointId(ep.id);
						ground.setYaw(ep.yaw);
					}
				} else {
					// Drop free nozzle as last resort (still not deleting)
					ep.location = NozzleLocationState.DISCONNECTED;
					ep.operatorId = null;
					ep.connected = false;
					ItemStack free = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
					level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, free));
					endpoints.remove(ep.id);
				}
			} else {
				ep.operatorId = null;
				ep.location = NozzleLocationState.GROUND;
			}
			setDirty();
		});
	}

	public void tick(ServerLevel level) {
		if (endpoints.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, HoseEndpoint>> it = endpoints.entrySet().iterator();
		while (it.hasNext()) {
			HoseEndpoint ep = it.next().getValue();
			if (!ep.connected) {
				continue;
			}
			// Validate assigned pump + automatic attack hose
			HoseConnection autoLink = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
			if (autoLink != null && autoLink.connected) {
				ep.bindPump(autoLink.sourcePos);
			} else if (ep.pumpPos != null && level.isLoaded(ep.pumpPos) && isPump(level, ep.pumpPos)) {
				ep.anchorPos = ep.pumpPos;
			} else if (ep.pumpPos != null && level.isLoaded(ep.pumpPos) && !isPump(level, ep.pumpPos)) {
				ep.connected = false;
				ep.closeValve();
				ForestFireMod.LOGGER.debug("Nozzle {} lost assigned pump {}", ep.id, ep.pumpPos);
			} else if (!level.isLoaded(ep.anchorPos) || !isHoseAnchor(level, ep.anchorPos)) {
				BlockPos found = findNearbyPump(level, ep.endpointPos, 8);
				if (found != null) {
					ep.bindPump(found);
				} else {
					ep.connected = false;
					ep.closeValve();
					ForestFireMod.LOGGER.debug("Nozzle {} lost hose/pump assignment", ep.id);
				}
			}

			if (ep.location == NozzleLocationState.HELD && ep.operatorId != null) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(ep.operatorId);
				if (player == null || player.level() != level) {
					// Offline / wrong dim — place on ground at last position
					releaseOperator(level, ep.operatorId, true);
					continue;
				}
				// Ensure player still holds the nozzle item (grace: do not drop connection on 1-tick glitches)
				if (!playerHoldsEndpoint(player, ep.id)) {
					ep.missingHoldTicks++;
					if (ep.missingHoldTicks >= 40) {
						releaseOperator(level, player.getUUID(), true);
					}
					continue;
				}
				ep.missingHoldTicks = 0;
				ep.endpointPos = player.blockPosition();
				ep.yaw = player.getYRot();
				ep.pitch = player.getXRot();
				refreshMetrics(level, ep);
				applyHoseConstraint(level, player, ep);
				if (ep.valve == NozzleValveState.OPEN && ep.mode.allowsFlow()) {
					tickSpray(level, player, ep);
				}
				// Do not force-close valve here when CLOSED — player releaseUsing owns that
				if (level.getGameTime() % 5L == 0L) {
					syncHud(player, ep);
				}
			} else if (ep.location == NozzleLocationState.GROUND) {
				ep.closeValve(); // ground nozzles never auto-spray
				ep.missingHoldTicks = 0;
				refreshMetrics(level, ep);
			}
		}
		if (level.getGameTime() % 40L == 0L) {
			setDirty();
		}
	}

	private void tickSpray(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
		// Strict: only the pump this nozzle is assigned to
		PortablePumpBlockEntity pump = HoseNetwork.findPumpForEndpoint(level, ep.id);
		if (pump == null && ep.pumpPos != null) {
			BlockEntity be = level.getBlockEntity(ep.pumpPos);
			if (be instanceof PortablePumpBlockEntity p) {
				pump = p;
			}
		}
		if (pump == null && ep.anchorPos != null && isPump(level, ep.anchorPos)) {
			BlockEntity be = level.getBlockEntity(ep.anchorPos);
			if (be instanceof PortablePumpBlockEntity p) {
				pump = p;
			}
		}
		if (pump == null) {
			ep.pressure01 = 0.0F;
			if (level.getGameTime() % 20L == 0L) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_no_pressure"));
			}
			return;
		}
		if (!pump.canSupplyNozzle()) {
			ep.pressure01 = Math.max(0.0F, pump.getPressure());
			if (level.getGameTime() % 20L == 0L) {
				player.sendOverlayMessage(Component.literal(
					"Pump: " + pump.getPumpState().name() + " — check fuel / water intake"
				));
			}
			// Keep valve open so flow resumes automatically when pump recovers
			return;
		}
		float demand = 0.12F + ep.mode.pressureDemand * 0.18F + ep.mode.flowDemand * 0.08F;
		// Longer hose reduces pressure gently
		float lengthPenalty = 1.0F - Math.min(0.4F, ep.hoseLengthFromPump * ForestFireConfig.get().pressureLossPerSegment * 0.8F);
		float supplied = pump.consumeForNozzle(demand) * lengthPenalty;
		ep.pressure01 = Mth.clamp(Math.max(pump.getPressure(), supplied / Math.max(0.05F, demand)), 0.0F, 1.0F);
		if (ep.pressure01 < 0.06F) {
			if (level.getGameTime() % 20L == 0L) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_low_pressure"));
			}
			return;
		}
		// Mild hose reaction only — no attack knockback feel
		if (ep.mode == NozzleMode.STRAIGHT_STREAM && ep.pressure01 > 0.5F) {
			Vec3 look = player.getLookAngle();
			player.setDeltaMovement(player.getDeltaMovement().add(look.scale(-0.004 * ep.pressure01)));
			player.hurtMarked = true;
		}
		NozzleWaterSimulation.spray(level, player, ep.mode, ep.pressure01);
	}

	private void applyHoseConstraint(ServerLevel level, ServerPlayer player, HoseEndpoint ep) {
		// Prefer automatic hose connection tension (deployed vs path length)
		HoseConnection auto = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
		double dx;
		double dz;
		double dist;
		int max;
		if (auto != null) {
			Vec3 hand = HosePathGenerator.playerHandEndpoint(player);
			auto.updateDynamicEndpoint(hand, HosePointType.PLAYER_ENDPOINT);
			auto.refreshTension();
			ep.tension = auto.tension;
			ep.remainingDistance = (int) Math.max(0, Math.floor(auto.remainingSlack));
			ep.maxReach = auto.deployedLength;
			ep.hoseLengthFromPump = (int) Math.ceil(auto.currentPathLength);
			dx = player.getX() - (auto.sourcePos.getX() + 0.5);
			dz = player.getZ() - (auto.sourcePos.getZ() + 0.5);
			dist = Math.sqrt(dx * dx + dz * dz);
			// Also clamp by path length vs deployed
			max = Math.max(1, auto.deployedLength);
			if (auto.currentPathLength > auto.deployedLength) {
				ep.tension = HoseTension.MAXIMUM;
				ep.remainingDistance = 0;
			}
		} else {
			dx = player.getX() - (ep.anchorPos.getX() + 0.5);
			dz = player.getZ() - (ep.anchorPos.getZ() + 0.5);
			dist = Math.sqrt(dx * dx + dz * dz);
			max = Math.max(1, ep.maxReach);
			ep.remainingDistance = (int) Math.max(0, Math.floor(max - dist));
			ep.tension = HoseTension.fromRemaining(ep.remainingDistance, max);
		}

		if (ep.tension == HoseTension.TIGHT) {
			// Resistance when moving further away
			Vec3 motion = player.getDeltaMovement();
			Vec3 away = new Vec3(dx, 0, dz).normalize();
			double outward = motion.x * away.x + motion.z * away.z;
			if (outward > 0) {
				player.setDeltaMovement(motion.x - away.x * outward * 0.55, motion.y, motion.z - away.z * outward * 0.55);
				player.hurtMarked = true;
			}
			if (level.getGameTime() % 25L == 0L) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_tight"));
			}
		} else if (ep.tension == HoseTension.MAXIMUM) {
			// Soft clamp: kill outward velocity and gently pull back — no hard teleport
			Vec3 motion = player.getDeltaMovement();
			Vec3 away = dist < 1.0E-4 ? Vec3.ZERO : new Vec3(dx, 0, dz).normalize();
			double outward = motion.x * away.x + motion.z * away.z;
			if (outward > 0) {
				player.setDeltaMovement(motion.x - away.x * outward, motion.y, motion.z - away.z * outward);
			}
			if (dist > max) {
				double excess = dist - max;
				double pull = Math.min(0.28, excess * 0.12);
				player.setDeltaMovement(player.getDeltaMovement().add(away.scale(-pull)));
			}
			player.hurtMarked = true;
			if (level.getGameTime() % 15L == 0L) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_maximum"));
			}
		}
	}

	public int countOpenNozzlesNear(BlockPos pumpPos) {
		return countOpenNozzlesOnPump(null, pumpPos);
	}

	/** Count open valves strictly assigned to this pump. */
	public int countOpenNozzlesOnPump(@Nullable ServerLevel level, BlockPos pumpPos) {
		int n = 0;
		for (HoseEndpoint ep : endpoints.values()) {
			if (!ep.connected || ep.valve != NozzleValveState.OPEN || !ep.mode.allowsFlow()) {
				continue;
			}
			boolean assigned = ep.pumpPos != null && ep.pumpPos.equals(pumpPos);
			if (!assigned && level != null) {
				HoseConnection c = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
				assigned = c != null && c.connected && c.sourcePos.equals(pumpPos);
			}
			if (!assigned && ep.anchorPos.equals(pumpPos)) {
				assigned = true;
			}
			if (assigned) {
				n++;
			}
		}
		return Math.max(0, n);
	}

	public void refreshMetrics(ServerLevel level, HoseEndpoint ep) {
		HoseConnection auto = HoseConnectionManager.get(level).findAttackByEndpoint(ep.id);
		if (auto != null && auto.connected) {
			ep.bindPump(auto.sourcePos);
			ep.hoseLengthFromPump = (int) Math.ceil(auto.currentPathLength);
			ep.maxReach = Math.max(4, auto.deployedLength);
			ep.tension = auto.tension;
			ep.remainingDistance = (int) Math.max(0, Math.floor(auto.remainingSlack));
			BlockEntity be = level.getBlockEntity(auto.sourcePos);
			if (be instanceof PortablePumpBlockEntity pump) {
				ep.pressure01 = Mth.clamp(pump.getPressure(), 0.0F, 1.0F);
			} else {
				ep.pressure01 = 0.0F;
			}
			return;
		}
		ep.hoseLengthFromPump = 0;
		ep.maxReach = Math.max(4, ForestFireConfig.get().nozzleFreeHoseBlocks);
		ep.pressure01 = 0.0F;
		if (ep.pumpPos != null) {
			BlockEntity be = level.getBlockEntity(ep.pumpPos);
			if (be instanceof PortablePumpBlockEntity pump) {
				ep.pressure01 = Mth.clamp(pump.getPressure() * 0.1F, 0.0F, 0.15F);
			}
		}
	}

	private void syncHud(ServerPlayer player, HoseEndpoint ep) {
		ModNetworking.sendNozzleHud(player, new NozzleHudPayload(
			ep.mode.label,
			ep.pressure01,
			ep.valve == NozzleValveState.OPEN,
			ep.tension.name(),
			ep.remainingDistance,
			ep.connected
		));
	}

	public static ItemStack connectedNozzleStack(HoseEndpoint ep) {
		ItemStack stack = new ItemStack(ModItems.FIRE_HOSE_NOZZLE);
		stack.set(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID, ep.id.toString());
		stack.set(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_MODE, ep.mode.ordinal());
		return stack;
	}

	public static boolean playerHoldsEndpoint(ServerPlayer player, UUID endpointId) {
		String id = endpointId.toString();
		ItemStack main = player.getMainHandItem();
		if (main.is(ModItems.FIRE_HOSE_NOZZLE)
			&& id.equals(main.getOrDefault(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID, ""))) {
			return true;
		}
		ItemStack off = player.getOffhandItem();
		if (off.is(ModItems.FIRE_HOSE_NOZZLE)
			&& id.equals(off.getOrDefault(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID, ""))) {
			return true;
		}
		// Also accept hotbar (player may switch slots briefly)
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.FIRE_HOSE_NOZZLE)
				&& id.equals(stack.getOrDefault(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID, ""))) {
				return true;
			}
		}
		return false;
	}

	private static void stripDuplicateEndpoints(ServerPlayer player, UUID endpointId, boolean keepOneInMain) {
		String id = endpointId.toString();
		boolean kept = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.is(ModItems.FIRE_HOSE_NOZZLE)) {
				continue;
			}
			String stackId = stack.get(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID);
			if (id.equals(stackId)) {
				if (keepOneInMain && !kept && i == player.getInventory().getSelectedSlot()) {
					kept = true;
					continue;
				}
				player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		// offhand
		ItemStack off = player.getOffhandItem();
		if (off.is(ModItems.FIRE_HOSE_NOZZLE) && id.equals(off.get(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID))) {
			if (!(keepOneInMain && player.getMainHandItem().isEmpty())) {
				player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
			}
		}
	}

	public static boolean isHoseAnchor(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(ModBlocks.HOSE_SPLITTER)
			|| state.is(ModBlocks.PORTABLE_PUMP) || state.is(ModBlocks.PORTABLE_SPRINKLER);
	}

	/** True if pos is a portable pump (preferred auto-hose attach point). */
	public static boolean isPump(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(ModBlocks.PORTABLE_PUMP);
	}

	@Nullable
	public static BlockPos findNearbyPump(ServerLevel level, BlockPos origin, int radius) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		int bestDist = Integer.MAX_VALUE;
		int r = Math.min(radius, 48);
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -4; dy <= 4; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (!level.isLoaded(cursor)) {
						continue;
					}
					if (isPump(level, cursor)) {
						int d = dx * dx + dy * dy + dz * dz;
						if (d < bestDist) {
							bestDist = d;
							best = cursor.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	@Nullable
	public static BlockPos findNearbyHose(ServerLevel level, BlockPos origin, int radius) {
		BlockPos pump = findNearbyPump(level, origin, radius);
		if (pump != null) {
			return pump;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (!level.isLoaded(cursor)) {
						continue;
					}
					if (isHoseAnchor(level, cursor)) {
						int d = dx * dx + dy * dy + dz * dz;
						if (d < bestDist) {
							bestDist = d;
							best = cursor.immutable();
						}
					}
				}
			}
		}
		return best;
	}
}
