package com.peterwolf.forestfire.client.aircraft;

import com.piotrek.peterwolfsplanes.client.LargePlaneModel;
import com.piotrek.peterwolfsplanes.client.PlaneRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Large biplane water bomber: bulk water tank behind the cockpit, ventral intake hose,
 * and drop doors. Hose extends smoothly with {@link FirefightingPlaneRenderState#hoseProgress}.
 */
public class FirefightingPlaneModel extends LargePlaneModel {
	private final ModelPart waterTank;
	private final ModelPart dropDoors;
	private final ModelPart intakeHose;
	private final ModelPart hoseNozzle;
	private final ModelPart tankGauge;

	public FirefightingPlaneModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.waterTank = body.getChild("water_tank");
		this.dropDoors = body.getChild("drop_doors");
		this.intakeHose = body.getChild("intake_hose");
		this.hoseNozzle = this.intakeHose.getChild("hose_nozzle");
		this.tankGauge = body.getChild("tank_gauge");
	}

	public static LayerDefinition createLayerDefinition() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition rootPart = mesh.getRoot();

		// Base large biplane airframe (same UV layout as LargePlaneModel)
		PartDefinition body = rootPart.addOrReplaceChild("body",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-6.0F, -6.0F, -16.0F, 12.0F, 12.0F, 14.0F)
				.texOffs(100, 0)
				.addBox(-6.0F, 5.0F, -2.0F, 12.0F, 1.0F, 21.0F)
				.texOffs(0, 36)
				.addBox(-6.0F, -3.0F, -2.0F, 1.0F, 8.0F, 21.0F)
				.texOffs(50, 36)
				.addBox(5.0F, -3.0F, -2.0F, 1.0F, 8.0F, 21.0F)
				.texOffs(180, 0)
				.addBox(-5.0F, -3.0F, 18.0F, 10.0F, 8.0F, 1.0F),
			PartPose.offset(0.0F, 14.0F, 0.0F)
		);

		// --- Water bomber bulk tank: wider + taller behind the cockpit ---
		// Original fuselage ~12 wide × ~12 tall; tank ~18 wide × ~18 tall.
		body.addOrReplaceChild("water_tank",
			CubeListBuilder.create()
				.texOffs(0, 0)
				// Main tank hull behind cockpit (starts mid-cockpit, extends aft)
				.addBox(-9.0F, -10.0F, 2.0F, 18.0F, 18.0F, 18.0F)
				// Slight roof fairing
				.texOffs(100, 0)
				.addBox(-7.0F, -12.0F, 4.0F, 14.0F, 2.0F, 14.0F)
				// Belly chines
				.texOffs(0, 36)
				.addBox(-8.0F, 7.5F, 3.0F, 16.0F, 2.0F, 16.0F),
			PartPose.ZERO
		);

		// Ventral drop doors (open when releasing)
		body.addOrReplaceChild("drop_doors",
			CubeListBuilder.create()
				.texOffs(50, 36)
				.addBox(-6.0F, 9.0F, 6.0F, 12.0F, 1.5F, 10.0F),
			PartPose.offset(0.0F, 0.0F, 0.0F)
		);

		// Retractable intake hose under tank
		PartDefinition hose = body.addOrReplaceChild("intake_hose",
			CubeListBuilder.create()
				// Hose tube (extends along +Y → world down after renderer flip)
				.texOffs(212, 48)
				.addBox(-1.25F, 0.0F, -1.25F, 2.5F, 18.0F, 2.5F)
				// Hose spiral detail rings
				.texOffs(180, 0)
				.addBox(-1.6F, 4.0F, -1.6F, 3.2F, 1.0F, 3.2F)
				.addBox(-1.6F, 10.0F, -1.6F, 3.2F, 1.0F, 3.2F),
			PartPose.offset(0.0F, 11.0F, 8.0F)
		);
		hose.addOrReplaceChild("hose_nozzle",
			CubeListBuilder.create()
				.texOffs(154, 94)
				// Scoop nozzle / shoe at end of hose
				.addBox(-2.5F, 17.0F, -3.0F, 5.0F, 3.0F, 6.0F)
				.texOffs(182, 94)
				.addBox(-1.5F, 20.0F, -2.0F, 3.0F, 2.0F, 4.0F),
			PartPose.ZERO
		);

		// Side tank fill gauge (visual only)
		body.addOrReplaceChild("tank_gauge",
			CubeListBuilder.create()
				.texOffs(98, 2)
				.addBox(9.0F, -6.0F, 8.0F, 1.0F, 10.0F, 2.0F),
			PartPose.ZERO
		);

		body.addOrReplaceChild("lower_wing",
			CubeListBuilder.create()
				.texOffs(0, 68)
				.addBox(-47.0F, 6.0F, -6.0F, 94.0F, 2.0F, 12.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("upper_wing",
			CubeListBuilder.create()
				.texOffs(0, 84)
				.addBox(-54.0F, -20.0F, -6.0F, 108.0F, 2.0F, 12.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("left_support_1",
			CubeListBuilder.create().texOffs(224, 48).addBox(-36.4F, -18.0F, -4.0F, 1.0F, 24.0F, 1.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("left_support_2",
			CubeListBuilder.create().texOffs(224, 48).addBox(-36.4F, -18.0F, 4.0F, 1.0F, 24.0F, 1.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("right_support_1",
			CubeListBuilder.create().texOffs(228, 48).addBox(35.1F, -18.0F, -4.0F, 1.0F, 24.0F, 1.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("right_support_2",
			CubeListBuilder.create().texOffs(228, 48).addBox(35.1F, -18.0F, 4.0F, 1.0F, 24.0F, 1.0F),
			PartPose.ZERO
		);

		// Tail boom starts further aft to clear bulk tank
		body.addOrReplaceChild("tail_boom",
			CubeListBuilder.create()
				.texOffs(0, 98)
				.addBox(-3.5F, -3.5F, 18.0F, 7.0F, 7.0F, 20.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("rudder",
			CubeListBuilder.create()
				.texOffs(100, 36)
				.addBox(-1.5F, -18.0F, 35.0F, 3.0F, 15.0F, 9.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("tail_wings",
			CubeListBuilder.create()
				.texOffs(130, 36)
				.addBox(-12.0F, -3.0F, 33.0F, 24.0F, 2.0F, 9.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("propeller",
			CubeListBuilder.create()
				.texOffs(212, 48)
				.addBox(-1.5F, -12.0F, -0.5F, 3.0F, 24.0F, 1.0F),
			PartPose.offset(0.0F, 0.0F, -16.5F)
		);
		body.addOrReplaceChild("left_wheel",
			CubeListBuilder.create()
				.texOffs(154, 94)
				.addBox(-9.0F, 4.0F, -9.0F, 4.0F, 10.0F, 12.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("right_wheel",
			CubeListBuilder.create()
				.texOffs(182, 94)
				.addBox(5.0F, 4.0F, -9.0F, 4.0F, 10.0F, 12.0F),
			PartPose.ZERO
		);

		PartDefinition speedDial = body.addOrReplaceChild("speed_dial",
			CubeListBuilder.create()
				.texOffs(98, 2)
				.addBox(-1.5F, -1.5F, -0.05F, 3.0F, 3.0F, 0.1F),
			PartPose.offset(-2.5F, 0.0F, -2.0F)
		);
		speedDial.addOrReplaceChild("speed_needle",
			CubeListBuilder.create()
				.texOffs(56, 20)
				.addBox(-0.25F, -1.2F, -0.1F, 0.5F, 1.2F, 0.1F),
			PartPose.offset(0.0F, 0.0F, -0.1F)
		);
		PartDefinition altDial = body.addOrReplaceChild("alt_dial",
			CubeListBuilder.create()
				.texOffs(98, 2)
				.addBox(-1.5F, -1.5F, -0.05F, 3.0F, 3.0F, 0.1F),
			PartPose.offset(2.5F, 0.0F, -2.0F)
		);
		altDial.addOrReplaceChild("alt_needle",
			CubeListBuilder.create()
				.texOffs(56, 20)
				.addBox(-0.25F, -1.2F, -0.1F, 0.5F, 1.2F, 0.1F),
			PartPose.offset(0.0F, 0.0F, -0.1F)
		);

		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	public void setupAnim(PlaneRenderState state) {
		super.setupAnim(state);
		float hose = 0.0F;
		float door = 0.0F;
		float fill = 0.0F;
		if (state instanceof FirefightingPlaneRenderState ff) {
			hose = Mth.clamp(ff.hoseProgress, 0.0F, 1.0F);
			door = Mth.clamp(ff.doorProgress, 0.0F, 1.0F);
			fill = Mth.clamp(ff.tankFill, 0.0F, 1.0F);
		}

		// Hose: hide when fully stowed; scale length + slight trail tilt when flying
		boolean deployed = hose > 0.02F;
		this.intakeHose.visible = deployed;
		this.hoseNozzle.visible = deployed;
		if (deployed) {
			// Retracted length ~5%, full hose ~100%
			this.intakeHose.yScale = 0.08F + 0.92F * hose;
			// Trail aft with airspeed (model +Z is aft on this airframe)
			this.intakeHose.xRot = (float) Math.toRadians(8.0F + state.speed * 1.2F) * hose;
			// Subtle sway from roll
			this.intakeHose.zRot = (float) Math.toRadians(state.roll * 0.15F) * hose;
		} else {
			this.intakeHose.yScale = 0.08F;
			this.intakeHose.xRot = 0.0F;
			this.intakeHose.zRot = 0.0F;
		}

		// Drop doors hinge open downward
		this.dropDoors.xRot = (float) Math.toRadians(door * 70.0F);
		this.dropDoors.visible = true;

		// Gauge fill indicator
		this.tankGauge.yScale = 0.2F + 0.8F * fill;
		this.waterTank.visible = true;
	}
}
