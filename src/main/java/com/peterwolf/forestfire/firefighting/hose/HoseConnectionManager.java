package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.intake.IntakeEfficiencyCalculator;
import com.peterwolf.forestfire.firefighting.intake.IntakeSourceResolver;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleLocationState;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleValveState;
import com.peterwolf.forestfire.network.HoseSyncPayload;
import com.peterwolf.forestfire.network.ModNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative automatic hose connections (intake + attack + supply).
 * Persists across restarts; client receives compact path snapshots for rendering.
 */
public final class HoseConnectionManager extends SavedData {
	private static final Codec<HoseConnectionManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		HoseConnection.CODEC.listOf().optionalFieldOf("connections", List.of())
			.forGetter(m -> new ArrayList<>(m.connections.values()))
	).apply(instance, HoseConnectionManager::new));

	public static final SavedDataType<HoseConnectionManager> TYPE = new SavedDataType<>(
		ForestFireMod.id("hose_connections"),
		HoseConnectionManager::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private static final Map<ServerLevel, HoseConnectionManager> CACHE = new HashMap<>();

	private final Map<UUID, HoseConnection> connections = new HashMap<>();
	/** Pending client sync dirty set. */
	private boolean broadcastDirty;

	public HoseConnectionManager() {
	}

	public HoseConnectionManager(List<HoseConnection> list) {
		for (HoseConnection c : list) {
			// After restart: safely close any attack lines' valves via endpoints later
			c.pressurized = false;
			connections.put(c.connectionId, c);
		}
	}

	public static HoseConnectionManager get(ServerLevel level) {
		return CACHE.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(TYPE));
	}

	public static void invalidate(ServerLevel level) {
		CACHE.remove(level);
	}

	public static void clearAll() {
		CACHE.clear();
	}

	public Optional<HoseConnection> get(UUID id) {
		return Optional.ofNullable(connections.get(id));
	}

	public List<HoseConnection> all() {
		return new ArrayList<>(connections.values());
	}

	public List<HoseConnection> byPump(BlockPos pumpPos) {
		List<HoseConnection> list = new ArrayList<>();
		long key = pumpPos.asLong();
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.sourcePos.asLong() == key) {
				list.add(c);
			}
		}
		return list;
	}

	@Nullable
	public HoseConnection findIntake(BlockPos pumpPos) {
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.type == HoseConnectionType.INTAKE && c.sourcePos.equals(pumpPos)) {
				return c;
			}
		}
		return null;
	}

	@Nullable
	public HoseConnection findAttackByEndpoint(UUID endpointId) {
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.type == HoseConnectionType.ATTACK && endpointId.equals(c.nozzleEndpointId)) {
				return c;
			}
		}
		return null;
	}

	@Nullable
	public HoseConnection findAttackOnPump(BlockPos pumpPos) {
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.type == HoseConnectionType.ATTACK && c.sourcePos.equals(pumpPos)) {
				return c;
			}
		}
		return null;
	}

	// --- Creation ---

	public boolean createIntake(ServerLevel level, ServerPlayer player, BlockPos pumpPos, BlockPos waterPos) {
		if (findIntake(pumpPos) != null) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.intake_port_occupied"));
			return false;
		}
		IntakeSourceResolver.SourceInfo source = IntakeSourceResolver.resolve(level, waterPos);
		if (!source.valid()) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.intake_invalid"));
			return false;
		}

		Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, true);
		Vec3 end = HosePathGenerator.surfacePoint(level, waterPos);
		// Water endpoint slightly into the fluid
		end = new Vec3(waterPos.getX() + 0.5, waterPos.getY() + 0.35, waterPos.getZ() + 0.5);

		HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.WATER_ENDPOINT);
		int available = HoseInventoryService.countAvailable(player, true);
		int maxLen = Math.min(ForestFireConfig.get().maxHoseLength, ForestFireConfig.get().maximumIntakeHoseLength);

		HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, maxLen, true, waterPos);
		if (!validation.ok()) {
			player.sendOverlayMessage(Component.literal(validation.message()));
			return false;
		}

		int required = validation.requiredLength();
		if (!HoseInventoryService.consume(player, required, true)) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
			return false;
		}

		HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.INTAKE, pumpPos, waterPos);
		conn.setPath(path);
		conn.deployedLength = required;
		conn.networkId = uuidFromPos(pumpPos);
		double lift = IntakeEfficiencyCalculator.verticalLift(start.y, end.y);
		boolean strainer = level.getBlockState(waterPos).is(ModBlocks.INTAKE_STRAINER)
			|| hasNearbyStrainer(level, waterPos);
		conn.intakeEfficiency = IntakeEfficiencyCalculator.calculate(path, lift, strainer);
		connections.put(conn.connectionId, conn);
		setDirty();
		broadcastDirty = true;

		player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.intake_connected"));
		player.sendSystemMessage(Component.literal("Source: " + source.label()));
		player.sendSystemMessage(Component.literal("Length: " + required + " blocks"));
		player.sendSystemMessage(Component.literal("Vertical lift: " + (int) Math.ceil(lift) + " blocks"));
		player.sendSystemMessage(Component.literal(String.format(
			"Estimated intake efficiency: %.0f%%", conn.intakeEfficiency * 100.0F)));
		int remaining = HoseInventoryService.countAvailable(player, true);
		if (remaining < Integer.MAX_VALUE / 8) {
			player.sendSystemMessage(Component.literal("Remaining hose: " + remaining + " blocks"));
		}
		syncNearby(level, conn);
		return true;
	}

	/**
	 * Connect free nozzle item to pump: creates ATTACK hose + held endpoint.
	 */
	public boolean createAttackHeld(ServerLevel level, ServerPlayer player, BlockPos pumpPos, NozzleMode mode) {
		if (findAttackOnPump(pumpPos) != null && ForestFireConfig.get().singleAttackLinePerPump) {
			// Allow multiple if config false; default single direct output for Phase 1 clarity
			// Check if that line already has open port — for multi we allow later via splitter
		}

		Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
		Vec3 end = HosePathGenerator.playerHandEndpoint(player);
		HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.PLAYER_ENDPOINT);

		int available = HoseInventoryService.countAvailable(player, false);
		int maxLen = ForestFireConfig.get().maxHoseLength;
		HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, maxLen, false, null);
		if (!validation.ok()) {
			player.sendOverlayMessage(Component.literal(validation.message()));
			return false;
		}
		int required = validation.requiredLength();
		// Deploy with some slack for movement
		int deploy = Math.min(maxLen, Math.max(required, required + ForestFireConfig.get().nozzleFreeHoseBlocks));
		// Only consume path requirement; free slack is virtual allowance within max
		int consumeAmt = required;
		if (!HoseInventoryService.consume(player, consumeAmt, false)) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
			return false;
		}

		// Create nozzle endpoint with pump as anchor
		HoseEndpointManager endpoints = HoseEndpointManager.get(level);
		HoseEndpoint ep = new HoseEndpoint(UUID.randomUUID(), pumpPos, player.blockPosition());
		ep.bindPump(pumpPos);
		ep.location = NozzleLocationState.HELD;
		ep.operatorId = player.getUUID();
		ep.mode = mode == null ? NozzleMode.STRAIGHT_STREAM : mode;
		ep.valve = NozzleValveState.CLOSED;
		ep.hoseLengthFromPump = required;
		ep.maxReach = Math.max(4, deploy);
		ep.yaw = player.getYRot();
		ep.pitch = player.getXRot();
		ep.connected = true;
		endpoints.putEndpoint(ep);

		HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.ATTACK, pumpPos, player.blockPosition());
		conn.setPath(path);
		conn.deployedLength = deploy;
		conn.nozzleEndpointId = ep.id;
		conn.targetEntityId = player.getUUID();
		conn.networkId = uuidFromPos(pumpPos);
		connections.put(conn.connectionId, conn);
		setDirty();
		broadcastDirty = true;

		// Put connected nozzle in hand
		net.minecraft.world.item.ItemStack stack = HoseEndpointManager.connectedNozzleStack(ep);
		net.minecraft.world.item.ItemStack main = player.getMainHandItem();
		if (main.is(com.peterwolf.forestfire.item.ModItems.FIRE_HOSE_NOZZLE)) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		} else {
			if (!main.isEmpty() && !player.getInventory().add(main.copy())) {
				player.drop(main.copy(), false);
			}
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		}

		player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.attack_hose_connected"));
		player.sendSystemMessage(Component.literal("Pump: " + pumpPos.toShortString()));
		player.sendSystemMessage(Component.literal("Nozzle: " + ep.mode.label));
		player.sendSystemMessage(Component.literal("Deployed length: " + deploy + " blocks"));
		int rem = HoseInventoryService.countAvailable(player, false);
		if (rem < Integer.MAX_VALUE / 8) {
			player.sendSystemMessage(Component.literal("Remaining hose: " + rem + " blocks"));
		}
		player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_connected"));
		syncNearby(level, conn);
		return true;
	}

	/**
	 * Connect free nozzle and place ground nozzle with auto attack hose.
	 */
	public boolean createAttackGround(
		ServerLevel level,
		ServerPlayer player,
		BlockPos pumpPos,
		BlockPos placePos,
		float yaw,
		float pitch,
		NozzleMode mode
	) {
		Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
		Vec3 end = new Vec3(placePos.getX() + 0.5, placePos.getY() + 0.25, placePos.getZ() + 0.5);
		HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.NOZZLE_ENDPOINT);

		int available = HoseInventoryService.countAvailable(player, false);
		int maxLen = ForestFireConfig.get().maxHoseLength;
		HosePathValidator.Result validation = HosePathValidator.validate(level, path, available, maxLen, false, null);
		if (!validation.ok()) {
			player.sendOverlayMessage(Component.literal(validation.message()));
			return false;
		}
		int required = validation.requiredLength();
		int deploy = Math.min(maxLen, Math.max(required, required + 2));
		if (!HoseInventoryService.consume(player, required, false)) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
			return false;
		}

		HoseEndpointManager endpoints = HoseEndpointManager.get(level);
		HoseEndpoint ep = endpoints.createGround(level, placePos, pumpPos, yaw, pitch, mode);
		ep.bindPump(pumpPos);
		ep.hoseLengthFromPump = required;
		ep.maxReach = Math.max(4, deploy);
		ep.valve = NozzleValveState.CLOSED;

		level.setBlock(placePos, ModBlocks.GROUND_NOZZLE.defaultBlockState(), 3);
		var be = level.getBlockEntity(placePos);
		if (be instanceof com.peterwolf.forestfire.firefighting.nozzle.GroundNozzleBlockEntity ground) {
			ground.setEndpointId(ep.id);
			ground.setYaw(yaw);
		}

		// Consume free nozzle item
		var hand = player.getMainHandItem();
		if (hand.is(com.peterwolf.forestfire.item.ModItems.FIRE_HOSE_NOZZLE)
			&& com.peterwolf.forestfire.firefighting.nozzle.FireHoseNozzleItem.getEndpointId(hand) == null) {
			hand.shrink(1);
		}

		HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.ATTACK, pumpPos, placePos);
		conn.setPath(path);
		conn.deployedLength = deploy;
		conn.nozzleEndpointId = ep.id;
		conn.networkId = uuidFromPos(pumpPos);
		connections.put(conn.connectionId, conn);
		setDirty();
		broadcastDirty = true;

		player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.attack_hose_connected"));
		player.sendSystemMessage(Component.literal("Deployed length: " + deploy + " blocks"));
		syncNearby(level, conn);
		return true;
	}

	public boolean createAttackToEndpoint(
		ServerLevel level,
		ServerPlayer player,
		BlockPos pumpPos,
		UUID endpointId,
		BlockPos nozzlePos
	) {
		HoseEndpointManager endpoints = HoseEndpointManager.get(level);
		HoseEndpoint ep = endpoints.get(endpointId).orElse(null);
		if (ep == null) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_orphaned"));
			return false;
		}
		if (findAttackByEndpoint(endpointId) != null) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_already_connected"));
			return false;
		}

		Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
		Vec3 end = new Vec3(nozzlePos.getX() + 0.5, nozzlePos.getY() + 0.25, nozzlePos.getZ() + 0.5);
		HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.NOZZLE_ENDPOINT);
		int available = HoseInventoryService.countAvailable(player, false);
		HosePathValidator.Result validation = HosePathValidator.validate(
			level, path, available, ForestFireConfig.get().maxHoseLength, false, null);
		if (!validation.ok()) {
			player.sendOverlayMessage(Component.literal(validation.message()));
			return false;
		}
		int required = validation.requiredLength();
		if (!HoseInventoryService.consume(player, required, false)) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
			return false;
		}

		ep.bindPump(pumpPos);
		ep.connected = true;
		ep.hoseLengthFromPump = required;
		ep.maxReach = Math.max(4, required + ForestFireConfig.get().nozzleFreeHoseBlocks);
		endpoints.setDirty();

		HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.ATTACK, pumpPos, nozzlePos);
		conn.setPath(path);
		conn.deployedLength = required + ForestFireConfig.get().nozzleFreeHoseBlocks;
		conn.nozzleEndpointId = ep.id;
		conn.networkId = uuidFromPos(pumpPos);
		connections.put(conn.connectionId, conn);
		setDirty();
		broadcastDirty = true;
		player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.attack_hose_connected"));
		syncNearby(level, conn);
		return true;
	}

	public boolean createSupplyToSplitter(ServerLevel level, ServerPlayer player, BlockPos pumpPos, BlockPos splitterPos) {
		// One supply line pump → splitter
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.type == HoseConnectionType.SUPPLY && c.sourcePos.equals(pumpPos) && c.targetPos.equals(splitterPos)) {
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_already_exists"));
				return false;
			}
		}
		Vec3 start = HosePathGenerator.pumpPort(level, pumpPos, false);
		Vec3 end = HosePathGenerator.surfacePoint(level, splitterPos);
		HosePath path = HosePathGenerator.generate(level, start, end, HosePointType.PUMP_PORT, HosePointType.SPLITTER_PORT);
		int available = HoseInventoryService.countAvailable(player, false);
		HosePathValidator.Result validation = HosePathValidator.validate(
			level, path, available, ForestFireConfig.get().maxHoseLength, false, null);
		if (!validation.ok()) {
			player.sendOverlayMessage(Component.literal(validation.message()));
			return false;
		}
		int required = validation.requiredLength();
		if (!HoseInventoryService.consume(player, required, false)) {
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
			return false;
		}
		HoseConnection conn = new HoseConnection(UUID.randomUUID(), HoseConnectionType.SUPPLY, pumpPos, splitterPos);
		conn.setPath(path);
		conn.deployedLength = required;
		conn.networkId = uuidFromPos(pumpPos);
		connections.put(conn.connectionId, conn);
		setDirty();
		broadcastDirty = true;
		player.sendSystemMessage(Component.literal("Supply hose connected to splitter (" + required + " blocks)"));
		syncNearby(level, conn);
		return true;
	}

	// --- Disconnect ---

	public int disconnectAllAtPump(ServerLevel level, ServerPlayer player, BlockPos pumpPos) {
		int n = 0;
		Iterator<Map.Entry<UUID, HoseConnection>> it = connections.entrySet().iterator();
		while (it.hasNext()) {
			HoseConnection c = it.next().getValue();
			if (c.sourcePos.equals(pumpPos) || c.targetPos.equals(pumpPos)) {
				retract(level, player, c);
				cleanupEndpointForConnection(level, player, c, false);
				syncRemove(level, c);
				it.remove();
				n++;
			}
		}
		if (n > 0) {
			setDirty();
			broadcastDirty = true;
			// Full resync
			syncAllNearby(level, pumpPos);
		}
		return n;
	}

	/**
	 * Pump was destroyed/broken — remove every hose remnant tied to this pump:
	 * connections, client render, nozzle endpoints, ground nozzle blocks.
	 * Hose material returns to {@code player} if present, otherwise drops at the pump.
	 */
	public int clearAllOnPumpDestroyed(ServerLevel level, BlockPos pumpPos, @Nullable ServerPlayer player) {
		int n = 0;
		int totalHose = 0;
		List<HoseConnection> doomed = new ArrayList<>();
		for (HoseConnection c : connections.values()) {
			if (c.sourcePos.equals(pumpPos) || c.targetPos.equals(pumpPos)) {
				doomed.add(c);
			}
		}
		// Also catch attack lines whose endpoint is still bound to this pump
		for (HoseConnection c : connections.values()) {
			if (doomed.contains(c)) {
				continue;
			}
			if (c.type == HoseConnectionType.ATTACK && c.nozzleEndpointId != null) {
				HoseEndpoint ep = HoseEndpointManager.get(level).get(c.nozzleEndpointId).orElse(null);
				if (ep != null && ep.pumpPos != null && ep.pumpPos.equals(pumpPos)) {
					doomed.add(c);
				}
			}
		}

		for (HoseConnection c : doomed) {
			int returnAmt = ForestFireConfig.get().returnFullHoseLength
				? c.deployedLength
				: (int) Math.ceil(c.currentPathLength);
			totalHose += Math.max(0, returnAmt);

			// Close valves / free nozzles / remove ground blocks
			cleanupEndpointForConnection(level, player, c, true);

			c.connected = false;
			c.pressurized = false;
			syncRemove(level, c);
			connections.remove(c.connectionId);
			n++;
		}

		// Any leftover endpoints still pointing at this pump
		HoseEndpointManager.get(level).purgePump(level, pumpPos, player);

		if (totalHose > 0) {
			if (player != null) {
				HoseInventoryService.returnHose(player, totalHose, false);
			} else {
				HoseInventoryService.dropHoseAt(level, pumpPos, totalHose);
			}
		}

		if (n > 0) {
			setDirty();
			broadcastDirty = true;
			syncAllInDimension(level);
			ForestFireMod.LOGGER.debug("Cleared {} hose connection(s) after pump removed at {}", n, pumpPos);
		}
		return n;
	}

	/**
	 * @param orphanNozzle if true, drop/convert connected nozzles to free items and remove ground blocks
	 */
	private void cleanupEndpointForConnection(
		ServerLevel level,
		@Nullable ServerPlayer player,
		HoseConnection c,
		boolean orphanNozzle
	) {
		if (c.nozzleEndpointId == null) {
			return;
		}
		HoseEndpointManager mgr = HoseEndpointManager.get(level);
		mgr.get(c.nozzleEndpointId).ifPresent(ep -> {
			ep.closeValve();
			ep.connected = false;
			if (!orphanNozzle) {
				return;
			}
			if (ep.location == NozzleLocationState.GROUND
				|| level.getBlockState(ep.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
				if (level.getBlockState(ep.endpointPos).is(ModBlocks.GROUND_NOZZLE)) {
					level.removeBlock(ep.endpointPos, false);
				}
				// Drop free nozzle at ground position
				net.minecraft.world.item.ItemStack free =
					new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.FIRE_HOSE_NOZZLE);
				level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
					level,
					ep.endpointPos.getX() + 0.5,
					ep.endpointPos.getY() + 0.5,
					ep.endpointPos.getZ() + 0.5,
					free
				));
			} else if (ep.location == NozzleLocationState.HELD && ep.operatorId != null) {
				ServerPlayer op = level.getServer().getPlayerList().getPlayer(ep.operatorId);
				if (op != null) {
					// Strip connected NBT — leave free nozzle item
					for (int i = 0; i < op.getInventory().getContainerSize(); i++) {
						net.minecraft.world.item.ItemStack stack = op.getInventory().getItem(i);
						if (!stack.is(com.peterwolf.forestfire.item.ModItems.FIRE_HOSE_NOZZLE)) {
							continue;
						}
						String id = stack.get(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID);
						if (ep.id.toString().equals(id)) {
							stack.remove(com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents.NOZZLE_ENDPOINT_ID);
						}
					}
				}
			}
			mgr.removeEndpoint(ep.id);
		});
	}

	public boolean disconnectByNozzleEndpoint(ServerLevel level, ServerPlayer player, UUID endpointId) {
		HoseConnection c = findAttackByEndpoint(endpointId);
		if (c == null) {
			return false;
		}
		// Close valve on endpoint
		HoseEndpointManager.get(level).get(endpointId).ifPresent(ep -> {
			ep.closeValve();
			ep.connected = false;
		});
		retract(level, player, c);
		connections.remove(c.connectionId);
		setDirty();
		broadcastDirty = true;
		syncRemove(level, c);
		player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_retracted"));
		return true;
	}

	public boolean disconnectIntakeAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Iterator<Map.Entry<UUID, HoseConnection>> it = connections.entrySet().iterator();
		while (it.hasNext()) {
			HoseConnection c = it.next().getValue();
			if (c.type == HoseConnectionType.INTAKE && (c.targetPos.equals(pos) || c.sourcePos.equals(pos))) {
				retract(level, player, c);
				it.remove();
				setDirty();
				broadcastDirty = true;
				syncRemove(level, c);
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_retracted"));
				return true;
			}
		}
		return false;
	}

	public boolean disconnectConnection(ServerLevel level, ServerPlayer player, UUID connectionId) {
		HoseConnection c = connections.remove(connectionId);
		if (c == null) {
			return false;
		}
		if (c.nozzleEndpointId != null) {
			HoseEndpointManager.get(level).get(c.nozzleEndpointId).ifPresent(ep -> {
				ep.closeValve();
			});
		}
		retract(level, player, c);
		setDirty();
		broadcastDirty = true;
		syncRemove(level, c);
		return true;
	}

	private void retract(ServerLevel level, ServerPlayer player, HoseConnection c) {
		if (c.nozzleEndpointId != null) {
			HoseEndpointManager.get(level).get(c.nozzleEndpointId).ifPresent(ep -> ep.closeValve());
		}
		int returnAmt = ForestFireConfig.get().returnFullHoseLength ? c.deployedLength : (int) Math.ceil(c.currentPathLength);
		HoseInventoryService.returnHose(player, returnAmt, c.type == HoseConnectionType.INTAKE);
	}

	// --- Anchors & route updates ---

	public boolean insertAnchorNear(ServerLevel level, ServerPlayer player, Vec3 anchor, double radius) {
		HoseConnection best = null;
		double bestDist = radius;
		for (HoseConnection c : connections.values()) {
			if (!c.connected || c.path.isEmpty()) {
				continue;
			}
			for (HoseControlPoint p : c.path.points()) {
				double d = p.position().distanceTo(anchor);
				if (d < bestDist) {
					bestDist = d;
					best = c;
				}
			}
		}
		if (best == null) {
			return false;
		}
		// Insert anchor into path near closest segment
		List<HoseControlPoint> pts = new ArrayList<>(best.path.points());
		int insertAt = 1;
		double minSeg = Double.MAX_VALUE;
		for (int i = 0; i < pts.size() - 1; i++) {
			double d = distToSegment(anchor, pts.get(i).position(), pts.get(i + 1).position());
			if (d < minSeg) {
				minSeg = d;
				insertAt = i + 1;
			}
		}
		pts.add(insertAt, new HoseControlPoint(anchor, HosePointType.GROUND_ANCHOR));
		best.setPath(HosePath.of(pts));
		// Extra length cost for detour
		int extra = Math.max(0, best.path.blockLength() - best.deployedLength);
		if (extra > 0) {
			if (!HoseInventoryService.consume(player, extra, best.type == HoseConnectionType.INTAKE)) {
				// revert
				pts.remove(insertAt);
				best.setPath(HosePath.of(pts));
				player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_insufficient"));
				return false;
			}
			best.deployedLength += extra;
		}
		best.routeDirty = false;
		setDirty();
		broadcastDirty = true;
		syncNearby(level, best);
		return true;
	}

	private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0E-8) {
			return p.distanceTo(a);
		}
		double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/**
	 * Called when nozzle is picked up or placed — regenerate final segment only.
	 */
	public void onNozzleMoved(ServerLevel level, UUID endpointId, Vec3 newEndpoint, HosePointType endType, BlockPos blockTarget) {
		HoseConnection c = findAttackByEndpoint(endpointId);
		if (c == null) {
			return;
		}
		c.updateDynamicEndpoint(newEndpoint, endType);
		c.targetPos = blockTarget.immutable();
		// Enforce max path length
		if (c.currentPathLength > c.deployedLength + 0.5) {
			// Path too long — try regenerate full route from pump
			Vec3 start = HosePathGenerator.pumpPort(level, c.sourcePos, false);
			HosePath regen = HosePathGenerator.generate(level, start, newEndpoint, HosePointType.PUMP_PORT, endType);
			if (regen.pathLength() <= c.deployedLength + 1.0) {
				c.setPath(regen);
			}
		}
		c.refreshTension();
		setDirty();
		broadcastDirty = true;
		if (level.getGameTime() % 5L == 0L) {
			syncNearby(level, c);
		}
	}

	// --- Tick ---

	public void tick(ServerLevel level) {
		if (connections.isEmpty()) {
			return;
		}
		boolean anyChange = false;
		for (HoseConnection c : connections.values()) {
			if (!c.connected) {
				continue;
			}
			if (c.type == HoseConnectionType.INTAKE) {
				if (!level.isLoaded(c.targetPos) || !IntakeSourceResolver.stillValid(level, c.targetPos)) {
					c.connected = false;
					c.pressurized = false;
					c.intakeEfficiency = 0.0F;
					anyChange = true;
					// Notify nearby players occasionally
					if (level.getGameTime() % 40L == 0L) {
						notifyNear(level, c.sourcePos, Component.translatable("message.peterwolfs_forestfire.intake_lost"));
					}
				} else if (!c.connected) {
					// restored
				} else {
					// Refresh efficiency occasionally
					if (level.getGameTime() % 80L == 0L) {
						double lift = IntakeEfficiencyCalculator.verticalLift(
							HosePathGenerator.pumpPort(level, c.sourcePos, true).y,
							c.targetPos.getY() + 0.35
						);
						boolean strainer = level.getBlockState(c.targetPos).is(ModBlocks.INTAKE_STRAINER)
							|| hasNearbyStrainer(level, c.targetPos);
						c.intakeEfficiency = IntakeEfficiencyCalculator.calculate(c.path, lift, strainer);
					}
				}
			} else if (c.type == HoseConnectionType.ATTACK && c.nozzleEndpointId != null) {
				HoseEndpoint ep = HoseEndpointManager.get(level).get(c.nozzleEndpointId).orElse(null);
				if (ep == null || !ep.connected) {
					continue;
				}
				// Dynamic final segment for held nozzle
				if (ep.location == NozzleLocationState.HELD && ep.operatorId != null) {
					ServerPlayer operator = level.getServer().getPlayerList().getPlayer(ep.operatorId);
					if (operator != null && operator.level() == level) {
						Vec3 hand = HosePathGenerator.playerHandEndpoint(operator);
						// Movement threshold before full update
						Vec3 lastEnd = c.path.end();
						if (lastEnd.distanceToSqr(hand) > 0.25) {
							c.updateDynamicEndpoint(hand, HosePointType.PLAYER_ENDPOINT);
							c.targetPos = operator.blockPosition();
							// Sync tension onto endpoint
							ep.tension = c.tension;
							ep.remainingDistance = (int) Math.max(0, Math.floor(c.remainingSlack));
							ep.maxReach = c.deployedLength;
							ep.hoseLengthFromPump = (int) Math.ceil(c.currentPathLength);
							anyChange = true;
						}
					}
				} else if (ep.location == NozzleLocationState.GROUND) {
					Vec3 tip = new Vec3(ep.endpointPos.getX() + 0.5, ep.endpointPos.getY() + 0.25, ep.endpointPos.getZ() + 0.5);
					if (c.path.end().distanceToSqr(tip) > 0.1) {
						c.updateDynamicEndpoint(tip, HosePointType.NOZZLE_ENDPOINT);
						c.targetPos = ep.endpointPos;
						anyChange = true;
					}
				}
				c.pressurized = ep.valve == NozzleValveState.OPEN;
			}

			// Pump still present?
			if (!level.isLoaded(c.sourcePos) || !level.getBlockState(c.sourcePos).is(ModBlocks.PORTABLE_PUMP)) {
				if (c.type != HoseConnectionType.SPLITTER_BRANCH) {
					// Keep data but mark disconnected if pump missing
					if (level.isLoaded(c.sourcePos)) {
						BlockState st = level.getBlockState(c.sourcePos);
						if (!st.is(ModBlocks.PORTABLE_PUMP) && !st.is(ModBlocks.HOSE_SPLITTER)) {
							c.connected = false;
							anyChange = true;
						}
					}
				}
			}
		}

		if (anyChange) {
			setDirty();
			broadcastDirty = true;
		}

		// Periodic full path sync for render clients
		if (broadcastDirty && level.getGameTime() % 10L == 0L) {
			syncAllInDimension(level);
			broadcastDirty = false;
		} else if (level.getGameTime() % 100L == 0L) {
			syncAllInDimension(level);
		}
	}

	// --- Query helpers for pump ---

	public IntakeScanResult scanIntake(ServerLevel level, BlockPos pumpPos) {
		HoseConnection intake = findIntake(pumpPos);
		if (intake == null || !intake.connected) {
			return IntakeScanResult.none();
		}
		if (!IntakeSourceResolver.stillValid(level, intake.targetPos)) {
			return IntakeScanResult.none();
		}
		IntakeSourceResolver.SourceInfo info = IntakeSourceResolver.resolve(level, intake.targetPos);
		boolean strainer = level.getBlockState(intake.targetPos).is(ModBlocks.INTAKE_STRAINER)
			|| hasNearbyStrainer(level, intake.targetPos);
		return new IntakeScanResult(true, info.availableWater(), strainer, intake.targetPos,
			intake.deployedLength, intake.intakeEfficiency);
	}

	public int countAttackLines(BlockPos pumpPos) {
		int n = 0;
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.sourcePos.equals(pumpPos)
				&& (c.type == HoseConnectionType.ATTACK || c.type == HoseConnectionType.SUPPLY
				|| c.type == HoseConnectionType.SPLITTER_BRANCH)) {
				n++;
			}
		}
		return n;
	}

	public int maxAttackPathLength(BlockPos pumpPos) {
		int max = 0;
		for (HoseConnection c : connections.values()) {
			if (c.connected && c.sourcePos.equals(pumpPos) && c.type == HoseConnectionType.ATTACK) {
				max = Math.max(max, (int) Math.ceil(c.currentPathLength));
			}
		}
		return max;
	}

	// --- Networking ---

	private void syncNearby(ServerLevel level, HoseConnection c) {
		HoseSyncPayload payload = HoseSyncPayload.fromConnection(c, false);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c.sourcePos.getX() + 0.5, c.sourcePos.getY() + 0.5, c.sourcePos.getZ() + 0.5)
				< 96 * 96) {
				ModNetworking.sendHoseSync(player, payload);
			}
		}
	}

	private void syncRemove(ServerLevel level, HoseConnection c) {
		HoseSyncPayload payload = HoseSyncPayload.fromConnection(c, true);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c.sourcePos.getX() + 0.5, c.sourcePos.getY() + 0.5, c.sourcePos.getZ() + 0.5)
				< 96 * 96) {
				ModNetworking.sendHoseSync(player, payload);
			}
		}
	}

	private void syncAllNearby(ServerLevel level, BlockPos around) {
		for (HoseConnection c : connections.values()) {
			if (c.sourcePos.closerThan(around, 64) || c.targetPos.closerThan(around, 64)) {
				syncNearby(level, c);
			}
		}
	}

	public void syncAllInDimension(ServerLevel level) {
		for (HoseConnection c : connections.values()) {
			if (c.connected) {
				syncNearby(level, c);
			}
		}
	}

	public void syncToPlayer(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		for (HoseConnection c : connections.values()) {
			if (c.connected) {
				ModNetworking.sendHoseSync(player, HoseSyncPayload.fromConnection(c, false));
			}
		}
	}

	private static void notifyNear(ServerLevel level, BlockPos pos, Component msg) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 32 * 32) {
				player.sendOverlayMessage(msg);
			}
		}
	}

	private static boolean hasNearbyStrainer(ServerLevel level, BlockPos pos) {
		for (var d : net.minecraft.core.Direction.values()) {
			if (level.getBlockState(pos.relative(d)).is(ModBlocks.INTAKE_STRAINER)) {
				return true;
			}
		}
		return false;
	}

	private static UUID uuidFromPos(BlockPos pos) {
		return new UUID(pos.asLong(), 0x484F5345L); // "HOSE"
	}

	public record IntakeScanResult(
		boolean hasIntake,
		int availableWater,
		boolean hasStrainer,
		@Nullable BlockPos tankOrSourcePos,
		int intakeLength,
		float efficiency
	) {
		public static IntakeScanResult none() {
			return new IntakeScanResult(false, 0, false, null, 0, 0.0F);
		}
	}
}
