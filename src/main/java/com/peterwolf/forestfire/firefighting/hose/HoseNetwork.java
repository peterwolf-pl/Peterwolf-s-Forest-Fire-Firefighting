package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.block.ModBlocks;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlockEntity;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Lightweight BFS hose graph. No per-segment entities.
 */
public final class HoseNetwork {
	private HoseNetwork() {
	}

	public static final class PumpLinks {
		public boolean hasIntake;
		public int availableWater;
		public int outputLength;
		public int activeNozzles;
		public boolean hasStrainer;
		@Nullable
		public BlockPos tankPos;
		@Nullable
		public BlockPos pumpPos;
	}

	public static PumpLinks scan(ServerLevel level, BlockPos pumpPos) {
		PumpLinks links = new PumpLinks();
		links.pumpPos = pumpPos;
		int max = ForestFireConfig.get().maxHoseLength;

		// Intake BFS
		Set<Long> intakeVisited = new HashSet<>();
		ArrayDeque<BlockPos> intakeQueue = new ArrayDeque<>();
		intakeQueue.add(pumpPos);
		intakeVisited.add(pumpPos.asLong());
		int intakeLen = 0;
		while (!intakeQueue.isEmpty() && intakeLen < max) {
			BlockPos current = intakeQueue.removeFirst();
			for (Direction dir : Direction.values()) {
				BlockPos next = current.relative(dir);
				if (!intakeVisited.add(next.asLong()) || !level.isLoaded(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.is(ModBlocks.INTAKE_HOSE)) {
					intakeQueue.add(next);
					intakeLen++;
					continue;
				}
				if (isWaterSource(level, next)) {
					links.hasIntake = true;
					links.availableWater += 10;
				}
				BlockEntity be = level.getBlockEntity(next);
				if (be instanceof PortableWaterTankBlockEntity tank && tank.getWater() > 0) {
					links.hasIntake = true;
					links.availableWater += tank.getWater();
					links.tankPos = next;
				}
				if (state.is(ModBlocks.PORTABLE_PUMP) && !next.equals(pumpPos)) {
					// other pump — ignore
				}
			}
		}
		// Direct water near pump also counts
		int local = PortablePumpBlockEntity.countNearbyWater(level, pumpPos);
		if (local > 0) {
			links.hasIntake = true;
			links.availableWater += local * 5;
		}

		// Strainer: dedicated block in/near water, iron bars, or next to intake path
		for (Direction dir : Direction.values()) {
			BlockPos n = pumpPos.relative(dir);
			BlockState ns = level.getBlockState(n);
			if (ns.is(ModBlocks.INTAKE_STRAINER) || ns.is(Blocks.IRON_BARS)) {
				links.hasStrainer = true;
			}
		}
		// Also accept strainer along intake hose path (within short scan already done)
		if (!links.hasStrainer) {
			for (long key : intakeVisited) {
				BlockPos p = BlockPos.of(key);
				if (level.getBlockState(p).is(ModBlocks.INTAKE_STRAINER)) {
					links.hasStrainer = true;
					break;
				}
				for (Direction dir : Direction.values()) {
					if (level.getBlockState(p.relative(dir)).is(ModBlocks.INTAKE_STRAINER)) {
						links.hasStrainer = true;
						break;
					}
				}
				if (links.hasStrainer) {
					break;
				}
			}
		}

		// Output hose BFS
		Set<Long> outVisited = new HashSet<>();
		ArrayDeque<Node> outQueue = new ArrayDeque<>();
		outQueue.add(new Node(pumpPos, 0));
		outVisited.add(pumpPos.asLong());
		int maxLen = 0;
		int nozzles = 0;
		while (!outQueue.isEmpty()) {
			Node node = outQueue.removeFirst();
			maxLen = Math.max(maxLen, node.length);
			for (Direction dir : Direction.values()) {
				BlockPos next = node.pos.relative(dir);
				if (!outVisited.add(next.asLong()) || !level.isLoaded(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.is(ModBlocks.FIRE_HOSE) || state.is(ModBlocks.HOSE_SPLITTER) || state.is(ModBlocks.PORTABLE_SPRINKLER)) {
					if (node.length + 1 <= max) {
						outQueue.add(new Node(next, node.length + 1));
					}
				}
				// Nozzle connection points: players hold nozzles; endpoints counted if hose ends in air near player later.
				// Count splitters as potential multi-line load.
				if (state.is(ModBlocks.HOSE_SPLITTER)) {
					nozzles += 1;
				}
				if (state.is(ModBlocks.PORTABLE_SPRINKLER)) {
					nozzles += 1;
				}
			}
		}
		links.outputLength = maxLen;
		links.activeNozzles = Math.max(1, nozzles);
		return links;
	}

	/**
	 * Find nearest running pump supplying a hose network that reaches {@code hosePos}.
	 */
	@Nullable
	public static PortablePumpBlockEntity findSupplyingPump(ServerLevel level, BlockPos hosePos) {
		int max = ForestFireConfig.get().maxHoseLength;
		Set<Long> visited = new HashSet<>();
		ArrayDeque<Node> queue = new ArrayDeque<>();
		queue.add(new Node(hosePos, 0));
		visited.add(hosePos.asLong());
		while (!queue.isEmpty()) {
			Node node = queue.removeFirst();
			if (node.length > max) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(node.pos);
			if (be instanceof PortablePumpBlockEntity pump && pump.canSupplyNozzle()) {
				return pump;
			}
			for (Direction dir : Direction.values()) {
				BlockPos next = node.pos.relative(dir);
				if (!visited.add(next.asLong()) || !level.isLoaded(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.is(ModBlocks.FIRE_HOSE) || state.is(ModBlocks.HOSE_SPLITTER)
					|| state.is(ModBlocks.PORTABLE_PUMP) || state.is(ModBlocks.PORTABLE_SPRINKLER)
					|| state.is(ModBlocks.WATER_TANK_SMALL) || state.is(ModBlocks.WATER_TANK_MEDIUM)
					|| state.is(ModBlocks.WATER_TANK_LARGE)) {
					queue.add(new Node(next, node.length + 1));
				}
			}
		}
		return null;
	}

	public static boolean isWaterSource(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(Fluids.WATER)
			|| level.getBlockState(pos).is(Blocks.WATER)
			|| level.getBlockState(pos).is(Blocks.WATER_CAULDRON);
	}

	/**
	 * Graph distance from a hose position to the nearest pump block (not necessarily running).
	 * Returns maxHoseLength if no pump is found.
	 */
	public static int distanceToPump(ServerLevel level, BlockPos hosePos) {
		int max = ForestFireConfig.get().maxHoseLength;
		Set<Long> visited = new HashSet<>();
		ArrayDeque<Node> queue = new ArrayDeque<>();
		queue.add(new Node(hosePos, 0));
		visited.add(hosePos.asLong());
		while (!queue.isEmpty()) {
			Node node = queue.removeFirst();
			if (node.length > max) {
				continue;
			}
			BlockState state = level.getBlockState(node.pos);
			if (state.is(ModBlocks.PORTABLE_PUMP)) {
				return node.length;
			}
			for (Direction dir : Direction.values()) {
				BlockPos next = node.pos.relative(dir);
				if (!visited.add(next.asLong()) || !level.isLoaded(next)) {
					continue;
				}
				BlockState ns = level.getBlockState(next);
				if (ns.is(ModBlocks.FIRE_HOSE) || ns.is(ModBlocks.HOSE_SPLITTER)
					|| ns.is(ModBlocks.PORTABLE_PUMP) || ns.is(ModBlocks.PORTABLE_SPRINKLER)
					|| ns.is(ModBlocks.GROUND_NOZZLE)) {
					queue.add(new Node(next, node.length + 1));
				}
			}
		}
		return max;
	}

	/**
	 * Count open handheld/ground nozzles for pump load (used by pump scan optionally).
	 */
	public static int countConnectedNozzles(ServerLevel level, BlockPos pumpPos) {
		// Endpoints are authoritative; HoseEndpointManager tallies open valves near this pump
		return com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager.get(level)
			.countOpenNozzlesNear(pumpPos);
	}

	private record Node(BlockPos pos, int length) {
	}
}
