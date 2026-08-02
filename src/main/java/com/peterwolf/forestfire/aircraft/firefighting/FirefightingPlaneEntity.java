package com.peterwolf.forestfire.aircraft.firefighting;

import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank;
import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank.IntakeStatus;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.simulation.water_drop.AerialWaterPayload;
import com.peterwolf.forestfire.simulation.water_drop.WaterDropSimulator;
import com.piotrek.peterwolfsplanes.api.PlaneCargoMass;
import com.piotrek.peterwolfsplanes.api.SpecializedPlaneControls;
import com.piotrek.peterwolfsplanes.entity.LargePlaneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Medium water bomber — full Planes flight via {@link LargePlaneEntity}.
 * Adds tank, scoop hose, and aerial suppression.
 */
public class FirefightingPlaneEntity extends LargePlaneEntity
	implements SpecializedPlaneControls, PlaneCargoMass {

	public static final TagKey<Block> WATER_SOURCES = TagKey.create(
		Registries.BLOCK,
		Identifier.fromNamespaceAndPath("peterwolfs_forestfire", "firefighting_plane_water_sources")
	);

	private static final EntityDataAccessor<Integer> WATER_AMOUNT =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DROP_ARMED =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> RELEASING =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> HOSE_DEPLOYED =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> INTAKE_STATUS =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> HOSE_PROGRESS =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DOOR_PROGRESS =
		SynchedEntityData.defineId(FirefightingPlaneEntity.class, EntityDataSerializers.FLOAT);

	/** Local units for debug overlay / predicted drop. */
	private double lastWaterDistance = Double.NaN;
	private int notOverWaterTicks;
	private int lastSyncedWater = -1;
	private int actionCooldown;
	private boolean debugMode;
	private float clientHoseProgress;
	private float clientDoorProgress;

	/**
	 * Planes is client-authoritative while piloted: server {@code getDeltaMovement()} is often ~0.
	 * Scooping speed is measured from position deltas so server-side fill still works.
	 */
	@Nullable
	private Vec3 previousTickPosition;
	private double measuredHorizontalSpeed;

	public FirefightingPlaneEntity(EntityType<? extends LargePlaneEntity> type, Level world) {
		super(type, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(WATER_AMOUNT, 0);
		builder.define(DROP_ARMED, false);
		builder.define(RELEASING, false);
		builder.define(HOSE_DEPLOYED, false);
		builder.define(INTAKE_STATUS, IntakeStatus.RETRACTED.ordinal());
		builder.define(HOSE_PROGRESS, 0.0F);
		builder.define(DOOR_PROGRESS, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setWaterAmount(input.getIntOr("WaterAmount", 0));
		this.setDropArmed(input.getBooleanOr("DropArmed", false));
		this.setHoseDeployed(input.getBooleanOr("HoseDeployed", false));
		// Releasing is never persisted as held state
		this.setReleasing(false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("WaterAmount", this.getWaterAmount());
		output.putBoolean("DropArmed", this.isDropArmed());
		output.putBoolean("HoseDeployed", this.isHoseDeployed());
	}

	@Override
	public boolean usesCombatControls() {
		return false;
	}

	/**
	 * Water bomber is not a combat airframe — ignore dogfight weapon packets.
	 */
	@Override
	public void applyCombatInput(boolean combatMode, boolean fireGuns, boolean dropBomb) {
		// no-op
	}

	@Override
	public float getCargoMassFactor() {
		return AircraftWaterTank.cargoMassFactor(this.getWaterAmount());
	}

	@Override
	public Item getDropItem() {
		return FirefightingPlaneRegistry.ITEM;
	}

	// --- State accessors ---

	public int getWaterAmount() {
		return this.entityData.get(WATER_AMOUNT);
	}

	public void setWaterAmount(int amount) {
		int clamped = AircraftWaterTank.clampAmount(amount);
		if (clamped != this.entityData.get(WATER_AMOUNT)) {
			this.entityData.set(WATER_AMOUNT, clamped);
		}
	}

	public boolean isDropArmed() {
		return this.entityData.get(DROP_ARMED);
	}

	public void setDropArmed(boolean armed) {
		if (armed != this.entityData.get(DROP_ARMED)) {
			this.entityData.set(DROP_ARMED, armed);
		}
	}

	public boolean isReleasing() {
		return this.entityData.get(RELEASING);
	}

	public void setReleasing(boolean releasing) {
		if (releasing != this.entityData.get(RELEASING)) {
			this.entityData.set(RELEASING, releasing);
		}
	}

	public boolean isHoseDeployed() {
		return this.entityData.get(HOSE_DEPLOYED);
	}

	public void setHoseDeployed(boolean deployed) {
		if (deployed != this.entityData.get(HOSE_DEPLOYED)) {
			this.entityData.set(HOSE_DEPLOYED, deployed);
		}
	}

	public IntakeStatus getIntakeStatus() {
		int ord = this.entityData.get(INTAKE_STATUS);
		IntakeStatus[] values = IntakeStatus.values();
		if (ord < 0 || ord >= values.length) {
			return IntakeStatus.IDLE;
		}
		return values[ord];
	}

	public void setIntakeStatus(IntakeStatus status) {
		int ord = status.ordinal();
		if (ord != this.entityData.get(INTAKE_STATUS)) {
			this.entityData.set(INTAKE_STATUS, ord);
		}
	}

	public float getHoseProgress() {
		return this.entityData.get(HOSE_PROGRESS);
	}

	public float getHoseProgress(float partialTick) {
		return Mth.lerp(partialTick, this.clientHoseProgress, this.getHoseProgress());
	}

	public float getDoorProgress() {
		return this.entityData.get(DOOR_PROGRESS);
	}

	public float getDoorProgress(float partialTick) {
		return Mth.lerp(partialTick, this.clientDoorProgress, this.getDoorProgress());
	}

	public double getLastWaterDistance() {
		return this.lastWaterDistance;
	}

	public boolean isDebugMode() {
		return this.debugMode;
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}

	// --- Actions (server) ---

	public void toggleDropArmed(@Nullable Player pilot) {
		if (!this.canPilotControl(pilot)) {
			return;
		}
		boolean next = !this.isDropArmed();
		this.setDropArmed(next);
		if (!next) {
			this.setReleasing(false);
		}
		if (pilot != null) {
			pilot.sendOverlayMessage(Component.translatable(
				next
					? "message.peterwolfs_forestfire.water_drop_armed"
					: "message.peterwolfs_forestfire.water_drop_off"
			));
		}
		if (!this.level().isClientSide()) {
			this.level().playSound(
				null, this.getX(), this.getY(), this.getZ(),
				next ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
				SoundSource.PLAYERS, 0.65F, next ? 1.5F : 0.7F
			);
		}
	}

	public void setReleaseActive(boolean active, @Nullable Player pilot) {
		if (!this.canPilotControl(pilot)) {
			return;
		}
		if (active) {
			if (!this.isDropArmed() || this.getWaterAmount() <= 0 || this.isRemoved()) {
				this.setReleasing(false);
				return;
			}
			this.setReleasing(true);
		} else {
			this.setReleasing(false);
		}
	}

	public void toggleHose(@Nullable Player pilot) {
		if (!this.canPilotControl(pilot)) {
			return;
		}
		boolean next = !this.isHoseDeployed();
		this.setHoseDeployed(next);
		if (pilot != null) {
			pilot.sendOverlayMessage(Component.translatable(
				next
					? "message.peterwolfs_forestfire.intake_hose_deployed"
					: "message.peterwolfs_forestfire.intake_hose_retracted"
			));
		}
		if (!this.level().isClientSide()) {
			this.level().playSound(
				null, this.getX(), this.getY(), this.getZ(),
				next ? SoundEvents.FISHING_BOBBER_THROW : SoundEvents.FISHING_BOBBER_RETRIEVE,
				SoundSource.PLAYERS, 0.8F, next ? 0.9F : 1.1F
			);
		}
	}

	public void forceHose(boolean deployed) {
		this.setHoseDeployed(deployed);
	}

	public void forceDropArmed(boolean armed) {
		this.setDropArmed(armed);
		if (!armed) {
			this.setReleasing(false);
		}
	}

	private boolean canPilotControl(@Nullable Player pilot) {
		if (this.level().isClientSide()) {
			return false;
		}
		if (pilot == null) {
			return false;
		}
		return this.getFirstPassenger() == pilot && !this.isRemoved();
	}

	// --- Tick ---

	@Override
	public void tick() {
		this.clientHoseProgress = this.getHoseProgress();
		this.clientDoorProgress = this.getDoorProgress();
		super.tick();
		this.updateMeasuredSpeed();

		if (this.actionCooldown > 0) {
			this.actionCooldown--;
		}

		// Animate hose / doors toward target states
		float hoseTarget = this.isHoseDeployed() ? 1.0F : 0.0F;
		float doorTarget = (this.isDropArmed() && this.isReleasing()) ? 1.0F : 0.0F;
		float hose = Mth.approach(this.getHoseProgress(), hoseTarget, 0.08F);
		float door = Mth.approach(this.getDoorProgress(), doorTarget, 0.12F);
		if (Math.abs(hose - this.entityData.get(HOSE_PROGRESS)) > 1.0E-3F) {
			this.entityData.set(HOSE_PROGRESS, hose);
		}
		if (Math.abs(door - this.entityData.get(DOOR_PROGRESS)) > 1.0E-3F) {
			this.entityData.set(DOOR_PROGRESS, door);
		}

		if (this.level().isClientSide()) {
			this.tickClientEffects();
			return;
		}

		ServerLevel server = (ServerLevel) this.level();
		this.tickAutoRetract(server);
		this.tickIntake(server);
		this.tickRelease(server);

		// Sync metrics occasionally
		int water = this.getWaterAmount();
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		if (this.lastSyncedWater < 0
			|| Math.abs(water - this.lastSyncedWater) >= cfg.waterSyncThreshold
			|| water == 0
			|| water == AircraftWaterTank.capacity()) {
			this.lastSyncedWater = water;
			WaterDropSimulator.get(server).recordTankSync();
		}
	}

	private void tickClientEffects() {
		if (this.isReleasing() && this.isDropArmed() && this.getWaterAmount() > 0 && this.getDoorProgress() > 0.4F) {
			Vec3 drop = this.getDropBayPosition();
			for (int i = 0; i < 3; i++) {
				this.level().addParticle(
					ParticleTypes.FALLING_WATER,
					drop.x + (this.random.nextDouble() - 0.5) * 0.8,
					drop.y,
					drop.z + (this.random.nextDouble() - 0.5) * 0.8,
					0.0, -0.35, 0.0
				);
			}
		}
		if (this.getIntakeStatus() == IntakeStatus.FILLING && this.getHoseProgress() > 0.7F) {
			Vec3 nozzle = this.getHoseNozzlePosition(1.0F);
			this.level().addParticle(
				ParticleTypes.BUBBLE,
				nozzle.x, nozzle.y, nozzle.z,
				0.0, 0.05, 0.0
			);
		}
	}

	private void tickAutoRetract(ServerLevel server) {
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		if (!this.isHoseDeployed()) {
			return;
		}
		// Require both onGround and nearly stopped — sticky onGround near water must not yank the hose.
		boolean land = this.onGround() && this.scoopingSpeed() < 0.08D;
		boolean noPilot = !(this.getFirstPassenger() instanceof Player);
		double speed = this.scoopingSpeed();
		boolean tooFast = cfg.autoRetractHoseAtUnsafeSpeed && speed > cfg.autoRetractSpeed;
		if (land || noPilot || tooFast || this.isRemoved()) {
			this.setHoseDeployed(false);
			if (tooFast && this.getFirstPassenger() instanceof Player pilot) {
				pilot.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_auto_retract_speed"));
			}
		}
	}

	private void tickIntake(ServerLevel server) {
		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		if (!cfg.enabled) {
			this.setIntakeStatus(IntakeStatus.IDLE);
			return;
		}

		if (!this.isHoseDeployed() || this.getHoseProgress() < 0.85F) {
			this.setIntakeStatus(IntakeStatus.RETRACTED);
			this.lastWaterDistance = Double.NaN;
			return;
		}

		// Only treat as landed when stopped on solid ground (not low-altitude scooping).
		if (this.onGround() && this.scoopingSpeed() < 0.08D) {
			this.setIntakeStatus(IntakeStatus.NOT_AIRBORNE);
			return;
		}

		if (this.getWaterAmount() >= AircraftWaterTank.capacity()) {
			this.setIntakeStatus(IntakeStatus.TANK_FULL);
			return;
		}

		double speed = this.scoopingSpeed();
		if (speed < 0.05D) {
			this.setIntakeStatus(IntakeStatus.NO_FORWARD_SPEED);
			return;
		}
		if (speed < cfg.minimumScoopingSpeed) {
			this.setIntakeStatus(IntakeStatus.TOO_SLOW);
			return;
		}
		if (speed > cfg.maximumScoopingSpeed) {
			this.setIntakeStatus(IntakeStatus.TOO_FAST);
			return;
		}

		float roll = Math.abs(this.getRoll());
		float pitch = Math.abs(this.getXRot());
		if (roll > cfg.maximumScoopingRollDegrees) {
			this.setIntakeStatus(IntakeStatus.EXCESSIVE_BANK);
			return;
		}
		if (pitch > cfg.maximumScoopingPitchDegrees) {
			this.setIntakeStatus(IntakeStatus.EXCESSIVE_PITCH);
			return;
		}

		WaterDropSimulator.get(server).recordCollectionCheck();
		// Search far enough to find the surface; validity uses in-water rules below.
		WaterProbe probe = this.probeWaterBelowNozzle(Math.max(8.0D, cfg.maximumNozzleSubmersionBlocks + 4.0D));
		this.lastWaterDistance = probe.distance;
		// distance = nozzleY - surfaceY: negative = submerged, positive = above water.
		// Scoop only when the nozzle is in/at the water — not several blocks above.
		boolean nozzleInWater = probe.valid
			&& probe.distance <= cfg.maximumWaterDistanceBlocks
			&& probe.distance >= -cfg.maximumNozzleSubmersionBlocks;
		if (!nozzleInWater) {
			this.notOverWaterTicks++;
			this.setIntakeStatus(IntakeStatus.WATER_OUT_OF_RANGE);
			return;
		}

		this.notOverWaterTicks = 0;
		this.setIntakeStatus(IntakeStatus.FILLING);
		int before = this.getWaterAmount();
		this.setWaterAmount(AircraftWaterTank.add(before, AircraftWaterTank.intakeRate()));
		if (this.getWaterAmount() >= AircraftWaterTank.capacity() && before < AircraftWaterTank.capacity()) {
			if (this.getFirstPassenger() instanceof Player pilot) {
				pilot.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.tank_full"));
			}
			server.playSound(
				null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.9F, 1.2F
			);
		} else if (this.tickCount % 12 == 0) {
			server.playSound(
				null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.25F, 1.4F
			);
		}
	}

	private void tickRelease(ServerLevel server) {
		if (!this.isReleasing() || !this.isDropArmed()) {
			return;
		}
		if (this.getWaterAmount() <= 0) {
			this.setReleasing(false);
			if (this.getFirstPassenger() instanceof Player pilot) {
				pilot.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.tank_empty"));
			}
			return;
		}
		if (this.getDoorProgress() < 0.35F) {
			return;
		}

		ForestFireConfig.Data.FirefightingAircraft cfg = ForestFireConfig.get().firefightingAircraft;
		int drain = Math.min(AircraftWaterTank.releaseRate(), this.getWaterAmount());
		this.setWaterAmount(this.getWaterAmount() - drain);

		Vec3 bay = this.getDropBayPosition();
		Vec3 planeVel = this.getDeltaMovement();
		// Fall slightly behind and below
		Vec3 releaseVel = planeVel.scale(0.9D).add(0.0D, -0.12D, 0.0D);
		double altitude = Math.max(0.0D, this.getY() - server.getHeight(
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
			this.blockPosition().getX(),
			this.blockPosition().getZ()
		));
		float radius = cfg.dropBaseRadius + (float) altitude * cfg.dropRadiusPerAltitude;
		radius = Mth.clamp(radius, 2.0F, 14.0F);

		AerialWaterPayload payload = new AerialWaterPayload(
			bay, releaseVel, drain, cfg.dropStrength, radius
		);
		WaterDropSimulator.get(server).spawn(payload);

		if (this.tickCount % 8 == 0) {
			server.playSound(
				null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.35F, 0.85F
			);
		}
	}

	private void updateMeasuredSpeed() {
		Vec3 pos = this.position();
		if (this.previousTickPosition != null) {
			// Smooth slightly so single-tick network teleport spikes do not flip scoop states.
			double sample = pos.subtract(this.previousTickPosition).horizontalDistance();
			this.measuredHorizontalSpeed = this.measuredHorizontalSpeed * 0.35D + sample * 0.65D;
		}
		this.previousTickPosition = pos;
	}

	/**
	 * Best-effort horizontal speed (blocks/tick) for scooping gates.
	 * Prefer live velocity when present; fall back to measured position delta (server + client-auth planes).
	 */
	public double scoopingSpeed() {
		double fromVelocity = this.getDeltaMovement().horizontalDistance();
		return Math.max(fromVelocity, this.measuredHorizontalSpeed);
	}

	/** Public wrapper — heading is private on base; recompute locally. */
	private Vec3 calculateHeadingPublic(float yaw, float pitch) {
		float yawRad = (float) Math.toRadians(yaw);
		float pitchRad = (float) Math.toRadians(pitch);
		return new Vec3(
			-Math.sin(yawRad) * Math.cos(pitchRad),
			-Math.sin(pitchRad),
			Math.cos(yawRad) * Math.cos(pitchRad)
		);
	}

	/**
	 * Hose nozzle world position. Extended below fuselage when progress = 1.
	 */
	public Vec3 getHoseNozzlePosition(float partialTick) {
		float progress = this.getHoseProgress(partialTick);
		Vec3 heading = this.calculateHeadingPublic(this.getYRot(), this.getXRot());
		// Slight trail behind when moving
		Vec3 vel = this.getDeltaMovement();
		Vec3 trail = vel.lengthSqr() > 1.0E-4D ? vel.normalize().scale(-0.2D * progress) : Vec3.ZERO;
		// Long probe — must be able to reach into water on a low pass
		double extend = 0.55D + 3.2D * progress;
		return this.position()
			.add(heading.scale(0.15D))
			.add(0.0D, -extend, 0.0D)
			.add(trail);
	}

	public Vec3 getDropBayPosition() {
		Vec3 heading = this.calculateHeadingPublic(this.getYRot(), this.getXRot());
		return this.position().add(heading.scale(-0.4D)).add(0.0D, -0.65D, 0.0D);
	}

	/**
	 * Downward raycast from nozzle for water surface within maxDistance.
	 */
	public WaterProbe probeWaterBelowNozzle(double maxDistance) {
		return this.probeWaterBelow(this.getHoseNozzlePosition(1.0F), maxDistance);
	}

	/**
	 * Altitude of the airframe above the water surface (blocks), for HUD.
	 * Works client- and server-side. Returns NaN when no water is found below.
	 */
	public double getAltitudeAboveWater() {
		WaterProbe probe = this.probeWaterBelow(this.position(), 96.0D);
		if (!probe.valid || Double.isNaN(probe.surfaceY())) {
			return Double.NaN;
		}
		return Math.max(0.0D, this.getY() - probe.surfaceY());
	}

	/**
	 * Distance from the deployed hose nozzle to the water surface (blocks).
	 * NaN if hose is stowed or no water in range of a long search.
	 */
	/**
	 * Signed height of nozzle above water surface (blocks). Negative = submerged / in water.
	 */
	public double getNozzleAltitudeAboveWater() {
		if (this.getHoseProgress() < 0.15F) {
			return Double.NaN;
		}
		Vec3 nozzle = this.getHoseNozzlePosition(1.0F);
		WaterProbe probe = this.probeWaterBelow(nozzle, 48.0D);
		if (!probe.valid || Double.isNaN(probe.surfaceY())) {
			return Double.NaN;
		}
		return nozzle.y - probe.surfaceY();
	}

	private WaterProbe probeWaterBelow(Vec3 from, double maxDistance) {
		Vec3 end = from.add(0.0D, -maxDistance - 0.5D, 0.0D);
		BlockHitResult hit = this.level().clip(new ClipContext(
			from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this
		));

		if (hit.getType() == HitResult.Type.MISS) {
			return WaterProbe.invalid(Double.POSITIVE_INFINITY);
		}

		BlockPos hitPos = hit.getBlockPos();
		FluidState fluid = this.level().getFluidState(hitPos);
		boolean water = fluid.is(FluidTags.WATER)
			|| this.level().getBlockState(hitPos).is(Blocks.WATER)
			|| this.level().getBlockState(hitPos).is(WATER_SOURCES);

		// Also accept water one block below solid if we hit a lily / surface
		if (!water) {
			BlockPos below = hitPos.below();
			FluidState belowFluid = this.level().getFluidState(below);
			if (belowFluid.is(FluidTags.WATER) || this.level().getBlockState(below).is(WATER_SOURCES)) {
				water = true;
				hitPos = below;
				fluid = belowFluid;
			}
		}

		double surfaceY = hitPos.getY() + (water ? fluid.getHeight(this.level(), hitPos) : 0.0D);
		if (water && fluid.isEmpty()) {
			surfaceY = hitPos.getY() + 1.0D;
		}
		double distance = from.y - surfaceY;
		// For scoop validity the caller still enforces max scoop range; HUD uses long searches.
		if (!water) {
			return WaterProbe.invalid(distance);
		}
		return new WaterProbe(true, distance, hitPos, surfaceY);
	}

	public record WaterProbe(boolean valid, double distance, @Nullable BlockPos waterPos, double surfaceY) {
		static WaterProbe invalid(double distance) {
			return new WaterProbe(false, distance, null, Double.NaN);
		}
	}
}
