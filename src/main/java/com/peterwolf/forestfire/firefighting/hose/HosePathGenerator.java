package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.config.ForestFireConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lightweight bounded terrain-following hose router.
 * Not mob pathfinding — samples ground height, tries limited detours around solids.
 */
public final class HosePathGenerator {
	private static final int MAX_SAMPLES = 96;
	private static final int MAX_DETOUR_RADIUS = 6;

	private HosePathGenerator() {
	}

	public static HosePath generate(
		Level level,
		Vec3 start,
		Vec3 end,
		HosePointType startType,
		HosePointType endType
	) {
		List<HoseControlPoint> points = new ArrayList<>();
		points.add(new HoseControlPoint(start, startType));

		double dx = end.x - start.x;
		double dz = end.z - start.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		int samples = Mth.clamp((int) Math.ceil(horiz), 2, MAX_SAMPLES);
		double step = horiz / samples;

		// Primary straight horizontal route with terrain sampling
		List<Vec3> route = sampleRoute(level, start, end, samples, 0);

		// If blocked, try left/right lateral offsets
		if (isBlocked(level, route)) {
			boolean found = false;
			for (int offset = 1; offset <= MAX_DETOUR_RADIUS && !found; offset++) {
				for (int sign : new int[]{1, -1}) {
					// Perpendicular detour in XZ
					double len = Math.max(1.0E-4, horiz);
					double nx = -dz / len * offset * sign;
					double nz = dx / len * offset * sign;
					Vec3 mid = new Vec3(
						(start.x + end.x) * 0.5 + nx,
						(start.y + end.y) * 0.5,
						(start.z + end.z) * 0.5 + nz
					);
					List<Vec3> viaMid = new ArrayList<>();
					viaMid.addAll(sampleRoute(level, start, mid, Math.max(2, samples / 2), 0));
					// drop first of second segment (duplicate mid-ish)
					List<Vec3> second = sampleRoute(level, mid, end, Math.max(2, samples / 2), 0);
					if (!second.isEmpty()) {
						viaMid.addAll(second.subList(1, second.size()));
					}
					if (!isBlocked(level, viaMid)) {
						route = viaMid;
						found = true;
						break;
					}
				}
			}
			// Last resort: still use primary route (validator may reject)
			if (!found) {
				route = sampleRoute(level, start, end, samples, 0);
			}
		}

		// Decimate to control points (skip near-collinear terrain samples)
		// Keep denser anchors for client Catmull–Rom hose rendering
		List<Vec3> simplified = simplify(route, 0.22);
		for (int i = 1; i < simplified.size() - 1; i++) {
			points.add(new HoseControlPoint(simplified.get(i), HosePointType.TERRAIN_POINT));
		}
		points.add(new HoseControlPoint(end, endType));
		return HosePath.of(points);
	}

	public static HosePath generateBetweenBlocks(
		Level level,
		BlockPos startBlock,
		BlockPos endBlock,
		HosePointType startType,
		HosePointType endType
	) {
		Vec3 start = surfacePoint(level, startBlock);
		Vec3 end = surfacePoint(level, endBlock);
		return generate(level, start, end, startType, endType);
	}

	/**
	 * Pump hose coupling — outside the pump body so path validation does not
	 * treat the pump block itself as a solid wall.
	 */
	public static Vec3 pumpPort(Level level, BlockPos pumpPos, boolean intakeSide) {
		BlockState state = level.getBlockState(pumpPos);
		Direction facing = Direction.NORTH;
		if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
			facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
		}
		// Intake on back; output on front — 0.85 so coupling is outside the solid cube
		Direction portDir = intakeSide ? facing.getOpposite() : facing;
		return new Vec3(
			pumpPos.getX() + 0.5 + portDir.getStepX() * 0.85,
			pumpPos.getY() + 0.35,
			pumpPos.getZ() + 0.5 + portDir.getStepZ() * 0.85
		);
	}

	public static Vec3 surfacePoint(Level level, BlockPos pos) {
		int topY = findGroundY(level, pos.getX(), pos.getY(), pos.getZ());
		return new Vec3(pos.getX() + 0.5, topY + 0.08, pos.getZ() + 0.5);
	}

	public static Vec3 playerHandEndpoint(net.minecraft.world.entity.player.Player player) {
		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 look = player.getLookAngle();
		// Hand roughly at chest/hand height forward
		return new Vec3(
			player.getX() + look.x * 0.45,
			player.getY() + player.getBbHeight() * 0.55,
			player.getZ() + look.z * 0.45
		);
	}

	private static List<Vec3> sampleRoute(Level level, Vec3 start, Vec3 end, int samples, double lateralBias) {
		List<Vec3> route = new ArrayList<>(samples + 1);
		double dx = end.x - start.x;
		double dz = end.z - start.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		double px = len > 1.0E-4 ? -dz / len * lateralBias : 0.0;
		double pz = len > 1.0E-4 ? dx / len * lateralBias : 0.0;

		for (int i = 0; i <= samples; i++) {
			double t = i / (double) samples;
			double x = Mth.lerp(t, start.x, end.x) + px * Math.sin(t * Math.PI);
			double z = Mth.lerp(t, start.z, end.z) + pz * Math.sin(t * Math.PI);
			int ix = Mth.floor(x);
			int iz = Mth.floor(z);
			int baseY = Mth.floor(Mth.lerp(t, start.y, end.y));
			int gy = findGroundY(level, ix, baseY, iz);
			// slight sag between supports
			double sag = 0.0;
			if (i > 0 && i < samples) {
				sag = -0.04 * Math.sin(t * Math.PI);
			}
			route.add(new Vec3(x, gy + 0.08 + sag, z));
		}
		// Force exact endpoints
		if (!route.isEmpty()) {
			route.set(0, start);
			route.set(route.size() - 1, end);
		}
		return route;
	}

	/**
	 * Find walkable ground Y near expected height. Prefers surface within ±6 of expected.
	 */
	public static int findGroundY(Level level, int x, int expectedY, int z) {
		int minY = level.getMinY();
		int maxY = level.getMaxY() - 1;
		int from = Mth.clamp(expectedY + 6, minY, maxY);
		int to = Mth.clamp(expectedY - 8, minY, maxY);
		for (int y = from; y >= to; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			BlockState above = level.getBlockState(pos.above());
			if (isSolidSupport(state) && isHosePassable(above)) {
				return y + 1;
			}
		}
		// Fallback: scan from high
		for (int y = from; y >= minY; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (isSolidSupport(level.getBlockState(pos)) && isHosePassable(level.getBlockState(pos.above()))) {
				return y + 1;
			}
		}
		return expectedY;
	}

	public static boolean isSolidSupport(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		if (state.canBeReplaced()) {
			return false;
		}
		// Fluids are not support for hose resting (hose sinks to bed)
		if (!state.getFluidState().isEmpty() && state.getFluidState().isSource()) {
			return false;
		}
		return state.is(BlockTags.DIRT)
			|| state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(BlockTags.LOGS)
			|| state.is(BlockTags.PLANKS)
			|| state.isSolid();
	}

	public static boolean isHosePassable(BlockState state) {
		if (state.isAir() || state.canBeReplaced()) {
			return true;
		}
		if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
			// route over fences rather than through
			return false;
		}
		if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
			// treat doors as passable for lightweight routing (player can open)
			return true;
		}
		// Leaves / plants
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE)) {
			return true;
		}
		return !state.isSolid();
	}

	/**
	 * True if any interior sample sits inside a solid full block.
	 */
	private static boolean isBlocked(Level level, List<Vec3> route) {
		if (route.size() < 2) {
			return false;
		}
		// skip endpoints
		for (int i = 1; i < route.size() - 1; i++) {
			Vec3 p = route.get(i);
			BlockPos pos = BlockPos.containing(p);
			if (!level.isLoaded(pos)) {
				return true;
			}
			BlockState state = level.getBlockState(pos);
			if (!isHosePassable(state) && isSolidObstacle(state)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isSolidObstacle(BlockState state) {
		if (state.isAir() || state.canBeReplaced()) {
			return false;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.DOORS)
			|| state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.SLABS) || state.is(BlockTags.STAIRS)
			|| state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.REPLACEABLE)) {
			return false;
		}
		// Equipment never blocks hose routing
		if (state.is(com.peterwolf.forestfire.block.ModBlocks.PORTABLE_PUMP)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.HOSE_SPLITTER)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.INTAKE_STRAINER)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.GROUND_NOZZLE)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.PORTABLE_SPRINKLER)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.WATER_TANK_SMALL)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.WATER_TANK_MEDIUM)
			|| state.is(com.peterwolf.forestfire.block.ModBlocks.WATER_TANK_LARGE)) {
			return false;
		}
		return state.isSolid();
	}

	private static List<Vec3> simplify(List<Vec3> route, double tolerance) {
		if (route.size() <= 3) {
			return route;
		}
		List<Vec3> out = new ArrayList<>();
		out.add(route.getFirst());
		Vec3 lastKept = route.getFirst();
		for (int i = 1; i < route.size() - 1; i++) {
			Vec3 p = route.get(i);
			// Keep if height change significant or direction change
			double dy = Math.abs(p.y - lastKept.y);
			double dist = p.distanceTo(lastKept);
			if (dy > 0.35 || dist > 1.75) {
				out.add(p);
				lastKept = p;
			} else if (dist > tolerance && Math.abs(p.y - lastKept.y) > 0.08) {
				out.add(p);
				lastKept = p;
			}
		}
		out.add(route.getLast());
		return out;
	}

	/** Max automatic connection distance from config. */
	public static int maxDistance() {
		return ForestFireConfig.get().maxHoseLength;
	}

	@Nullable
	public static HosePath tryGenerateBounded(ServerLevel level, Vec3 start, Vec3 end, HosePointType startType, HosePointType endType) {
		double dist = start.distanceTo(end);
		if (dist > maxDistance() + 4) {
			return null;
		}
		return generate(level, start, end, startType, endType);
	}
}
