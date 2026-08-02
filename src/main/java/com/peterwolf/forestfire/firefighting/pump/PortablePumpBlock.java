package com.peterwolf.forestfire.firefighting.pump;

import com.mojang.serialization.MapCodec;
import com.peterwolf.forestfire.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PortablePumpBlock extends BaseEntityBlock {
	public static final MapCodec<PortablePumpBlock> CODEC = simpleCodec(PortablePumpBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 2.0, 16.0, 15.0, 14.0);

	public PortablePumpBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
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
		return new PortablePumpBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.PORTABLE_PUMP, PortablePumpBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof PortablePumpBlockEntity pump && player instanceof ServerPlayer serverPlayer) {
			// Right-click = toggle ON/OFF (main action)
			// Shift + right-click = status + hose connection summary
			if (player.isShiftKeyDown()) {
				for (Component line : pump.statusLines()) {
					serverPlayer.sendSystemMessage(line);
				}
				if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
					var mgr = com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager.get(serverLevel);
					var intake = mgr.scanIntake(serverLevel, pos);
					if (intake.hasIntake()) {
						serverPlayer.sendSystemMessage(Component.literal(
							"Auto intake: OK len=" + intake.intakeLength()
								+ " eff=" + String.format("%.0f%%", intake.efficiency() * 100)
						));
					} else {
						serverPlayer.sendSystemMessage(Component.literal(
							"Auto intake: none — use Hose Connector: pump intake face → water"
						));
					}
					int lines = mgr.countAttackLines(pos);
					serverPlayer.sendSystemMessage(Component.literal("Auto attack lines: " + lines));
				}
			} else {
				pump.togglePower(serverPlayer);
				// Always show short status after toggle
				for (Component line : pump.statusLines()) {
					serverPlayer.sendSystemMessage(line);
				}
			}
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}
}
