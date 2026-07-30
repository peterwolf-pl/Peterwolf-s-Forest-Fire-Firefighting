package com.peterwolf.forestfire.firefighting.nozzle;

public enum NozzleMode {
	STRAIGHT_STREAM(12.0F, 0.6F, 0.45F, "Straight Stream"),
	NARROW_FOG(7.0F, 1.4F, 0.9F, "Narrow Fog"),
	WIDE_FOG(3.5F, 2.4F, 1.2F, "Wide Fog"),
	SHUTOFF(0.0F, 0.0F, 0.0F, "Shutoff");

	public final float range;
	public final float radius;
	public final float cooling;
	public final String label;

	NozzleMode(float range, float radius, float cooling, String label) {
		this.range = range;
		this.radius = radius;
		this.cooling = cooling;
		this.label = label;
	}

	public NozzleMode next() {
		NozzleMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
