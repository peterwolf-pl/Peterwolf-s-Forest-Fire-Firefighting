package com.peterwolf.forestfire.client.hose;

import com.peterwolf.forestfire.config.ForestFireConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Renders automatic hose paths as continuous lines via Minecraft gizmo primitives.
 * One logical connection = one polyline (no block entities).
 */
public final class HoseRenderer {
	private HoseRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(HoseRenderer::beforeGizmos);
	}

	private static void beforeGizmos(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		var hoses = ClientHoseState.all();
		if (hoses.isEmpty()) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || !camera.initialized) {
			return;
		}

		double renderDist = ForestFireConfig.get().hoseRenderDistance;
		double renderDistSq = renderDist * renderDist;
		Vec3 camPos = camera.pos;

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		boolean any = false;

		for (ClientHoseState.HoseRenderData hose : hoses) {
			if (hose.points() == null || hose.points().size() < 2) {
				continue;
			}
			Vec3 mid = hose.points().get(hose.points().size() / 2);
			if (camPos.distanceToSqr(mid) > renderDistSq) {
				continue;
			}
			int colour = colourFor(hose);
			float width = hose.pressurized() ? 3.5F : 2.5F;
			var pts = hose.points();
			for (int i = 0; i < pts.size() - 1; i++) {
				Vec3 a = pts.get(i);
				Vec3 b = pts.get(i + 1);
				gizmos.addLine(a, b, colour, width);
				// slight parallel line for thickness
				gizmos.addLine(a.add(0, 0.03, 0), b.add(0, 0.03, 0), darken(colour), width * 0.75F);
			}
			any = true;
		}

		if (any) {
			// Gizmos use world-space endpoints; camera state handles projection
			gizmos.submit(context.submitNodeCollector(), camera, false);
		}
	}

	private static int colourFor(ClientHoseState.HoseRenderData hose) {
		// ARGB
		if ("INTAKE".equals(hose.type())) {
			return hose.pressurized() ? 0xFF3A8CFF : 0xFF2A5A9A;
		}
		if ("SUPPLY".equals(hose.type()) || "SPLITTER_BRANCH".equals(hose.type())) {
			return hose.pressurized() ? 0xFFFFAA33 : 0xFFAA7722;
		}
		return hose.pressurized() ? 0xFFE02020 : 0xFF8B1515;
	}

	private static int darken(int argb) {
		int a = (argb >> 24) & 0xFF;
		int r = (int) (((argb >> 16) & 0xFF) * 0.75);
		int g = (int) (((argb >> 8) & 0xFF) * 0.75);
		int b = (int) ((argb & 0xFF) * 0.75);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
