package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.block.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class GroundNozzleBlockEntity extends BlockEntity {
	@Nullable
	private UUID endpointId;
	private float yaw;

	public GroundNozzleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GROUND_NOZZLE, pos, state);
	}

	@Nullable
	public UUID getEndpointId() {
		return endpointId;
	}

	public void setEndpointId(UUID endpointId) {
		this.endpointId = endpointId;
		setChanged();
	}

	public float getYaw() {
		return yaw;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput tag) {
		super.saveAdditional(tag);
		if (endpointId != null) {
			tag.putString("endpointId", endpointId.toString());
		}
		tag.putFloat("yaw", yaw);
	}

	@Override
	protected void loadAdditional(ValueInput tag) {
		super.loadAdditional(tag);
		String raw = tag.getStringOr("endpointId", "");
		if (!raw.isEmpty()) {
			try {
				endpointId = UUID.fromString(raw);
			} catch (IllegalArgumentException e) {
				endpointId = null;
			}
		}
		yaw = tag.getFloatOr("yaw", 0.0F);
	}
}
