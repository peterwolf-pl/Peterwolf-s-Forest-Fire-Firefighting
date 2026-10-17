package com.peterwolf.forestfire.firefighting.nozzle;

import com.mojang.serialization.MapCodec;
import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpoint;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Ground representation of a connected fire hose nozzle.
 * Right-click empty hand: pick up. Shift-right-click: inspect status.
 */
public class GroundNozzleBlock extends BaseEntityBlock {
	public static final MapCodec<GroundNozzleBlock> CODEC = simpleCodec(GroundNozzleBlock::new);
	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 6.0, 12.0);

	public GroundNozzleBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GroundNozzleBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		HoseEndpointManager manager = HoseEndpointManager.get(serverLevel);
		Optional<HoseEndpoint> epOpt = manager.byGroundPos(pos);
		if (epOpt.isEmpty() && level.getBlockEntity(pos) instanceof GroundNozzleBlockEntity be && be.getEndpointId() != null) {
			epOpt = manager.get(be.getEndpointId());
		}
		if (epOpt.isEmpty()) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.nozzle_orphaned"));
			return InteractionResult.CONSUME;
		}
		HoseEndpoint ep = epOpt.get();
		if (player.isShiftKeyDown()) {
			// Inspect
			serverPlayer.sendSystemMessage(Component.literal("Ground nozzle"));
			serverPlayer.sendSystemMessage(Component.literal("Mode: " + ep.mode.label));
			serverPlayer.sendSystemMessage(Component.literal("Connected: " + ep.connected));
			serverPlayer.sendSystemMessage(Component.literal(String.format("Pressure: %.0f%%", ep.pressure01 * 100)));
			serverPlayer.sendSystemMessage(Component.literal("Hose length: " + ep.hoseLengthFromPump + "  Reach: " + ep.maxReach));
			return InteractionResult.CONSUME;
		}
		// Pickup with empty-hand semantics (this method is without item)
		manager.pickup(serverLevel, serverPlayer, ep);
		return InteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack stack,
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		InteractionHand hand,
		BlockHitResult hit
	) {
		// Allow pickup even if something is in hand — only empty preferred; if nozzle disconnect tool later
		if (stack.isEmpty()) {
			return useWithoutItem(state, level, pos, player, hit);
		}
		return InteractionResult.PASS;
	}

}
