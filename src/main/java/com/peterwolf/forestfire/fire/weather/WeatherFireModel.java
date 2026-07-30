package com.peterwolf.forestfire.fire.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Simplified environmental modifiers for wildfire behaviour.
 */
public final class WeatherFireModel {
	private WeatherFireModel() {
	}

	public static float humidity01(ServerLevel level, BlockPos pos) {
		boolean raining = level.isRaining();
		boolean thundering = level.isThundering();
		Biome biome = level.getBiome(pos).value();
		boolean humidBiome = biome.getBaseTemperature() < 0.5F || biome.hasPrecipitation();
		float base = humidBiome ? 0.55F : 0.30F;
		if (raining) {
			base += 0.30F;
		}
		if (thundering) {
			base += 0.10F;
		}
		// Recent rainfall approximation: raining or biome wet
		if (level.isRainingAt(pos)) {
			base += 0.15F;
		}
		return Math.min(1.0F, base);
	}

	public static float temperature01(ServerLevel level, BlockPos pos) {
		Biome biome = level.getBiome(pos).value();
		float temp = biome.getBaseTemperature();
		// Daytime boost
		long dayTime = level.getOverworldClockTime() % 24000L;
		boolean day = dayTime > 0 && dayTime < 12000;
		if (day) {
			temp += 0.15F;
		}
		return Math.min(1.0F, Math.max(0.0F, temp));
	}

	public static boolean isRainingAt(ServerLevel level, BlockPos pos) {
		return level.isRaining() && level.isRainingAt(pos);
	}

	public static String recentRainfallLabel(ServerLevel level, BlockPos pos) {
		if (level.isRainingAt(pos)) {
			return "active";
		}
		if (level.isRaining()) {
			return "nearby";
		}
		return "none";
	}

	public static FireDangerLevel danger(ServerLevel level, BlockPos pos, WindSystem wind) {
		float humidity = humidity01(level, pos);
		float temperature = temperature01(level, pos);
		float windFactor = wind.speed();
		float dryness = 1.0F - humidity;
		float score = dryness * 0.45F + temperature * 0.30F + windFactor * 0.25F;
		if (level.isRaining()) {
			score *= 0.45F;
		}
		if (score < 0.25F) {
			return FireDangerLevel.LOW;
		}
		if (score < 0.40F) {
			return FireDangerLevel.MODERATE;
		}
		if (score < 0.55F) {
			return FireDangerLevel.HIGH;
		}
		if (score < 0.72F) {
			return FireDangerLevel.VERY_HIGH;
		}
		return FireDangerLevel.EXTREME;
	}

	/** Simple slope factor: positive = uphill in given direction. */
	public static float slopeFactor(ServerLevel level, BlockPos from, int dx, int dz) {
		int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ());
		int y1 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX() + dx, from.getZ() + dz);
		int delta = y1 - y0;
		if (delta > 0) {
			return Math.min(1.5F, 1.0F + delta * 0.12F);
		}
		if (delta < 0) {
			return Math.max(0.4F, 1.0F + delta * 0.08F);
		}
		return 1.0F;
	}
}
