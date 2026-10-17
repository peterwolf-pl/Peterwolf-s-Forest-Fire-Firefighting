package com.peterwolf.forestfire.simulation.water_drop;

import net.minecraft.world.phys.Vec3;

/**
 * Lightweight non-entity water mass falling from a bomber.
 * Server-side only; client visuals use particles from the plane.
 */
public final class AerialWaterPayload {
	public Vec3 position;
	public Vec3 velocity;
	public int remainingUnits;
	public float strength;
	public float radius;
	public int age;
	public final int maxAge;

	public AerialWaterPayload(Vec3 position, Vec3 velocity, int remainingUnits, float strength, float radius) {
		this.position = position;
		this.velocity = velocity;
		this.remainingUnits = Math.max(0, remainingUnits);
		this.strength = strength;
		this.radius = radius;
		this.age = 0;
		this.maxAge = 120;
	}

	public boolean isDead() {
		return remainingUnits <= 0 || age >= maxAge || position.y < -64.0D;
	}
}
