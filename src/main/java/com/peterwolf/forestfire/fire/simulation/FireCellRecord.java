package com.peterwolf.forestfire.fire.simulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** Serializable fire cell for SavedData. */
public record FireCellRecord(
	BlockPos pos,
	byte heat,
	byte moisture,
	byte fuel,
	byte stageOrdinal,
	short reignitionTimer,
	byte materialOrdinal,
	int incidentId,
	byte integrity
) {
	public static final Codec<FireCellRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(FireCellRecord::pos),
		Codec.BYTE.fieldOf("heat").forGetter(FireCellRecord::heat),
		Codec.BYTE.fieldOf("moisture").forGetter(FireCellRecord::moisture),
		Codec.BYTE.fieldOf("fuel").forGetter(FireCellRecord::fuel),
		Codec.BYTE.fieldOf("stage").forGetter(FireCellRecord::stageOrdinal),
		Codec.SHORT.fieldOf("reignite").forGetter(FireCellRecord::reignitionTimer),
		Codec.BYTE.fieldOf("material").forGetter(FireCellRecord::materialOrdinal),
		Codec.INT.fieldOf("incident").forGetter(FireCellRecord::incidentId),
		Codec.BYTE.optionalFieldOf("integrity", (byte) 100).forGetter(FireCellRecord::integrity)
	).apply(instance, FireCellRecord::new));

	public static FireCellRecord from(BlockPos pos, FireCell cell) {
		return new FireCellRecord(
			pos.immutable(),
			cell.heat,
			cell.moisture,
			cell.fuel,
			cell.stageOrdinal,
			cell.reignitionTimer,
			cell.materialOrdinal,
			cell.incidentId,
			cell.integrity
		);
	}

	public FireCell toCell() {
		FireCell cell = new FireCell();
		cell.heat = heat;
		cell.moisture = moisture;
		cell.fuel = fuel;
		cell.stageOrdinal = stageOrdinal;
		cell.reignitionTimer = reignitionTimer;
		cell.materialOrdinal = materialOrdinal;
		cell.incidentId = incidentId;
		cell.integrity = integrity;
		return cell;
	}
}
