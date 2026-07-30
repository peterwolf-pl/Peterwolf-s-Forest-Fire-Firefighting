package com.peterwolf.forestfire.firefighting.water;

import com.mojang.serialization.MapCodec;
import com.peterwolf.forestfire.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class PortableWaterTankBlock extends BaseEntityBlock {
	public static final MapCodec<PortableWaterTankBlock> CODEC = simpleCodec(props -> new PortableWaterTankBlock(props, TankSize.SMALL));
	private final TankSize size;

	public PortableWaterTankBlock(BlockBehaviour.Properties properties, TankSize size) {
		super(properties);
		this.size = size;
	}

	public TankSize size() {
		return size;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PortableWaterTankBlockEntity(pos, state, size);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(pos) instanceof PortableWaterTankBlockEntity tank) {
			serverPlayer.sendSystemMessage(Component.translatable(
				"message.peterwolfs_forestfire.tank_status",
				tank.getWater(),
				tank.getCapacity()
			));
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}
}
