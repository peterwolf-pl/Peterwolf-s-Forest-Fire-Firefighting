package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Attack hose segment laid on terrain. */
public class HoseBlock extends Block implements SimpleWaterloggedBlock {
	public static final MapCodec<HoseBlock> CODEC = simpleCodec(HoseBlock::new);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	public static final BooleanProperty PRESSURISED = BooleanProperty.create("pressurised");
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape SHAPE_X = Block.box(0.0, 0.0, 5.5, 16.0, 3.0, 10.5);
	private static final VoxelShape SHAPE_Z = Block.box(5.5, 0.0, 0.0, 10.5, 3.0, 16.0);
	private static final VoxelShape SHAPE_PRESSURE_X = Block.box(0.0, 0.0, 5.0, 16.0, 4.0, 11.0);
	private static final VoxelShape SHAPE_PRESSURE_Z = Block.box(5.0, 0.0, 0.0, 11.0, 4.0, 16.0);

	public HoseBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(WATERLOGGED, false)
			.setValue(PRESSURISED, false)
			.setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WATERLOGGED, PRESSURISED, AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		return this.defaultBlockState()
			.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER)
			.setValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		boolean xAxis = state.getValue(AXIS) == Direction.Axis.X;
		if (state.getValue(PRESSURISED)) {
			return xAxis ? SHAPE_PRESSURE_X : SHAPE_PRESSURE_Z;
		}
		return xAxis ? SHAPE_X : SHAPE_Z;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}
}
