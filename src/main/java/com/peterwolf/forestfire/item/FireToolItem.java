package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.simulation.FuelMaterial;
import com.peterwolf.forestfire.world.FirebreakTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FireToolItem extends Item {
	public enum ToolType {
		AXE,
		PULASKI,
		SHOVEL,
		RAKE
	}

	private final ToolType toolType;

	public FireToolItem(Properties properties, ToolType toolType) {
		super(properties.stacksTo(1).durability(500));
		this.toolType = toolType;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		LevelAccess access = LevelAccess.of(context);
		if (access == null) {
			return InteractionResult.PASS;
		}
		ServerLevel level = access.level;
		ServerPlayer player = access.player;
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		FireSimulation sim = FireSimulation.get(level);
		boolean acted = false;

		switch (toolType) {
			case AXE -> {
				if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_DOORS)) {
					level.destroyBlock(pos, true, player);
					FireCell cell = sim.getCell(pos);
					if (cell != null) {
						cell.addHeat(-20);
						cell.addMoisture(5);
					}
					acted = true;
				}
			}
			case PULASKI -> {
				// Dig / clear vegetation / expose roots
				if (state.is(BlockTags.REPLACEABLE) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
					|| state.is(Blocks.ROOTED_DIRT) || state.is(BlockTags.LEAVES)) {
					if (state.is(Blocks.GRASS_BLOCK)) {
						level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
					} else if (state.is(Blocks.DIRT) || state.is(Blocks.ROOTED_DIRT)) {
						level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
					} else {
						level.destroyBlock(pos, false, player);
					}
					sim.firebreaks().mark(pos, FirebreakTracker.Type.TRENCH, -1);
					exposeHotspot(sim, pos);
					acted = true;
				}
			}
			case SHOVEL -> {
				// Dirt throw / trench / firebreak
				if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
					|| state.is(Blocks.SAND) || state.is(Blocks.GRAVEL)) {
					if (state.is(Blocks.GRASS_BLOCK)) {
						level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
					}
					sim.applyWater(pos.above(), 0.4F, 1.0F); // smother with dirt approximation
					sim.firebreaks().mark(pos, FirebreakTracker.Type.BARE_DIRT, -1);
					// Smother flames above
					BlockPos above = pos.above();
					if (level.getBlockState(above).is(Blocks.FIRE)) {
						level.removeBlock(above, false);
					}
					acted = true;
				} else if (state.is(BlockTags.REPLACEABLE)) {
					level.destroyBlock(pos, false, player);
					sim.firebreaks().mark(pos, FirebreakTracker.Type.CLEARED_GRASS, -1);
					acted = true;
				}
			}
			case RAKE -> {
				if (state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS)
					|| state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || FuelMaterial.of(state) == FuelMaterial.DEAD_BUSH) {
					level.destroyBlock(pos, false, player);
					sim.firebreaks().mark(pos, FirebreakTracker.Type.CLEARED_GRASS, -1);
					acted = true;
				}
			}
		}

		if (acted) {
			context.getItemInHand().hurtAndBreak(1, player, context.getHand());
			player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.tool_used", toolType.name()));
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	private static void exposeHotspot(FireSimulation sim, BlockPos pos) {
		FireCell cell = sim.getCell(pos);
		if (cell != null && cell.stage().isHotspotRisk()) {
			cell.addHeat(5); // expose — shows heat more clearly for scanner
		}
		FireCell below = sim.getCell(pos.below());
		if (below != null && below.stage().isHotspotRisk()) {
			below.addHeat(8);
		}
	}

	private record LevelAccess(ServerLevel level, ServerPlayer player) {
		static LevelAccess of(UseOnContext context) {
			if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
				return new LevelAccess(level, player);
			}
			return null;
		}
	}
}
