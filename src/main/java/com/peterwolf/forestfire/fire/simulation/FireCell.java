package com.peterwolf.forestfire.fire.simulation;

/**
 * Compact per-block wildfire state. Stored only for blocks that enter the simulation.
 * heat/moisture/fuel are 0–100 scale integers for bandwidth-friendly networking later.
 */
public final class FireCell {
	public byte heat;
	public byte moisture;
	public byte fuel;
	public byte stageOrdinal;
	public short reignitionTimer;
	public byte materialOrdinal;
	/** Incident id that owns this cell (0 = none / orphan). */
	public int incidentId;
	/** Structural integrity for trunks / buildings (0–100). */
	public byte integrity;
	/**
	 * Fractional residues keep sub-unit simulation rates deterministic instead of
	 * rounding every fire tick to zero. They are persisted with the cell.
	 */
	public float heatRemainder;
	public float moistureRemainder;
	public float fuelRemainder;

	public FireCell() {
	}

	public FireCell(FuelMaterial material, int incidentId) {
		this.materialOrdinal = (byte) material.ordinal();
		this.moisture = (byte) Math.round(material.baseMoisture * 100.0F);
		// 0–100 scale mapped from material burn duration (not /3 — that died in seconds).
		this.fuel = (byte) material.initialFuelPercent();
		this.heat = 0;
		this.stageOrdinal = (byte) BurnStage.UNBURNED.ordinal();
		this.reignitionTimer = 0;
		this.incidentId = incidentId;
		this.integrity = 100;
	}

	public BurnStage stage() {
		return BurnStage.byOrdinal(stageOrdinal & 0xFF);
	}

	public void setStage(BurnStage stage) {
		this.stageOrdinal = (byte) stage.ordinal();
	}

	public FuelMaterial material() {
		FuelMaterial[] values = FuelMaterial.values();
		int index = materialOrdinal & 0xFF;
		if (index < 0 || index >= values.length) {
			return FuelMaterial.NON_FLAMMABLE;
		}
		return values[index];
	}

	public float heat01() {
		return (heat & 0xFF) / 100.0F;
	}

	public float moisture01() {
		return (moisture & 0xFF) / 100.0F;
	}

	public float fuel01() {
		return (fuel & 0xFF) / 100.0F;
	}

	public void addHeat(float amount) {
		if (!Float.isFinite(amount) || amount == 0.0F) {
			return;
		}
		float accumulated = heatRemainder + amount;
		int whole = wholeUnitsTowardZero(accumulated);
		heatRemainder = accumulated - whole;
		int current = heat & 0xFF;
		int next = Math.min(100, Math.max(0, current + whole));
		if (next != current + whole) {
			heatRemainder = 0.0F;
		}
		if ((next == 100 && heatRemainder > 0.0F) || (next == 0 && heatRemainder < 0.0F)) {
			heatRemainder = 0.0F;
		}
		heat = (byte) next;
	}

	public void addMoisture(float amount) {
		if (!Float.isFinite(amount) || amount == 0.0F) {
			return;
		}
		float accumulated = moistureRemainder + amount;
		int whole = wholeUnitsTowardZero(accumulated);
		moistureRemainder = accumulated - whole;
		int current = moisture & 0xFF;
		int next = Math.min(100, Math.max(0, current + whole));
		if (next != current + whole) {
			moistureRemainder = 0.0F;
		}
		if ((next == 100 && moistureRemainder > 0.0F) || (next == 0 && moistureRemainder < 0.0F)) {
			moistureRemainder = 0.0F;
		}
		moisture = (byte) next;
	}

	public void consumeFuel(float amount) {
		if (!Float.isFinite(amount) || amount <= 0.0F) {
			return;
		}
		float accumulated = fuelRemainder + amount;
		int whole = (int) Math.floor(accumulated);
		fuelRemainder = accumulated - whole;
		int next = Math.max(0, (fuel & 0xFF) - whole);
		if (next == 0) {
			fuelRemainder = 0.0F;
		}
		fuel = (byte) next;
	}

	public void setHeat(int value) {
		heat = (byte) Math.min(100, Math.max(0, value));
		heatRemainder = 0.0F;
	}

	public void setMoisture(int value) {
		moisture = (byte) Math.min(100, Math.max(0, value));
		moistureRemainder = 0.0F;
	}

	public void setFuel(int value) {
		fuel = (byte) Math.min(100, Math.max(0, value));
		fuelRemainder = 0.0F;
	}

	private static int wholeUnitsTowardZero(float value) {
		return value >= 0.0F ? (int) Math.floor(value) : (int) Math.ceil(value);
	}

	public ThermalReading thermalReading() {
		int h = heat & 0xFF;
		if (h < 15) {
			return ThermalReading.COLD;
		}
		if (h < 35) {
			return ThermalReading.WARM;
		}
		if (h < 65) {
			return ThermalReading.HOT;
		}
		return ThermalReading.CRITICAL;
	}

	public FireCell copy() {
		FireCell copy = new FireCell();
		copy.heat = heat;
		copy.moisture = moisture;
		copy.fuel = fuel;
		copy.stageOrdinal = stageOrdinal;
		copy.reignitionTimer = reignitionTimer;
		copy.materialOrdinal = materialOrdinal;
		copy.incidentId = incidentId;
		copy.integrity = integrity;
		copy.heatRemainder = heatRemainder;
		copy.moistureRemainder = moistureRemainder;
		copy.fuelRemainder = fuelRemainder;
		return copy;
	}

	public enum ThermalReading {
		COLD,
		WARM,
		HOT,
		CRITICAL
	}
}
