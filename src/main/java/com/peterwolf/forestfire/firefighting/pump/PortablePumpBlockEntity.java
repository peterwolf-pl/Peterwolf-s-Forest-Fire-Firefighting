package com.peterwolf.forestfire.firefighting.pump;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.firefighting.hose.HoseNetwork;
import com.peterwolf.forestfire.firefighting.water.PortableWaterTankBlockEntity;
import com.peterwolf.forestfire.sound.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PortablePumpBlockEntity extends BlockEntity {
	private PumpState state = PumpState.OFF;
	private int fuel;
	private float pressure;
	private float heat;
	private int intakeWater;
	private int connectedNozzles;
	private int hoseLength;
	private boolean hasIntake;
	private boolean hasStrainer;
	private float efficiency = 1.0F;
	private int startTicks;
	private int debris;

	public PortablePumpBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PORTABLE_PUMP, pos, state);
		this.fuel = ForestFireConfig.get().pumpFuelCapacity;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState blockState, PortablePumpBlockEntity pump) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		pump.tickPump(serverLevel);
	}

	private void tickPump(ServerLevel level) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		HoseNetwork.PumpLinks links = HoseNetwork.scan(level, worldPosition);
		hasIntake = links.hasIntake;
		hoseLength = links.outputLength;
		// Prefer endpoint manager open-nozzle count for realistic multi-line load
		int openNozzles = HoseNetwork.countOpenNozzlesForPump(level, worldPosition);
		connectedNozzles = Math.max(links.activeNozzles, openNozzles);
		intakeWater = links.availableWater;
		hasStrainer = links.hasStrainer;

		float intakeEff = 1.0F;
		var autoIntake = com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager.get(level)
			.scanIntake(level, worldPosition);
		if (autoIntake.hasIntake()) {
			intakeEff = Math.max(0.2F, autoIntake.efficiency());
		}

		if (state == PumpState.DAMAGED || state == PumpState.OFF) {
			pressure = Math.max(0.0F, pressure - 0.05F);
			return;
		}

		if (state == PumpState.STARTING) {
			startTicks++;
			if (startTicks >= 40) {
				state = PumpState.RUNNING;
				level.playSound(null, worldPosition, ModSounds.PUMP_IDLE, SoundSource.BLOCKS, 0.6F, 1.0F);
			}
			return;
		}

		if (level.getGameTime() % 20L == 0L) {
			fuel = Math.max(0, fuel - cfg.pumpFuelPerSecond);
		}
		if (fuel <= 0) {
			state = PumpState.OFF;
			pressure = 0.0F;
			level.playSound(null, worldPosition, ModSounds.PUMP_FAIL, SoundSource.BLOCKS, 0.8F, 0.8F);
			setChanged();
			return;
		}

		if (!hasIntake || intakeWater <= 0) {
			state = PumpState.STARVED;
			pressure = Math.max(0.0F, pressure - 0.08F);
			if (level.getGameTime() % 40L == 0L) {
				level.playSound(null, worldPosition, ModSounds.PUMP_LOW_WATER, SoundSource.BLOCKS, 0.5F, 1.0F);
			}
			return;
		}

		if (!hasStrainer) {
			debris++;
			if (debris > 800) {
				efficiency = Math.max(0.55F, efficiency - 0.0003F);
			}
		} else if (efficiency < 1.0F && level.getGameTime() % 40L == 0L) {
			// Strainer gradually cleans the system
			efficiency = Math.min(1.0F, efficiency + 0.01F);
			debris = Math.max(0, debris - 5);
		}

		// Open nozzles add load, but keep it gentle so continuous spray stays usable
		float load = 1.0F + connectedNozzles * 0.18F + hoseLength * cfg.pressureLossPerSegment * 0.75F;
		float targetPressure = cfg.basePumpPressure * efficiency * intakeEff / Math.max(0.55F, load * 0.55F);
		pressure = pressure * 0.80F + targetPressure * 0.20F;
		// Slow thermal inertia — old values overheated in ~2 seconds of spraying
		heat += load * 0.0015F;
		heat = Math.max(0.0F, heat - 0.004F);

		if (heat > 1.0F) {
			// Stay usable at reduced output; cool while still pumping
			state = PumpState.OVERHEATED;
			pressure = Math.min(pressure, targetPressure * 0.65F);
			heat = Math.max(0.85F, heat - 0.01F);
		} else if (load > 4.0F) {
			state = PumpState.OVERLOADED;
			pressure = Math.min(pressure, targetPressure * 0.85F);
		} else {
			state = PumpState.RUNNING;
		}

		if (links.tankPos != null) {
			BlockEntity be = level.getBlockEntity(links.tankPos);
			if (be instanceof PortableWaterTankBlockEntity tank) {
				tank.drain(Math.max(1, connectedNozzles * 2));
			}
		}

		if (level.getGameTime() % 80L == 0L && state == PumpState.RUNNING) {
			level.playSound(null, worldPosition, ModSounds.PUMP_LOAD, SoundSource.BLOCKS, 0.35F, 1.0F);
		}
		setChanged();
	}

	public void togglePower(ServerPlayer player) {
		if (state == PumpState.DAMAGED) {
			player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_damaged"));
			return;
		}
		if (state == PumpState.OFF || state == PumpState.STARVED || state == PumpState.OVERHEATED) {
			if (fuel <= 0) {
				player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_no_fuel"));
				player.sendSystemMessage(Component.literal("Tip: use Pump Fuel Can on the pump."));
				return;
			}
			// Refresh intake before start so first toggle works after placing intake hose
			if (level instanceof ServerLevel serverLevel) {
				HoseNetwork.PumpLinks links = HoseNetwork.scan(serverLevel, worldPosition);
				hasIntake = links.hasIntake;
				intakeWater = links.availableWater;
				hasStrainer = links.hasStrainer;
			}
			if (!hasIntake || intakeWater <= 0) {
				player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_no_intake"));
				player.sendSystemMessage(Component.literal(
					"Tip: place pump next to water OR connect Intake Hose into water. Optional: Intake Strainer in water."
				));
				// Still allow starting — will go STARVED if no water (helps debug)
			}
			state = PumpState.STARTING;
			startTicks = 0;
			heat = Math.max(0.0F, heat - 0.3F);
			if (level != null) {
				level.playSound(null, worldPosition, ModSounds.PUMP_START, SoundSource.BLOCKS, 0.8F, 1.0F);
			}
			player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_starting"));
			player.sendSystemMessage(Component.literal("§aPump STARTING → RUNNING. Right-click again to turn OFF."));
		} else {
			state = PumpState.OFF;
			pressure = 0.0F;
			player.sendSystemMessage(Component.translatable("message.peterwolfs_forestfire.pump_off"));
		}
		setChanged();
	}

	public void addFuel(int amount) {
		fuel = Math.min(ForestFireConfig.get().pumpFuelCapacity, fuel + amount);
		setChanged();
	}

	public PumpState getPumpState() {
		return state;
	}

	public float getPressure() {
		return pressure;
	}

	public boolean canSupplyNozzle() {
		// OVERHEATED still supplies water (weaker) — do not hard-kill the line mid-attack
		if (fuel <= 0 || pressure <= 0.05F) {
			return false;
		}
		if (state == PumpState.OFF || state == PumpState.DAMAGED || state == PumpState.STARTING) {
			return false;
		}
		if (state == PumpState.STARVED && intakeWater <= 0) {
			return false;
		}
		return true;
	}

	public float consumeForNozzle(float demand) {
		if (!canSupplyNozzle()) {
			return 0.0F;
		}
		float factor = state == PumpState.OVERHEATED ? 0.65F : (state == PumpState.OVERLOADED ? 0.8F : 1.0F);
		float supplied = Math.min(demand, pressure) * factor;
		// Light draw so continuous fog/stream does not empty the pump in seconds
		pressure = Math.max(0.08F, pressure - demand * 0.006F);
		return supplied;
	}

	public List<Component> statusLines() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Portable Fire Pump"));
		lines.add(Component.literal("State: " + state.name()));
		lines.add(Component.literal(String.format(Locale.ROOT, "Pressure: %.2f", pressure)));
		lines.add(Component.literal("Fuel: " + fuel + "/" + ForestFireConfig.get().pumpFuelCapacity));
		lines.add(Component.literal("Intake: " + (hasIntake ? "OK" : "MISSING") + " water=" + intakeWater));
		lines.add(Component.literal("Hose length: " + hoseLength + " | Nozzles: " + connectedNozzles));
		lines.add(Component.literal(String.format(Locale.ROOT, "Efficiency: %.0f%%  Heat: %.0f%%", efficiency * 100.0F, heat * 100.0F)));
		if (!hasStrainer) {
			lines.add(Component.literal("Warning: no intake strainer — debris risk"));
		}
		if (state == PumpState.STARVED) {
			lines.add(Component.literal("Low/no water intake"));
		}
		if (state == PumpState.OVERHEATED) {
			lines.add(Component.literal("Pump overheating"));
		}
		return lines;
	}

	@Override
	protected void saveAdditional(ValueOutput tag) {
		super.saveAdditional(tag);
		tag.putString("state", state.name());
		tag.putInt("fuel", fuel);
		tag.putFloat("pressure", pressure);
		tag.putFloat("heat", heat);
		tag.putFloat("efficiency", efficiency);
		tag.putInt("debris", debris);
		tag.putBoolean("strainer", hasStrainer);
	}

	@Override
	protected void loadAdditional(ValueInput tag) {
		super.loadAdditional(tag);
		try {
			state = PumpState.valueOf(tag.getStringOr("state", "OFF"));
		} catch (Exception exception) {
			state = PumpState.OFF;
		}
		fuel = tag.getIntOr("fuel", ForestFireConfig.get().pumpFuelCapacity);
		pressure = tag.getFloatOr("pressure", 0.0F);
		heat = tag.getFloatOr("heat", 0.0F);
		efficiency = tag.getFloatOr("efficiency", 1.0F);
		debris = tag.getIntOr("debris", 0);
		hasStrainer = tag.getBooleanOr("strainer", false);
	}

	public static int countNearbyWater(Level level, BlockPos origin) {
		int water = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -2; dy <= 1; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (level.getFluidState(cursor).is(Fluids.WATER) || level.getBlockState(cursor).is(Blocks.WATER)) {
						water++;
					}
				}
			}
		}
		return water;
	}
}
