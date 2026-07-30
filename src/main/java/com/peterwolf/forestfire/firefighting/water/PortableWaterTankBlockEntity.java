package com.peterwolf.forestfire.firefighting.water;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PortableWaterTankBlockEntity extends BlockEntity {
	private int water;
	private TankSize size;

	public PortableWaterTankBlockEntity(BlockPos pos, BlockState state) {
		this(pos, state, sizeFromState(state));
	}

	public PortableWaterTankBlockEntity(BlockPos pos, BlockState state, TankSize size) {
		super(ModBlockEntities.WATER_TANK, pos, state);
		this.size = size;
		this.water = size.capacity() / 2;
	}

	private static TankSize sizeFromState(BlockState state) {
		if (state.is(ModBlocks.WATER_TANK_LARGE)) {
			return TankSize.LARGE;
		}
		if (state.is(ModBlocks.WATER_TANK_MEDIUM)) {
			return TankSize.MEDIUM;
		}
		return TankSize.SMALL;
	}

	public int getWater() {
		return water;
	}

	public int getCapacity() {
		return size.capacity();
	}

	public int fill(int amount) {
		int before = water;
		water = Math.min(getCapacity(), water + amount);
		setChanged();
		return water - before;
	}

	public int drain(int amount) {
		int drained = Math.min(water, amount);
		water -= drained;
		setChanged();
		return drained;
	}

	@Override
	protected void saveAdditional(ValueOutput tag) {
		super.saveAdditional(tag);
		tag.putInt("water", water);
		tag.putString("size", size.name());
	}

	@Override
	protected void loadAdditional(ValueInput tag) {
		super.loadAdditional(tag);
		water = tag.getIntOr("water", 0);
		try {
			size = TankSize.valueOf(tag.getStringOr("size", sizeFromState(getBlockState()).name()));
		} catch (Exception exception) {
			size = sizeFromState(getBlockState());
		}
	}
}
