package com.peterwolf.forestfire.client.aircraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneEntity;
import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank;
import com.piotrek.peterwolfsplanes.entity.PlaneEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class FirefightingPlaneRenderer extends EntityRenderer<PlaneEntity, FirefightingPlaneRenderState> {
	private static final Identifier TEXTURE = ForestFireMod.id("textures/entity/firefighting_plane.png");
	private final FirefightingPlaneModel model;

	public FirefightingPlaneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.0F;
		this.model = new FirefightingPlaneModel(
			context.bakeLayer(FirefightingPlanesClient.FIREFIGHTING_PLANE_LAYER)
		);
	}

	@Override
	public FirefightingPlaneRenderState createRenderState() {
		return new FirefightingPlaneRenderState();
	}

	@Override
	public void extractRenderState(PlaneEntity entity, FirefightingPlaneRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.pitch = entity.getVisualPitch(partialTick);
		state.yRot = entity.getRenderYaw(partialTick);
		state.roll = entity.getRoll(partialTick);
		state.propellerAngle = entity.getPropellerAngle(partialTick);
		state.speed = (float) entity.getInstrumentSpeedMetersPerSecond();
		state.altitude = (float) state.y;
		if (entity instanceof FirefightingPlaneEntity plane) {
			state.hoseProgress = plane.getHoseProgress(partialTick);
			state.doorProgress = plane.getDoorProgress(partialTick);
			state.tankFill = AircraftWaterTank.fillRatio(plane.getWaterAmount());
			state.releasing = plane.isReleasing();
			double agl = plane.getAltitudeAboveWater();
			state.waterAgl = Double.isNaN(agl) ? Float.NaN : (float) agl;
		}
	}

	@Override
	public void submit(FirefightingPlaneRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
		super.submit(state, poseStack, collector, cameraState);
		poseStack.pushPose();
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.yRot));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.5F, 0.0F);
		this.model.setupAnim(state);
		collector.submitModel(this.model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();
	}
}
