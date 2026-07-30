package com.peterwolf.forestfire.fire.spread;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Lightweight flying-ember spot-fire system. No persistent entities.
 */
public final class EmberSystem {
	private final List<Ember> embers = new ArrayList<>();

	public boolean spawn(ServerLevel level, BlockPos origin, WindSystem wind, int incidentId, ForestFireConfig.Data cfg) {
		if (embers.size() >= cfg.maxEmbersPerTick * 8) {
			return false;
		}
		if (WeatherFireModel.isRainingAt(level, origin) && level.getRandom().nextFloat() < 0.7F) {
			return false;
		}
		RandomSource random = level.getRandom();
		float dist = 4.0F + wind.speed() * 18.0F + random.nextFloat() * 6.0F;
		int tx = origin.getX() + Math.round(wind.dx() * dist) + random.nextInt(3) - 1;
		int tz = origin.getZ() + Math.round(wind.dz() * dist) + random.nextInt(3) - 1;
		int ty = origin.getY() + 2 + random.nextInt(4);
		embers.add(new Ember(origin.immutable(), new BlockPos(tx, ty, tz), incidentId, 20 + random.nextInt(30)));
		return true;
	}

	public void tick(ServerLevel level, FireSimulation simulation, ForestFireConfig.Data cfg) {
		Iterator<Ember> it = embers.iterator();
		while (it.hasNext()) {
			Ember ember = it.next();
			ember.life--;
			// Visual travel
			if (level.getRandom().nextBoolean() && level.isLoaded(ember.origin)) {
				double px = ember.origin.getX() + 0.5 + (ember.target.getX() - ember.origin.getX()) * (1.0 - ember.life / 40.0);
				double py = ember.origin.getY() + 1.5;
				double pz = ember.origin.getZ() + 0.5 + (ember.target.getZ() - ember.origin.getZ()) * (1.0 - ember.life / 40.0);
				level.sendParticles(ParticleTypes.LAVA, px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
			}
			if (ember.life > 0) {
				continue;
			}
			it.remove();
			BlockPos land = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ember.target);
			if (!level.isLoaded(land)) {
				continue;
			}
			if (level.getRandom().nextFloat() < cfg.emberSpotFireChance) {
				simulation.tryIgnite(land, ember.incidentId, 35, false);
				// also try one block up for grass/leaves
				simulation.tryIgnite(land.above(), ember.incidentId, 30, false);
			}
		}
	}

	private static final class Ember {
		final BlockPos origin;
		final BlockPos target;
		final int incidentId;
		int life;

		Ember(BlockPos origin, BlockPos target, int incidentId, int life) {
			this.origin = origin;
			this.target = target;
			this.incidentId = incidentId;
			this.life = life;
		}
	}
}
