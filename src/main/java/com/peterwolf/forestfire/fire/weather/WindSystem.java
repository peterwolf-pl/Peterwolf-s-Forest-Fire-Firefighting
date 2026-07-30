package com.peterwolf.forestfire.fire.weather;

import com.peterwolf.forestfire.config.ForestFireConfig;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Lightweight regional wind. Changes gradually — never snaps every tick.
 * Direction is stored as yaw degrees (0 = south in Minecraft convention for display;
 * spread math uses unit vector).
 */
public final class WindSystem {
	private float speed;
	private float yawDegrees;
	private float targetSpeed;
	private float targetYaw;
	private int ticksUntilChange;
	private long lastGameTime = Long.MIN_VALUE;

	public WindSystem() {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		this.speed = 0.25F;
		this.yawDegrees = 0.0F;
		this.targetSpeed = speed;
		this.targetYaw = yawDegrees;
		this.ticksUntilChange = cfg.windChangeIntervalTicks;
	}

	public void tick(ServerLevel level) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		if (!cfg.windEnabled) {
			speed = 0.0F;
			return;
		}
		long time = level.getGameTime();
		if (lastGameTime == time) {
			return;
		}
		lastGameTime = time;
		RandomSource random = level.getRandom();

		if (--ticksUntilChange <= 0) {
			ticksUntilChange = Math.max(200, cfg.windChangeIntervalTicks + random.nextInt(cfg.windChangeIntervalTicks / 2 + 1));
			float delta = (random.nextFloat() * 2.0F - 1.0F) * cfg.windChangeStrength;
			targetSpeed = Mth.clamp(targetSpeed + delta, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
			targetYaw = Mth.wrapDegrees(targetYaw + (random.nextFloat() * 2.0F - 1.0F) * 35.0F * cfg.windChangeStrength * 4.0F);
		}

		// Smooth interpolation toward targets
		speed = Mth.lerp(0.02F, speed, targetSpeed);
		float yawDiff = Mth.wrapDegrees(targetYaw - yawDegrees);
		yawDegrees = Mth.wrapDegrees(yawDegrees + yawDiff * 0.02F);
	}

	public float speed() {
		return speed;
	}

	public float yawDegrees() {
		return yawDegrees;
	}

	/** Unit X component of wind (east positive). */
	public float dx() {
		float rad = yawDegrees * Mth.DEG_TO_RAD;
		return Mth.sin(rad);
	}

	/** Unit Z component of wind (south positive). */
	public float dz() {
		float rad = yawDegrees * Mth.DEG_TO_RAD;
		return Mth.cos(rad);
	}

	public Direction cardinal() {
		float yaw = Mth.wrapDegrees(yawDegrees);
		if (yaw >= -45 && yaw < 45) {
			return Direction.SOUTH;
		}
		if (yaw >= 45 && yaw < 135) {
			return Direction.WEST;
		}
		if (yaw >= -135 && yaw < -45) {
			return Direction.EAST;
		}
		return Direction.NORTH;
	}

	public String compassLabel() {
		float yaw = Mth.wrapDegrees(yawDegrees);
		// 8-point compass
		String[] labels = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		int index = Math.round(((yaw + 180.0F) % 360.0F) / 45.0F) % 8;
		// Map: 0 deg = south in our vector model
		int mapped = Math.floorMod(Math.round(yaw / 45.0F), 8);
		return labels[mapped];
	}

	public void load(float speed, float yaw) {
		this.speed = speed;
		this.yawDegrees = yaw;
		this.targetSpeed = speed;
		this.targetYaw = yaw;
	}
}
