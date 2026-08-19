package com.peterwolf.forestfire.firefighting.pump;

import com.mojang.serialization.MapCodec;
import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.item.HoseConnectorItem;
import com.peterwolf.forestfire.item.HoseRollItem;
import com.peterwolf.forestfire.item.ModItems;
import com.peterwolf.forestfire.item.PumpFuelCanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.Explosion;
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

	/**
	 * Holding hose tools must NOT toggle the pump. Newer MC often routes item+block
	 * clicks through {@code useItemOn} and may fall back to empty-hand toggle — so we
	 * either PASS to Item.useOn or dispatch the item use ourselves.
	 */
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
		if (isHoseTool(stack) || isFuelCan(stack) || isNozzle(stack)) {
			// Force item use so hose rolls / connector / nozzle / fuel always win over power toggle
			net.minecraft.world.item.context.UseOnContext ctx =
				new net.minecraft.world.item.context.UseOnContext(level, player, hand, stack, hit);
			InteractionResult itemResult = stack.useOn(ctx);
			if (itemResult.consumesAction() || itemResult != InteractionResult.PASS) {
				return itemResult;
			}
			return InteractionResult.PASS;
		}
		// Other items / default: empty-hand style pump control
		return useWithoutItem(state, level, pos, player, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// Safety: if something called this with a hose tool, still pass through
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		if (isHoseTool(main) || isHoseTool(off) || isFuelCan(main) || isFuelCan(off) || isNozzle(main) || isNozzle(off)) {
			return InteractionResult.PASS;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof PortablePumpBlockEntity pump && player instanceof ServerPlayer serverPlayer) {
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
							"Auto intake: none — hose roll: back of pump → water"
						));
					}
					int lines = mgr.countAttackLines(pos);
					serverPlayer.sendSystemMessage(Component.literal("Auto attack lines: " + lines));
				}
			} else {
				pump.togglePower(serverPlayer);
				for (Component line : pump.statusLines()) {
					serverPlayer.sendSystemMessage(line);
				}
			}
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	/**
	 * Breaking the pump clears every automatic hose line, render path and nozzle link.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			ServerPlayer sp = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
			int n = HoseConnectionManager.get(serverLevel).clearAllOnPumpDestroyed(serverLevel, pos, sp);
			if (sp != null && n > 0) {
				sp.sendSystemMessage(Component.translatable(
					"message.peterwolfs_forestfire.pump_hoses_cleared", n));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Creative mid-click / programmatic destroy path. */
	@Override
	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			HoseConnectionManager.get(serverLevel).clearAllOnPumpDestroyed(serverLevel, pos, null);
		}
		super.destroy(level, pos, state);
	}

	@Override
	public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
		HoseConnectionManager.get(level).clearAllOnPumpDestroyed(level, pos, null);
		super.wasExploded(level, pos, explosion);
	}

	private static boolean isHoseTool(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof HoseRollItem
			|| stack.getItem() instanceof HoseConnectorItem
			|| stack.is(ModItems.HOSE_ROLL_SMALL)
			|| stack.is(ModItems.HOSE_ROLL_STANDARD)
			|| stack.is(ModItems.HOSE_ROLL_LARGE)
			|| stack.is(ModItems.HOSE_CONNECTOR)
			|| stack.is(ModItems.HOSE_ANCHOR);
	}

	private static boolean isFuelCan(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof PumpFuelCanItem || stack.is(ModItems.PUMP_FUEL_CAN));
	}

	private static boolean isNozzle(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ModItems.FIRE_HOSE_NOZZLE);
	}
}
