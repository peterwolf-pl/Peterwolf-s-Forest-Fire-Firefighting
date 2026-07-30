package com.peterwolf.forestfire.client.hud;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.WindSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Compact firefighter HUD: incident status, wind, nozzle pressure.
 */
public final class FirefighterHud {
	private static IncidentHudPayload incident;
	private static WindSyncPayload wind;
	private static boolean showDedicationSplash = true;
	private static long dedicationUntilMs;

	private FirefighterHud() {
	}

	public static void updateIncident(IncidentHudPayload payload) {
		incident = payload;
	}

	public static void updateWind(WindSyncPayload payload) {
		wind = payload;
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int y = 8;
		int x = 8;
		if (showDedicationSplash) {
			if (dedicationUntilMs == 0L) {
				dedicationUntilMs = System.currentTimeMillis() + 8000L;
			}
			if (System.currentTimeMillis() < dedicationUntilMs) {
				graphics.text(mc.font, Component.literal(ForestFireMod.DEDICATION), x, y, 0xFFFFCC66, true);
				y += 12;
			} else {
				showDedicationSplash = false;
			}
		}
		if (incident != null && incident.incidentId() > 0) {
			graphics.text(mc.font, Component.literal(
				"#" + incident.incidentId() + " " + incident.name() + " [" + incident.status() + "]"
			), x, y, 0xFFFFAA33, true);
			y += 10;
			graphics.text(mc.font, Component.literal(
				"Burning: " + incident.burningBlocks()
					+ "  Contain: " + String.format("%.0f%%", incident.containment())
			), x, y, 0xFFEEEEEE, true);
			y += 10;
			if (!incident.nozzleMode().isEmpty()) {
				graphics.text(mc.font, Component.literal(
					"Nozzle: " + incident.nozzleMode() + "  P=" + String.format("%.2f", incident.pressure())
				), x, y, 0xFF66CCFF, true);
				y += 10;
			}
		}
		if (wind != null) {
			graphics.text(mc.font, Component.literal(
				String.format("Wind: %.2f @ %.0f°", wind.speed(), wind.yawDegrees())
			), x, y, 0xFFAACCEE, true);
		}
	}
}
