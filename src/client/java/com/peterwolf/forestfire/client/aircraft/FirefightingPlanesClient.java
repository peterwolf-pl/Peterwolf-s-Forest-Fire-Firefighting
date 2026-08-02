package com.peterwolf.forestfire.client.aircraft;

import com.mojang.blaze3d.platform.InputConstants;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneEntity;
import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneRegistry;
import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank;
import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank.IntakeStatus;
import com.peterwolf.forestfire.network.firefighting.FireplaneActionPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-only Planes firefighting UI, keys, and renderer.
 */
public final class FirefightingPlanesClient {
	public static final ModelLayerLocation FIREFIGHTING_PLANE_LAYER = new ModelLayerLocation(
		ForestFireMod.id("firefighting_plane"), "main"
	);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		ForestFireMod.id("firefighting_plane")
	);

	public static final KeyMapping TOGGLE_DROP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.peterwolfs_forestfire.toggle_water_drop",
		InputConstants.Type.KEYSYM,
		InputConstants.KEY_V,
		CATEGORY
	));
	public static final KeyMapping RELEASE_WATER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.peterwolfs_forestfire.release_water",
		InputConstants.Type.KEYSYM,
		InputConstants.KEY_B,
		CATEGORY
	));
	public static final KeyMapping TOGGLE_HOSE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.peterwolfs_forestfire.toggle_intake_hose",
		InputConstants.Type.KEYSYM,
		InputConstants.KEY_H,
		CATEGORY
	));

	private static boolean lastReleaseDown;
	/** Edge-guard so a single physical key press cannot toggle hose twice in one tick. */
	private static int hoseToggleCooldown;

	private FirefightingPlanesClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(FirefightingPlaneRegistry.ENTITY, FirefightingPlaneRenderer::new);
		ModelLayerRegistry.registerModelLayer(FIREFIGHTING_PLANE_LAYER, FirefightingPlaneModel::createLayerDefinition);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !(client.player.getVehicle() instanceof FirefightingPlaneEntity plane)) {
				lastReleaseDown = false;
				hoseToggleCooldown = 0;
				return;
			}

			if (hoseToggleCooldown > 0) {
				hoseToggleCooldown--;
			}

			while (TOGGLE_DROP.consumeClick()) {
				ClientPlayNetworking.send(new FireplaneActionPayload(FireplaneActionPayload.TOGGLE_DROP_ARMED, true));
			}
			// Aircraft scoop hose only (not ground automatic hose). One packet per press.
			boolean hoseClicked = false;
			while (TOGGLE_HOSE.consumeClick()) {
				hoseClicked = true;
			}
			if (hoseClicked && hoseToggleCooldown <= 0) {
				hoseToggleCooldown = 4;
				ClientPlayNetworking.send(new FireplaneActionPayload(FireplaneActionPayload.TOGGLE_HOSE, true));
			}

			boolean releaseDown = RELEASE_WATER.isDown();
			if (releaseDown != lastReleaseDown) {
				ClientPlayNetworking.send(new FireplaneActionPayload(FireplaneActionPayload.SET_RELEASING, releaseDown));
				lastReleaseDown = releaseDown;
			}
			// Local prediction for doors / HUD
			if (releaseDown && plane.isDropArmed() && plane.getWaterAmount() > 0) {
				plane.setReleasing(true);
			} else if (!releaseDown) {
				plane.setReleasing(false);
			}
		});

		HudElementRegistry.addLast(ForestFireMod.id("firefighting_plane_hud"), FirefightingPlanesClient::renderHud);
		ForestFireMod.LOGGER.info("Firefighting plane client registered.");
	}

	private static void renderHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(client.player.getVehicle() instanceof FirefightingPlaneEntity plane)) {
			return;
		}
		Font font = client.font;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int boxWidth = 186;
		int boxHeight = 92;
		int boxX = (screenWidth - boxWidth) / 2;
		int boxY = (screenHeight - boxHeight) / 2 + (int) (screenHeight * 0.18) + 62;

		graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x99002040);
		graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF3399CC);
		graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF3399CC);

		String water = AircraftWaterTank.formatHud(plane.getWaterAmount());
		String drop = plane.isDropArmed() ? "DROP SYSTEM: ARMED" : "DROP SYSTEM: OFF";
		String hose = plane.isHoseDeployed() ? "INTAKE HOSE: DEPLOYED" : "INTAKE HOSE: RETRACTED";
		String intake = intakeLabel(plane.getIntakeStatus());
		int dropColor = plane.isDropArmed() ? 0xFF55FF55 : 0xFFAAAAAA;
		int intakeColor = plane.getIntakeStatus() == IntakeStatus.FILLING ? 0xFF55FFFF : 0xFFCCCCCC;
		if (plane.getWaterAmount() <= 0) {
			intakeColor = 0xFFFF5555;
		}

		// Height above water (plane + nozzle when hose is out)
		double waterAgl = plane.getAltitudeAboveWater();
		String waterAlt;
		int waterAltColor = 0xFF88CCFF;
		if (Double.isNaN(waterAgl)) {
			waterAlt = "WATER AGL: —";
			waterAltColor = 0xFF888888;
		} else {
			waterAlt = String.format("WATER AGL: %.1f m", waterAgl);
			if (waterAgl <= 3.0D) {
				waterAltColor = 0xFF55FF55; // in scoop band for airframe-level cue
			} else if (waterAgl <= 8.0D) {
				waterAltColor = 0xFFFFCC55;
			} else {
				waterAltColor = 0xFFFF8855;
			}
		}
		double nozzleAgl = plane.getNozzleAltitudeAboveWater();
		if (!Double.isNaN(nozzleAgl) && plane.isHoseDeployed()) {
			waterAlt = String.format("WATER AGL: %.1f m  NOZZLE: %.1f m", waterAgl, nozzleAgl);
			if (nozzleAgl <= 3.0D) {
				waterAltColor = 0xFF55FFFF;
			}
		}

		graphics.text(font, water, boxX + 8, boxY + 6, 0xFFFFFFFF, false);
		graphics.text(font, waterAlt, boxX + 8, boxY + 18, waterAltColor, false);
		graphics.text(font, drop, boxX + 8, boxY + 30, dropColor, false);
		graphics.text(font, hose, boxX + 8, boxY + 42, 0xFFCCEEFF, false);
		graphics.text(font, intake, boxX + 8, boxY + 54, intakeColor, false);

		String warn = warningLine(plane);
		if (warn != null) {
			graphics.text(font, warn, boxX + 8, boxY + 66, 0xFFFFAA00, false);
		} else {
			graphics.text(font, "V arm  B drop  H hose", boxX + 8, boxY + 66, 0xFF8899AA, false);
		}

		if (plane.isDebugMode()) {
			String dbg = String.format("dbg hose=%.2f door=%.2f dist=%.2f",
				plane.getHoseProgress(), plane.getDoorProgress(), plane.getLastWaterDistance());
			graphics.text(font, dbg, boxX + 8, boxY + 78, 0xFFFFFF55, false);
		}
	}

	private static String intakeLabel(IntakeStatus status) {
		return switch (status) {
			case FILLING -> "INTAKE: FILLING";
			case TOO_SLOW -> "INTAKE: TOO SLOW";
			case TOO_FAST -> "INTAKE: TOO FAST";
			case WATER_OUT_OF_RANGE -> "INTAKE: WATER OUT OF RANGE";
			case EXCESSIVE_BANK -> "INTAKE: EXCESSIVE BANK ANGLE";
			case EXCESSIVE_PITCH -> "INTAKE: EXCESSIVE PITCH";
			case TANK_FULL -> "INTAKE: TANK FULL";
			case RETRACTED -> "INTAKE: HOSE RETRACTED";
			case NOT_AIRBORNE -> "INTAKE: NOT AIRBORNE";
			case NO_FORWARD_SPEED -> "INTAKE: NO FORWARD SPEED";
			case IDLE -> "INTAKE: IDLE";
		};
	}

	private static String warningLine(FirefightingPlaneEntity plane) {
		if (plane.getWaterAmount() <= 0) {
			return "TANK EMPTY";
		}
		if (plane.getWaterAmount() >= AircraftWaterTank.capacity()) {
			return "TANK FULL";
		}
		if (!plane.isDropArmed()) {
			return "DROP SYSTEM OFF";
		}
		return switch (plane.getIntakeStatus()) {
			case WATER_OUT_OF_RANGE -> "TOO HIGH ABOVE WATER";
			case TOO_SLOW -> "SPEED TOO LOW";
			case TOO_FAST -> "SPEED TOO HIGH";
			case EXCESSIVE_BANK -> "EXCESSIVE BANK ANGLE";
			default -> null;
		};
	}
}
