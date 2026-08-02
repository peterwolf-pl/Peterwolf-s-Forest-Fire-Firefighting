package com.peterwolf.forestfire.firefighting.water;

import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative continuous water stream / fog cone.
 * No projectile entities — ray/cone sampling with a hard block budget.
 */
public final class NozzleWaterSimulation {
	private static final int MAX_BLOCKS_PER_TICK = 48;

	private NozzleWaterSimulation() {
	}

	/**
	 * @param pressure01 0–1 effective pressure at nozzle
	 * @return water units used for scoring
	 */
	public static float spray(
		ServerLevel level,
		ServerPlayer player,
		NozzleMode mode,
		float pressure01
	) {
		if (!mode.allowsFlow() || pressure01 <= 0.05F) {
			return 0.0F;
		}

		float effectivePressure = Mth.clamp(pressure01, 0.05F, 1.0F);
		float range = mode.range * (0.45F + 0.55F * effectivePressure);
		float cooling = mode.cooling * (0.5F + 0.5F * effectivePressure);
		float radius = mode.radius * (0.7F + 0.3F * effectivePressure);

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		FireSimulation sim = FireSimulation.get(level);
		float used = 0.0F;
		int budget = MAX_BLOCKS_PER_TICK;

		if (mode == NozzleMode.STRAIGHT_STREAM) {
			// Narrow ray along look
			HitResult hit = player.pick(range, 0.0F, false);
			Vec3 end = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation()
				: eye.add(look.scale(range));
			int steps = Math.max(1, (int) Math.ceil(range));
			for (int i = 1; i <= steps && budget > 0; i++) {
				double t = i / (double) steps;
				Vec3 p = eye.lerp(end, t);
				BlockPos pos = BlockPos.containing(p);
				float falloff = 1.0F - (float) t * 0.45F;
				used += sim.applyWater(pos, cooling * falloff * 0.85F, Math.max(0.4F, radius * 0.5F));
				budget--;
			}
			spawnStreamParticles(level, eye, end, effectivePressure, false);
		} else {
			// Cone sample for fog
			int rings = mode == NozzleMode.WIDE_FOG ? 3 : 2;
			int rays = mode == NozzleMode.WIDE_FOG ? 10 : 7;
			for (int ray = 0; ray < rays && budget > 0; ray++) {
				float yawOff = (ray / (float) rays - 0.5F) * radius * 18.0F;
				float pitchOff = ((ray % 3) - 1) * radius * 6.0F;
				Vec3 dir = rotateLook(look, yawOff, pitchOff);
				int steps = Math.max(2, (int) Math.ceil(range));
				for (int i = 1; i <= steps && budget > 0; i++) {
					if (i % (rings == 3 ? 1 : 2) != 0 && i != steps) {
						continue;
					}
					double dist = (i / (double) steps) * range;
					Vec3 p = eye.add(dir.scale(dist));
					BlockPos pos = BlockPos.containing(p);
					float falloff = 1.0F - (float) (dist / range) * 0.5F;
					float r = radius * (0.4F + (float) (dist / range) * 0.8F);
					used += sim.applyWater(pos, cooling * falloff * 0.55F, r);
					budget--;
				}
			}
			Vec3 end = eye.add(look.scale(range * 0.85));
			spawnStreamParticles(level, eye, end, effectivePressure, true);
		}

		// Wide fog softens operator heat (small wetness near player)
		if (mode == NozzleMode.WIDE_FOG) {
			sim.applyWater(player.blockPosition(), 0.15F * effectivePressure, 1.2F);
		}

		final float waterUsed = used;
		IncidentManager manager = IncidentManager.get(level);
		manager.openIncidents().stream().findFirst().ifPresent(incident -> {
			incident.recordWater(waterUsed * 10.0F);
			incident.addParticipant(player.getUUID());
			manager.markDirty();
		});

		float pitch = 1.2F + effectivePressure * 0.4F;
		float volume = 0.25F + effectivePressure * 0.35F;
		if (effectivePressure < 0.35F) {
			pitch = 0.75F;
			volume = 0.2F;
		}
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, volume, pitch);
		return used;
	}

	private static Vec3 rotateLook(Vec3 look, float yawDeg, float pitchDeg) {
		// Approximate local yaw/pitch offset around look vector
		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);
		double cosY = Math.cos(yaw);
		double sinY = Math.sin(yaw);
		double cosP = Math.cos(pitch);
		double sinP = Math.sin(pitch);
		double x = look.x * cosY - look.z * sinY;
		double z = look.x * sinY + look.z * cosY;
		double y = look.y * cosP - Math.sqrt(x * x + z * z) * sinP * 0.15;
		return new Vec3(x, y, z).normalize();
	}

	private static void spawnStreamParticles(ServerLevel level, Vec3 start, Vec3 end, float pressure, boolean fog) {
		int steps = fog ? 6 : 5;
		for (int i = 0; i < steps; i++) {
			double t = i / (double) steps;
			double px = start.x + (end.x - start.x) * t;
			double py = start.y + (end.y - start.y) * t;
			double pz = start.z + (end.z - start.z) * t;
			double spread = fog ? 0.18 + t * 0.25 : 0.04;
			level.sendParticles(ParticleTypes.SPLASH, px, py, pz, fog ? 4 : 2, spread, spread * 0.5, spread, 0.01);
			level.sendParticles(ParticleTypes.RAIN, px, py, pz, fog ? 3 : 1, spread * 0.5, spread * 0.3, spread * 0.5, 0.0);
			if (pressure > 0.6F && i > steps / 2) {
				level.sendParticles(ParticleTypes.CLOUD, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}
}
