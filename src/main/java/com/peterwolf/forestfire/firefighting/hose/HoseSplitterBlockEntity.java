package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class HoseSplitterBlockEntity extends BlockEntity {
	private int openLines = 2;

	public HoseSplitterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.HOSE_SPLITTER, pos, state);
	}

	public int getOpenLines() {
		return openLines;
	}

	public int cycleOpenLines() {
		openLines = openLines >= 3 ? 1 : openLines + 1;
		setChanged();
		return openLines;
	}

	@Override
	protected void saveAdditional(ValueOutput tag) {
		super.saveAdditional(tag);
		tag.putInt("openLines", openLines);
	}

	@Override
	protected void loadAdditional(ValueInput tag) {
		super.loadAdditional(tag);
		openLines = Math.max(1, Math.min(3, tag.getIntOr("openLines", 2)));
	}
}
