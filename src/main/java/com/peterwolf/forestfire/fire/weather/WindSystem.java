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
	/** Extra speed from large-fire convection (firestorm). Smoothly lerped. */
	private float firestormBoost;
	private float targetFirestormBoost;

	public WindSystem() {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		this.speed = cfg.configuredWindSpeed;
		this.yawDegrees = Mth.wrapDegrees(cfg.configuredWindYawDegrees);
		this.targetSpeed = speed;
		this.targetYaw = yawDegrees;
		this.ticksUntilChange = cfg.windChangeIntervalTicks;
	}

	public void tick(ServerLevel level) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		if (!cfg.windEnabled) {
			firestormBoost = 0.0F;
			targetFirestormBoost = 0.0F;
			return;
		}
		long time = level.getGameTime();
		if (lastGameTime == time) {
			return;
		}
		lastGameTime = time;
		RandomSource random = level.getRandom();

		if (!cfg.dynamicWind) {
			targetSpeed = cfg.configuredWindSpeed;
			targetYaw = Mth.wrapDegrees(cfg.configuredWindYawDegrees);
			ticksUntilChange = cfg.windChangeIntervalTicks;
		} else if (--ticksUntilChange <= 0) {
			ticksUntilChange = Math.max(200, cfg.windChangeIntervalTicks + random.nextInt(cfg.windChangeIntervalTicks / 2 + 1));
			float delta = (random.nextFloat() * 2.0F - 1.0F) * cfg.windChangeStrength;
			targetSpeed = Mth.clamp(targetSpeed + delta, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
			targetYaw = Mth.wrapDegrees(
				targetYaw + (random.nextFloat() * 2.0F - 1.0F) * cfg.windDirectionChangeDegrees
			);
		}

		// Smooth interpolation toward targets
		speed = Mth.lerp(cfg.windSmoothingFactor, speed, targetSpeed);
		float yawDiff = Mth.wrapDegrees(targetYaw - yawDegrees);
		yawDegrees = Mth.wrapDegrees(yawDegrees + yawDiff * cfg.windSmoothingFactor);
		// Fire-driven wind builds/decays faster than weather wind
		firestormBoost = Mth.lerp(0.08F, firestormBoost, targetFirestormBoost);
	}

	/**
	 * Apply fire-induced wind from a large hot burn (call each fire tick with current FirestormState).
	 */
	public void applyFirestorm(FirestormState storm) {
		if (storm == null || !storm.active) {
			targetFirestormBoost = 0.0F;
			return;
		}
		targetFirestormBoost = storm.windBoost;
	}

	/** Ambient weather wind only (no firestorm). */
	public float baseSpeed() {
		return speed;
	}

	/** Ambient weather wind used outside the compact firestorm cluster. */
	public float speed() {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		if (!cfg.windEnabled) {
			return 0.0F;
		}
		return Mth.clamp(speed, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
	}

	/** Local effective wind inside the active firestorm cluster. */
	public float firestormSpeed() {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		if (!cfg.windEnabled) {
			return 0.0F;
		}
		float cap = cfg.maximumWindSpeed + cfg.firestormWindBoostMax;
		return Mth.clamp(speed + firestormBoost, cfg.minimumWindSpeed, cap);
	}

	public float firestormBoost() {
		return firestormBoost;
	}

	public float yawDegrees() {
		return yawDegrees;
	}

	/** Unit X component of wind (east positive). */
	public float dx() {
		float rad = yawDegrees * Mth.DEG_TO_RAD;
		// Minecraft yaw +90 points west, hence negative world X.
		return -Mth.sin(rad);
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
		// Map: 0 deg = south in our vector model
		int mapped = Math.floorMod(Math.round(yaw / 45.0F), 8);
		return labels[mapped];
	}

	/**
	 * Immediately set the ambient wind for this dimension. Dynamic mode may
	 * gradually move away from this state at the next configured weather change.
	 */
	public void setWind(float speed, float yaw) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		float safeSpeed = Float.isFinite(speed) ? speed : cfg.configuredWindSpeed;
		float safeYaw = Float.isFinite(yaw) ? yaw : cfg.configuredWindYawDegrees;
		this.speed = Mth.clamp(safeSpeed, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
		this.yawDegrees = Mth.wrapDegrees(safeYaw);
		this.targetSpeed = this.speed;
		this.targetYaw = this.yawDegrees;
		this.ticksUntilChange = cfg.windChangeIntervalTicks;
	}

	public void setSpeed(float speed) {
		setWind(speed, yawDegrees);
	}

	public void setYawDegrees(float yaw) {
		setWind(speed, yaw);
	}

	public void resetToConfigured() {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		setWind(cfg.configuredWindSpeed, cfg.configuredWindYawDegrees);
	}

	public void load(float speed, float yaw) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		if (!cfg.dynamicWind) {
			setWind(cfg.configuredWindSpeed, cfg.configuredWindYawDegrees);
			return;
		}
		setWind(speed, yaw);
	}
}
