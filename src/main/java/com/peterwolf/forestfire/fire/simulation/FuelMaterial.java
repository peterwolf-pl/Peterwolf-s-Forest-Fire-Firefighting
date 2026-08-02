package com.peterwolf.forestfire.fire.simulation;

import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material behaviour profiles for wildfire fuel.
 * Values are tuned for gameplay balance, not lab physics.
 */
public enum FuelMaterial {
	// baseFuel ≈ relative burn duration units (sim ticks at full flame)
	// emberRate = how readily heat lifts glowing plant fragments (sparks)
	// Grass is surface fire: quick to start, but must last long enough for hose work.
	GRASS(0.90F, 0.12F, 0.30F, 140, 0.18F, false, false),
	DRY_LEAVES(0.95F, 0.20F, 0.55F, 90, 0.55F, true, false),
	LIVING_LEAVES(0.45F, 0.45F, 0.45F, 100, 0.40F, true, false),
	LOG(0.18F, 0.30F, 0.70F, 220, 0.22F, false, true),
	TRUNK(0.12F, 0.30F, 0.85F, 320, 0.20F, false, true),
	DEAD_BUSH(0.98F, 0.05F, 0.35F, 80, 0.28F, false, false),
	WOODEN_STRUCTURE(0.28F, 0.25F, 0.75F, 180, 0.25F, false, true),
	PEAT(0.08F, 0.70F, 0.50F, 400, 0.05F, false, true),
	NON_FLAMMABLE(0.0F, 1.0F, 0.0F, 0, 0.0F, false, false);

	/** Base chance to ignite when heated (0–1). */
	public final float igniteEase;
	/** Starting moisture (0–1). */
	public final float baseMoisture;
	/** Peak flame intensity contribution (0–1). */
	public final float flameIntensity;
	/** Ticks of fuel at FULLY_INVOLVED burn rate (scaled by tick interval). */
	public final int baseFuel;
	/** Ember production rate while flaming. */
	public final float emberRate;
	/** Participates in crown fire. */
	public final boolean crownFuel;
	/** Can smoulder and form hotspots after flames die. */
	public final boolean longBurn;

	FuelMaterial(
		float igniteEase,
		float baseMoisture,
		float flameIntensity,
		int baseFuel,
		float emberRate,
		boolean crownFuel,
		boolean longBurn
	) {
		this.igniteEase = igniteEase;
		this.baseMoisture = baseMoisture;
		this.flameIntensity = flameIntensity;
		this.baseFuel = baseFuel;
		this.emberRate = emberRate;
		this.crownFuel = crownFuel;
		this.longBurn = longBurn;
	}

	public static FuelMaterial of(BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS
			|| block == Blocks.FERN || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH
			|| state.is(BlockTags.FLOWERS)) {
			if (block == Blocks.DEAD_BUSH) {
				return DEAD_BUSH;
			}
			return GRASS;
		}
		if (block == Blocks.LEAF_LITTER || block == Blocks.VINE || block == Blocks.GLOW_LICHEN) {
			return DRY_LEAVES;
		}
		if (state.is(BlockTags.LEAVES) || block instanceof LeavesBlock) {
			// PERSISTENT describes player placement/decay, not whether a leaf is dry.
			return LIVING_LEAVES;
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
			if (state.hasProperty(RotatedPillarBlock.AXIS)
				&& state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
				return LOG;
			}
			return TRUNK;
		}
		if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_STAIRS)
			|| state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.WOODEN_TRAPDOORS) || block == Blocks.CRAFTING_TABLE || block == Blocks.BOOKSHELF
			|| block == Blocks.CHEST || block == Blocks.BARREL || block == Blocks.LADDER) {
			return WOODEN_STRUCTURE;
		}
		// Rooted dirt is the closest vanilla proxy for an organic soil layer.
		// Wet mud and muddy mangrove roots are deliberately non-flammable.
		if (block == Blocks.ROOTED_DIRT) {
			return PEAT;
		}
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.BEDS)) {
			return WOODEN_STRUCTURE;
		}
		if (state.ignitedByLava()) {
			return WOODEN_STRUCTURE;
		}
		return NON_FLAMMABLE;
	}

	public boolean flammable() {
		return this != NON_FLAMMABLE && baseFuel > 0;
	}

	/** Fuel percent 1–100 used by FireCell at creation. */
	public int initialFuelPercent() {
		if (!flammable()) {
			return 0;
		}
		// Grass/dead bush need high starting fuel — surface fire should stay lit for minutes.
		if (this == GRASS) {
			return 95;
		}
		if (this == DEAD_BUSH) {
			return 70;
		}
		// Map burn duration onto 0–100 so canopy/logs last for a real firefight.
		return Math.min(100, Math.max(30, baseFuel / 2));
	}

	/** Fuel consumed per simulation tick while flaming (small = long burn). */
	public float fuelConsumePerTick() {
		return switch (this) {
			// ~95 fuel / 0.10 ≈ 950 sim ticks × 5 game ticks ≈ several minutes of surface fire
			case GRASS -> 0.10F;
			case DEAD_BUSH -> 0.18F;
			case DRY_LEAVES, LIVING_LEAVES -> 0.28F;
			case LOG, WOODEN_STRUCTURE -> 0.18F;
			case TRUNK -> 0.12F;
			case PEAT -> 0.08F;
			case NON_FLAMMABLE -> 0.0F;
		};
	}
}
