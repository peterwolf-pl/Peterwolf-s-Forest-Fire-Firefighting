package com.peterwolf.forestfire.fire.simulation;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material behaviour profiles for wildfire fuel.
 * Values are tuned for gameplay balance, not lab physics.
 */
public enum FuelMaterial {
	GRASS(0.85F, 0.15F, 0.25F, 8, 0.05F, false, false),
	DRY_LEAVES(0.95F, 0.20F, 0.55F, 14, 0.35F, true, false),
	LIVING_LEAVES(0.45F, 0.55F, 0.45F, 18, 0.25F, true, false),
	LOG(0.18F, 0.35F, 0.70F, 180, 0.08F, false, true),
	TRUNK(0.12F, 0.40F, 0.85F, 260, 0.06F, false, true),
	DEAD_BUSH(0.98F, 0.05F, 0.35F, 6, 0.12F, false, false),
	WOODEN_STRUCTURE(0.28F, 0.30F, 0.75F, 140, 0.10F, false, true),
	PEAT(0.08F, 0.70F, 0.50F, 400, 0.02F, false, true),
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
			|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE)) {
			if (block == Blocks.DEAD_BUSH) {
				return DEAD_BUSH;
			}
			if (state.is(BlockTags.LEAVES) || block instanceof LeavesBlock) {
				// handled below — replaceable leaves tags may overlap
			} else if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS
				|| block == Blocks.FERN || block == Blocks.LARGE_FERN || state.is(BlockTags.FLOWERS)) {
				return GRASS;
			}
		}
		if (state.is(BlockTags.LEAVES) || block instanceof LeavesBlock) {
			// Persistent leaves behave as living; non-persistent as drier canopy fuel
			if (state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)) {
				return LIVING_LEAVES;
			}
			return DRY_LEAVES;
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
			return TRUNK;
		}
		if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_STAIRS)
			|| state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.WOODEN_TRAPDOORS) || block == Blocks.CRAFTING_TABLE || block == Blocks.BOOKSHELF
			|| block == Blocks.CHEST || block == Blocks.BARREL || block == Blocks.LADDER) {
			return WOODEN_STRUCTURE;
		}
		if (block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS || block == Blocks.ROOTED_DIRT) {
			return PEAT;
		}
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.BEDS) || state.ignitedByLava()) {
			return WOODEN_STRUCTURE;
		}
		if (state.ignitedByLava() || block == Blocks.VINE || block == Blocks.GLOW_LICHEN) {
			return DRY_LEAVES;
		}
		return NON_FLAMMABLE;
	}

	public boolean flammable() {
		return this != NON_FLAMMABLE && baseFuel > 0;
	}
}
