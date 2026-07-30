package com.peterwolf.forestfire.fire.heat;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.BurnStage;
import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.item.AirTankItem;
import com.peterwolf.forestfire.item.ModItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Radiant heat and smoke exposure for firefighters near active fire cells.
 */
public final class HeatExposureSystem {
	public enum Level {
		SAFE,
		WARM,
		HOT,
		DANGEROUS,
		CRITICAL
	}

	private HeatExposureSystem() {
	}

	public static void tick(ServerLevel level) {
		if (!ForestFireConfig.get().heatExposureEnabled) {
			return;
		}
		if (level.getGameTime() % 20L != 0L) {
			return;
		}
		FireSimulation sim = FireSimulation.get(level);
		if (sim.cells().isEmpty()) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			apply(player, sim);
		}
	}

	private static void apply(ServerPlayer player, FireSimulation sim) {
		BlockPos origin = player.blockPosition();
		float heatScore = 0.0F;
		float smokeScore = 0.0F;
		int checked = 0;
		for (Map.Entry<Long, FireCell> entry : sim.cells().entrySet()) {
			if (checked++ > 200) {
				break;
			}
			FireCell cell = entry.getValue();
			BurnStage stage = cell.stage();
			if (!stage.isBurning() && stage != BurnStage.REIGNITION_RISK) {
				continue;
			}
			BlockPos pos = BlockPos.of(entry.getKey());
			double distSq = pos.distSqr(origin);
			if (distSq > 12 * 12) {
				continue;
			}
			double dist = Math.sqrt(distSq);
			float falloff = (float) (1.0 / (1.0 + dist));
			heatScore += cell.heat01() * cell.material().flameIntensity * falloff;
			if (stage.hasVisibleFlames()) {
				smokeScore += falloff * 0.8F;
			}
		}

		float protection = gearProtection(player);
		heatScore *= (1.0F - protection * 0.55F) * ForestFireConfig.get().heatDamageMultiplier;
		smokeScore *= (1.0F - protection * 0.65F);
		if (hasBreathingGear(player)) {
			smokeScore *= 0.25F;
			consumeAir(player);
		}

		Level heatLevel = classify(heatScore);
		if (heatLevel.ordinal() >= Level.HOT.ordinal()) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
				"Heat exposure: " + heatLevel.name()
			));
		}
		if (heatLevel == Level.DANGEROUS) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false, true));
			player.hurtServer(player.level(), player.damageSources().onFire(), 0.5F * ForestFireConfig.get().heatDamageMultiplier);
		} else if (heatLevel == Level.CRITICAL) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false, true));
			player.hurtServer(player.level(), player.damageSources().onFire(), 1.5F * ForestFireConfig.get().heatDamageMultiplier);
		}

		if (ForestFireConfig.get().smokeEnabled && smokeScore > 0.8F) {
			if (ForestFireConfig.get().smokeCausesWeakness) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
			}
			if (ForestFireConfig.get().smokeCausesBlindness && smokeScore > 1.4F) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, true));
			}
			if (ForestFireConfig.get().smokeDamagePerSecond > 0 && smokeScore > 1.2F && !hasBreathingGear(player)) {
				player.hurtServer(player.level(), player.damageSources().onFire(), ForestFireConfig.get().smokeDamagePerSecond * 0.5F);
			}
		}
		// Note: hurtServer requires ServerLevel; ServerPlayer.level() is ServerLevel.
	}

	private static Level classify(float score) {
		if (score < 0.15F) {
			return Level.SAFE;
		}
		if (score < 0.4F) {
			return Level.WARM;
		}
		if (score < 0.8F) {
			return Level.HOT;
		}
		if (score < 1.3F) {
			return Level.DANGEROUS;
		}
		return Level.CRITICAL;
	}

	private static float gearProtection(ServerPlayer player) {
		float p = 0.0F;
		if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.FF_HELMET)) {
			p += 0.15F;
		}
		if (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.FF_CHESTPLATE)) {
			p += 0.25F;
		}
		if (player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.FF_LEGGINGS)) {
			p += 0.2F;
		}
		if (player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.FF_BOOTS)) {
			p += 0.1F;
		}
		return Math.min(0.8F, p);
	}

	private static boolean hasBreathingGear(ServerPlayer player) {
		boolean mask = player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BREATHING_MASK)
			|| player.getOffhandItem().is(ModItems.BREATHING_MASK)
			|| player.getMainHandItem().is(ModItems.BREATHING_MASK);
		boolean tank = player.getInventory().hasAnyMatching(s -> s.is(ModItems.AIR_TANK) && AirTankItem.getAir(s) > 0);
		return mask && tank;
	}

	private static void consumeAir(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.AIR_TANK) && AirTankItem.consume(stack, 20)) {
				return;
			}
		}
	}
}
