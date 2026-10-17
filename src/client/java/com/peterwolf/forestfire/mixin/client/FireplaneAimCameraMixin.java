package com.peterwolf.forestfire.mixin.client;

import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneEntity;
import com.peterwolf.forestfire.client.aircraft.FirefightingPlanesClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Top-down aiming camera while the water drop system is armed (V).
 * Looks straight down over the bomber so the pilot can line up the dump corridor.
 */
@Mixin(Camera.class)
public abstract class FireplaneAimCameraMixin {
	/** Height of the aiming camera above the aircraft (blocks). */
	private static final double AIM_HEIGHT = 36.0D;

	@Shadow
	private boolean detached;

	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void forestfire$applyDropAimCamera(float partialTicks, CallbackInfo ci) {
		if (!FirefightingPlanesClient.isDropAimCameraActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || this.entity == null) {
			return;
		}
		if (!(client.player.getVehicle() instanceof FirefightingPlaneEntity plane)) {
			FirefightingPlanesClient.setDropAimCameraActive(false);
			return;
		}
		// Only while drop system is armed
		if (!plane.isDropArmed()) {
			FirefightingPlanesClient.setDropAimCameraActive(false);
			return;
		}

		double x = Mth.lerp(partialTicks, plane.xo, plane.getX());
		double y = Mth.lerp(partialTicks, plane.yo, plane.getY());
		double z = Mth.lerp(partialTicks, plane.zo, plane.getZ());

		// Lead slightly along heading so the view centers near the drop footprint
		float yaw = plane.getYRot();
		double yawRad = Math.toRadians(yaw);
		Vec3 vel = plane.getDeltaMovement();
		double lead = Mth.clamp(vel.horizontalDistance() * 8.0D, 0.0D, 6.0D);
		x += -Math.sin(yawRad) * lead;
		z += Math.cos(yawRad) * lead;

		// Straight down; yaw aligned with aircraft so forward matches flight path
		this.setRotation(yaw, 90.0F);
		this.setPosition(x, y + AIM_HEIGHT, z);
		this.detached = true;
	}
}
