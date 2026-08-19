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
 * High-wing (górnopłat) firefighting water bomber — Air Tractor / CL-style silhouette:
 * single high wing, integrated tank fuselage, ventral scoop hose, drop belly.
 */
public class FirefightingPlaneModel extends LargePlaneModel {
	private final ModelPart waterTank;
	private final ModelPart dropDoors;
	private final ModelPart intakeHose;
	private final ModelPart hoseNozzle;
	private final ModelPart tankGauge;
	private final ModelPart highWing;
	private final ModelPart leftStrut;
	private final ModelPart rightStrut;

	public FirefightingPlaneModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.waterTank = body.getChild("water_tank");
		this.dropDoors = body.getChild("drop_doors");
		this.intakeHose = body.getChild("intake_hose");
		this.hoseNozzle = this.intakeHose.getChild("hose_nozzle");
		this.tankGauge = body.getChild("tank_gauge");
		this.highWing = body.getChild("high_wing");
		this.leftStrut = body.getChild("left_strut");
		this.rightStrut = body.getChild("right_strut");
	}

	public static LayerDefinition createLayerDefinition() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition rootPart = mesh.getRoot();

		// Continuous fuselage: nose → cockpit → tank → tail (no stacked cargo boxes)
		// Model space: +Z aft, +Y up before renderer flip
		PartDefinition body = rootPart.addOrReplaceChild("body",
			CubeListBuilder.create()
				// Engine cowl / nose
				.texOffs(0, 0)
				.addBox(-5.5F, -5.0F, -18.0F, 11.0F, 10.0F, 12.0F)
				// Forward fuselage under cockpit
				.texOffs(100, 0)
				.addBox(-5.0F, -2.0F, -6.0F, 10.0F, 8.0F, 10.0F)
				// Cockpit side rails (open high-wing cropduster style)
				.texOffs(0, 36)
				.addBox(-5.5F, -4.0F, -6.0F, 1.0F, 6.0F, 10.0F)
				.texOffs(50, 36)
				.addBox(4.5F, -4.0F, -6.0F, 1.0F, 6.0F, 10.0F)
				// Windscreen frame
				.texOffs(180, 0)
				.addBox(-4.5F, -5.5F, -6.0F, 9.0F, 2.0F, 1.0F),
			PartPose.offset(0.0F, 14.0F, 0.0F)
		);

		// Integrated water tank bay — same height as fuselage line, slightly wider, long hopper
		body.addOrReplaceChild("water_tank",
			CubeListBuilder.create()
				// Hopper / tank (smooth continuation of fuselage, not a cargo stack)
				.texOffs(0, 0)
				.addBox(-6.5F, -5.5F, 3.0F, 13.0F, 12.0F, 16.0F)
				// Upper fairing / spine
				.texOffs(100, 0)
				.addBox(-5.0F, -7.0F, 4.0F, 10.0F, 2.0F, 14.0F)
				// Belly keel (drop bay outline)
				.texOffs(0, 36)
				.addBox(-5.0F, 6.0F, 5.0F, 10.0F, 2.0F, 12.0F),
			PartPose.ZERO
		);

		// High wing (górnopłat) — single main wing on top of tank/fuselage
		body.addOrReplaceChild("high_wing",
			CubeListBuilder.create()
				.texOffs(0, 68)
				// Wide high wing, slightly swept look via thickness
				.addBox(-48.0F, -9.5F, -2.0F, 96.0F, 2.5F, 14.0F)
				// Wing root fairing
				.texOffs(0, 84)
				.addBox(-8.0F, -8.0F, 0.0F, 16.0F, 2.0F, 10.0F),
			PartPose.ZERO
		);

		// Wing struts (V-strut cropduster style)
		body.addOrReplaceChild("left_strut",
			CubeListBuilder.create()
				.texOffs(224, 48)
				.addBox(-22.0F, -8.0F, 2.0F, 1.5F, 14.0F, 1.5F)
				.addBox(-34.0F, -9.0F, 4.0F, 1.2F, 12.0F, 1.2F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("right_strut",
			CubeListBuilder.create()
				.texOffs(228, 48)
				.addBox(20.5F, -8.0F, 2.0F, 1.5F, 14.0F, 1.5F)
				.addBox(32.8F, -9.0F, 4.0F, 1.2F, 12.0F, 1.2F),
			PartPose.ZERO
		);

		// Red tip tanks / wing tanks (firefighting look)
		body.addOrReplaceChild("left_tip_tank",
			CubeListBuilder.create()
				.texOffs(154, 94)
				.addBox(-50.0F, -10.0F, 1.0F, 3.0F, 3.5F, 8.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("right_tip_tank",
			CubeListBuilder.create()
				.texOffs(182, 94)
				.addBox(47.0F, -10.0F, 1.0F, 3.0F, 3.5F, 8.0F),
			PartPose.ZERO
		);

		// Drop doors under hopper
		body.addOrReplaceChild("drop_doors",
			CubeListBuilder.create()
				.texOffs(50, 36)
				.addBox(-4.5F, 7.5F, 7.0F, 9.0F, 1.2F, 8.0F),
			PartPose.ZERO
		);

		// Long retractable scoop probe (must reach into water when deployed)
		PartDefinition hose = body.addOrReplaceChild("intake_hose",
			CubeListBuilder.create()
				.texOffs(212, 48)
				// Probe tube — long enough to dip into water under low pass
				.addBox(-1.0F, 0.0F, -1.0F, 2.0F, 22.0F, 2.0F)
				.texOffs(180, 0)
				.addBox(-1.4F, 6.0F, -1.4F, 2.8F, 1.0F, 2.8F)
				.addBox(-1.4F, 13.0F, -1.4F, 2.8F, 1.0F, 2.8F),
			PartPose.offset(0.0F, 8.0F, 10.0F)
		);
		hose.addOrReplaceChild("hose_nozzle",
			CubeListBuilder.create()
				// Scoop shoe / intake head
				.texOffs(154, 94)
				.addBox(-2.2F, 21.0F, -3.5F, 4.4F, 2.5F, 7.0F)
				.texOffs(182, 94)
				.addBox(-1.5F, 23.0F, -2.0F, 3.0F, 2.0F, 4.0F),
			PartPose.ZERO
		);

		// Side tank level gauge
		body.addOrReplaceChild("tank_gauge",
			CubeListBuilder.create()
				.texOffs(98, 2)
				.addBox(6.6F, -4.0F, 10.0F, 0.8F, 8.0F, 1.5F),
			PartPose.ZERO
		);

		// Streamlined tail boom from tank to empennage
		body.addOrReplaceChild("tail_boom",
			CubeListBuilder.create()
				.texOffs(0, 98)
				.addBox(-3.0F, -3.0F, 18.0F, 6.0F, 6.0F, 22.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("rudder",
			CubeListBuilder.create()
				.texOffs(100, 36)
				.addBox(-1.2F, -16.0F, 36.0F, 2.4F, 16.0F, 8.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("tail_wings",
			CubeListBuilder.create()
				.texOffs(130, 36)
				.addBox(-13.0F, -2.0F, 34.0F, 26.0F, 2.0F, 8.0F),
			PartPose.ZERO
		);

		// Propeller
		body.addOrReplaceChild("propeller",
			CubeListBuilder.create()
				.texOffs(212, 48)
				.addBox(-1.5F, -13.0F, -0.5F, 3.0F, 26.0F, 1.0F),
			PartPose.offset(0.0F, 0.0F, -18.5F)
		);

		// Conventional gear
		body.addOrReplaceChild("left_wheel",
			CubeListBuilder.create()
				.texOffs(154, 94)
				.addBox(-8.5F, 5.0F, -6.0F, 3.5F, 9.0F, 9.0F),
			PartPose.ZERO
		);
		body.addOrReplaceChild("right_wheel",
			CubeListBuilder.create()
				.texOffs(182, 94)
				.addBox(5.0F, 5.0F, -6.0F, 3.5F, 9.0F, 9.0F),
			PartPose.ZERO
		);

		// Dummy parts expected by LargePlaneModel / PlaneModel hierarchy (unused biplane leftovers not required)
		// PlaneModel needs speed_dial / alt_dial / propeller under body — already have propeller.
		PartDefinition speedDial = body.addOrReplaceChild("speed_dial",
			CubeListBuilder.create()
				.texOffs(98, 2)
				.addBox(-1.5F, -1.5F, -0.05F, 3.0F, 3.0F, 0.1F),
			PartPose.offset(-2.5F, 0.5F, -5.5F)
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
			PartPose.offset(2.5F, 0.5F, -5.5F)
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

		this.highWing.visible = true;
		this.leftStrut.visible = true;
		this.rightStrut.visible = true;
		this.waterTank.visible = true;

		boolean deployed = hose > 0.02F;
		this.intakeHose.visible = deployed;
		this.hoseNozzle.visible = deployed;
		if (deployed) {
			// Full probe length when deployed — must reach into water
			this.intakeHose.yScale = 0.12F + 0.88F * hose;
			this.intakeHose.xRot = (float) Math.toRadians(6.0F + state.speed * 0.9F) * hose;
			this.intakeHose.zRot = (float) Math.toRadians(state.roll * 0.12F) * hose;
		} else {
			this.intakeHose.yScale = 0.12F;
			this.intakeHose.xRot = 0.0F;
			this.intakeHose.zRot = 0.0F;
		}

		this.dropDoors.xRot = (float) Math.toRadians(door * 65.0F);
		this.tankGauge.yScale = 0.2F + 0.8F * fill;
	}
}
