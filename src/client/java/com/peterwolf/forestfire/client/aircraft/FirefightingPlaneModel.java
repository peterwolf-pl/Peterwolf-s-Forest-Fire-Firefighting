package com.peterwolf.forestfire.client.aircraft;

import com.piotrek.peterwolfsplanes.client.LargePlaneModel;
import com.piotrek.peterwolfsplanes.client.PlaneRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * Reuses the large biplane airframe. Hose / door motion is primarily particle-driven;
 * render-state fields are kept for future mesh parts.
 */
public class FirefightingPlaneModel extends LargePlaneModel {
	public FirefightingPlaneModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayerDefinition() {
		return LargePlaneModel.createLayerDefinition();
	}

	@Override
	public void setupAnim(PlaneRenderState state) {
		super.setupAnim(state);
		// Custom mesh parts (tank doors / hose) can be parented here in a later art pass.
	}
}
