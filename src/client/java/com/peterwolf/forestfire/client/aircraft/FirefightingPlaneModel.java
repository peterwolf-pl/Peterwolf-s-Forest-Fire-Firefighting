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
 * Heavy, single-engine, high-wing amphibious water bomber.
 *
 * <p>The stepped hull, shoulder-mounted wing, centre-line engine pod, wing
 * floats and tall tail give the aircraft a flying-boat silhouette while
 * retaining the deliberately blocky proportions of a Minecraft entity.</p>
 */
public class FirefightingPlaneModel extends LargePlaneModel {
	private static final int TEX_WIDTH = 512;
	private static final int TEX_HEIGHT = 256;

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
		body.getChild("left_water_bomber_panel").yScale = 0.52F;
		body.getChild("right_water_bomber_panel").yScale = 0.52F;
		body.getChild("left_fire_rescue_panel").yScale = 0.52F;
		body.getChild("right_fire_rescue_panel").yScale = 0.52F;
	}

	public static LayerDefinition createLayerDefinition() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition rootPart = mesh.getRoot();

		// Model space: +Z aft. Negative Y is above the hull after the renderer transform.
		PartDefinition body = rootPart.addOrReplaceChild("body",
			CubeListBuilder.create()
				// Broad white upper fuselage and raised rescue-boat bow.
				.texOffs(0, 0)
				.addBox(-5.5F, -5.0F, -25.0F, 11.0F, 8.0F, 11.0F)
				.texOffs(0, 0)
				.addBox(-6.0F, -6.0F, -18.0F, 12.0F, 9.0F, 19.0F)
				.texOffs(0, 0)
				.addBox(-6.5F, -5.0F, 1.0F, 13.0F, 9.0F, 25.0F)
				// Stepped red lower hull: wide amidships, narrower at the bow.
				.texOffs(104, 0)
				.addBox(-4.0F, 3.0F, -28.0F, 8.0F, 4.0F, 8.0F)
				.texOffs(104, 0)
				.addBox(-5.0F, 3.0F, -22.0F, 10.0F, 6.0F, 13.0F)
				.texOffs(104, 0)
				.addBox(-6.0F, 3.0F, -11.0F, 12.0F, 7.0F, 34.0F)
				.texOffs(104, 0)
				.addBox(-4.5F, 3.5F, 23.0F, 9.0F, 5.5F, 8.0F)
				// Dark protective keel and the visible flying-boat hull step.
				.texOffs(256, 128)
				.addBox(-2.5F, 9.0F, -14.0F, 5.0F, 2.0F, 37.0F)
				.texOffs(312, 0)
				.addBox(-4.5F, 8.0F, 18.0F, 9.0F, 2.0F, 4.0F)
				// Continuous yellow rescue stripe on both sides.
				.texOffs(208, 0)
				.addBox(-6.65F, -1.5F, -20.0F, 0.35F, 2.5F, 43.0F)
				.texOffs(208, 0)
				.addBox(6.3F, -1.5F, -20.0F, 0.35F, 2.5F, 43.0F),
			PartPose.offset(0.0F, 14.0F, 0.0F)
		);

		// Two-seat cockpit with a stepped roof and framed forward/side glazing.
		PartDefinition cockpit = body.addOrReplaceChild("cockpit",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-5.0F, -9.5F, -19.0F, 10.0F, 4.5F, 12.0F)
				.texOffs(384, 0)
				.addBox(-4.4F, -8.7F, -19.2F, 8.8F, 4.0F, 0.5F)
				.texOffs(384, 0)
				.addBox(-5.25F, -8.4F, -17.5F, 0.5F, 3.5F, 8.0F)
				.texOffs(384, 0)
				.addBox(4.75F, -8.4F, -17.5F, 0.5F, 3.5F, 8.0F)
				// Heavy black window frames and central windscreen divider.
				.texOffs(312, 0)
				.addBox(-0.45F, -9.0F, -19.45F, 0.9F, 4.5F, 0.6F)
				.texOffs(312, 0)
				.addBox(-5.55F, -9.2F, -19.45F, 11.1F, 0.7F, 0.7F),
			PartPose.ZERO
		);
		cockpit.addOrReplaceChild("cockpit_roof_beacon",
			CubeListBuilder.create()
				.texOffs(208, 0)
				.addBox(-1.5F, -1.0F, -1.5F, 3.0F, 1.0F, 3.0F),
			PartPose.offset(0.0F, -9.5F, -11.0F)
		);

		// Integrated central water tank with maintenance panels and side fill ports.
		PartDefinition tank = body.addOrReplaceChild("water_tank",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-6.8F, -4.7F, 1.5F, 13.6F, 8.5F, 24.0F)
				.texOffs(456, 0)
				.addBox(-7.05F, -3.5F, 5.0F, 0.35F, 6.0F, 8.0F)
				.texOffs(456, 0)
				.addBox(6.7F, -3.5F, 5.0F, 0.35F, 6.0F, 8.0F)
				.texOffs(456, 0)
				.addBox(-7.05F, -3.5F, 15.0F, 0.35F, 6.0F, 7.0F)
				.texOffs(456, 0)
				.addBox(6.7F, -3.5F, 15.0F, 0.35F, 6.0F, 7.0F)
				// Round-looking square fill connections, one on each side.
				.texOffs(192, 128)
				.addBox(-7.55F, -2.5F, 12.0F, 0.8F, 4.0F, 4.0F)
				.texOffs(192, 128)
				.addBox(6.75F, -2.5F, 12.0F, 0.8F, 4.0F, 4.0F),
			PartPose.ZERO
		);
		tank.addOrReplaceChild("tank_spine",
			CubeListBuilder.create()
				.texOffs(384, 128)
				.addBox(-4.5F, -2.0F, -9.0F, 9.0F, 2.0F, 20.0F),
			PartPose.offset(0.0F, -4.7F, 13.0F)
		);

		// Large pixel-art emergency markings remain readable without tiny geometry.
		addSidePanel(body, "left_water_bomber_panel", -6.95F, -7.0F, 44);
		addSidePanel(body, "right_water_bomber_panel", 6.95F, -7.0F, 44);
		addSidePanel(body, "left_fire_rescue_panel", -7.30F, 13.0F, 0);
		addSidePanel(body, "right_fire_rescue_panel", 7.30F, 13.0F, 0);

		// Thick straight high wing. Stepped tips suggest taper without smooth geometry.
		PartDefinition wing = body.addOrReplaceChild("high_wing",
			CubeListBuilder.create()
				.texOffs(0, 64)
				.addBox(-13.0F, -14.0F, -3.0F, 26.0F, 3.0F, 17.0F)
				.texOffs(0, 64)
				.addBox(-47.0F, -13.7F, -2.5F, 34.0F, 2.7F, 16.0F)
				.texOffs(0, 64)
				.addBox(13.0F, -13.7F, -2.5F, 34.0F, 2.7F, 16.0F)
				.texOffs(264, 64)
				.addBox(-56.0F, -13.4F, -1.5F, 9.0F, 2.4F, 14.0F)
				.texOffs(264, 64)
				.addBox(47.0F, -13.4F, -1.5F, 9.0F, 2.4F, 14.0F),
			PartPose.ZERO
		);
		// Clearly separated flaps and ailerons along the trailing edge.
		wing.addOrReplaceChild("left_flap",
			CubeListBuilder.create().texOffs(0, 96)
				.addBox(-39.0F, -1.0F, 0.0F, 26.0F, 1.2F, 4.0F),
			PartPose.offset(0.0F, -11.0F, 13.0F)
		);
		wing.addOrReplaceChild("right_flap",
			CubeListBuilder.create().texOffs(0, 96)
				.addBox(13.0F, -1.0F, 0.0F, 26.0F, 1.2F, 4.0F),
			PartPose.offset(0.0F, -11.0F, 13.0F)
		);
		wing.addOrReplaceChild("left_aileron",
			CubeListBuilder.create().texOffs(264, 64)
				.addBox(-54.0F, -1.0F, 0.0F, 15.0F, 1.2F, 3.5F),
			PartPose.offset(0.0F, -11.0F, 12.5F)
		);
		wing.addOrReplaceChild("right_aileron",
			CubeListBuilder.create().texOffs(264, 64)
				.addBox(39.0F, -1.0F, 0.0F, 15.0F, 1.2F, 3.5F),
			PartPose.offset(0.0F, -11.0F, 12.5F)
		);
		wing.addOrReplaceChild("left_navigation_light",
			CubeListBuilder.create().texOffs(456, 64)
				.addBox(-1.5F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F),
			PartPose.offset(-55.0F, -12.2F, 4.0F)
		);
		wing.addOrReplaceChild("right_navigation_light",
			CubeListBuilder.create().texOffs(472, 64)
				.addBox(-0.5F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F),
			PartPose.offset(55.0F, -12.2F, 4.0F)
		);

		// Angled structural struts from the hull shoulders to the outer wing.
		PartDefinition leftStrut = body.addOrReplaceChild("left_strut",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-0.7F, -14.0F, -0.7F, 1.4F, 28.0F, 1.4F),
			PartPose.offsetAndRotation(-19.0F, -7.0F, 2.0F, 0.0F, 0.0F, 1.17F)
		);
		leftStrut.addOrReplaceChild("rear_brace",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-0.55F, -13.0F, -0.55F, 1.1F, 26.0F, 1.1F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.20F, 0.0F, 0.0F)
		);
		PartDefinition rightStrut = body.addOrReplaceChild("right_strut",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-0.7F, -14.0F, -0.7F, 1.4F, 28.0F, 1.4F),
			PartPose.offsetAndRotation(19.0F, -7.0F, 2.0F, 0.0F, 0.0F, -1.17F)
		);
		rightStrut.addOrReplaceChild("rear_brace",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-0.55F, -13.0F, -0.55F, 1.1F, 26.0F, 1.1F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.20F, 0.0F, 0.0F)
		);

		// Central engine pod above the hull, with intake, exhausts and mounting frame.
		PartDefinition engine = body.addOrReplaceChild("engine_pod",
			CubeListBuilder.create()
				.texOffs(312, 0)
				.addBox(-5.5F, -20.5F, -7.0F, 11.0F, 8.0F, 10.0F)
				.texOffs(208, 0)
				.addBox(-5.8F, -19.0F, -7.5F, 11.6F, 2.0F, 2.0F)
				.texOffs(312, 0)
				.addBox(-2.5F, -18.0F, -8.0F, 5.0F, 3.0F, 1.5F)
				.texOffs(312, 0)
				.addBox(-6.2F, -16.5F, -1.0F, 1.2F, 2.0F, 3.0F)
				.texOffs(312, 0)
				.addBox(5.0F, -16.5F, -1.0F, 1.2F, 2.0F, 3.0F)
				// Four-legged reinforced mounting frame.
				.texOffs(456, 0)
				.addBox(-4.5F, -13.0F, -4.0F, 1.2F, 4.0F, 1.2F)
				.texOffs(456, 0)
				.addBox(3.3F, -13.0F, -4.0F, 1.2F, 4.0F, 1.2F)
				.texOffs(456, 0)
				.addBox(-4.5F, -13.0F, 0.0F, 1.2F, 4.0F, 1.2F)
				.texOffs(456, 0)
				.addBox(3.3F, -13.0F, 0.0F, 1.2F, 4.0F, 1.2F),
			PartPose.ZERO
		);
		engine.addOrReplaceChild("engine_top_fairing",
			CubeListBuilder.create().texOffs(312, 0)
				.addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F),
			PartPose.offset(0.0F, -20.5F, -2.0F)
		);

		// Four-blade propeller and large yellow spinner at the front of the high pod.
		PartDefinition propeller = body.addOrReplaceChild("propeller",
			CubeListBuilder.create()
				.texOffs(64, 128)
				.addBox(-1.3F, -13.0F, -0.7F, 2.6F, 26.0F, 1.4F)
				.texOffs(64, 128)
				.addBox(-13.0F, -1.3F, -0.7F, 26.0F, 2.6F, 1.4F)
				.texOffs(208, 0)
				.addBox(-2.8F, -2.8F, -1.4F, 5.6F, 5.6F, 2.8F),
			PartPose.offset(0.0F, -16.5F, -8.2F)
		);
		propeller.addOrReplaceChild("spinner_cap",
			CubeListBuilder.create().texOffs(208, 0)
				.addBox(-1.8F, -1.8F, -1.5F, 3.6F, 3.6F, 3.0F),
			PartPose.offset(0.0F, 0.0F, -1.5F)
		);

		// Stabilising floats beneath the outer wing, each with two metal struts.
		addWingFloat(body, "left_float", -37.0F);
		addWingFloat(body, "right_float", 37.0F);

		// Four broad rectangular drop doors along the belly centreline.
		body.addOrReplaceChild("drop_doors",
			CubeListBuilder.create()
				.texOffs(104, 0)
				.addBox(-4.7F, -0.6F, -10.0F, 4.2F, 1.2F, 8.0F)
				.texOffs(104, 0)
				.addBox(0.5F, -0.6F, -10.0F, 4.2F, 1.2F, 8.0F)
				.texOffs(104, 0)
				.addBox(-4.7F, -0.6F, 0.0F, 4.2F, 1.2F, 8.0F)
				.texOffs(104, 0)
				.addBox(0.5F, -0.6F, 0.0F, 4.2F, 1.2F, 8.0F),
			PartPose.offset(0.0F, 10.8F, 8.0F)
		);

		// Retractable rear water-intake probe and always-visible protective housing.
		body.addOrReplaceChild("intake_housing",
			CubeListBuilder.create()
				.texOffs(312, 0)
				.addBox(-3.0F, 8.0F, 20.0F, 6.0F, 2.0F, 10.0F)
				.texOffs(208, 0)
				.addBox(-3.3F, 8.5F, 21.0F, 0.8F, 1.4F, 8.0F)
				.texOffs(208, 0)
				.addBox(2.5F, 8.5F, 21.0F, 0.8F, 1.4F, 8.0F),
			PartPose.ZERO
		);
		PartDefinition hose = body.addOrReplaceChild("intake_hose",
			CubeListBuilder.create()
				.texOffs(312, 0)
				.addBox(-1.2F, 0.0F, -1.2F, 2.4F, 22.0F, 2.4F)
				.texOffs(456, 0)
				.addBox(-1.7F, 5.0F, -1.7F, 3.4F, 1.0F, 3.4F)
				.texOffs(208, 0)
				.addBox(-1.7F, 12.0F, -1.7F, 3.4F, 1.0F, 3.4F),
			PartPose.offset(0.0F, 9.0F, 25.0F)
		);
		hose.addOrReplaceChild("hose_nozzle",
			CubeListBuilder.create()
				.texOffs(456, 0)
				.addBox(-2.8F, 20.5F, -4.0F, 5.6F, 2.5F, 8.0F)
				.texOffs(312, 0)
				.addBox(-1.8F, 22.5F, -2.5F, 3.6F, 2.5F, 5.0F),
			PartPose.ZERO
		);

		// Blue water-level inspection window on the starboard tank wall.
		body.addOrReplaceChild("tank_gauge",
			CubeListBuilder.create().texOffs(192, 128)
				.addBox(6.85F, -3.6F, 18.0F, 0.65F, 6.5F, 1.8F),
			PartPose.ZERO
		);

		// Narrowing rear fuselage and reinforced tall tail.
		body.addOrReplaceChild("tail_boom",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-4.5F, -3.5F, 25.0F, 9.0F, 7.0F, 12.0F)
				.texOffs(0, 0)
				.addBox(-3.5F, -4.0F, 36.0F, 7.0F, 6.0F, 12.0F)
				.texOffs(456, 0)
				.addBox(-4.5F, -4.8F, 33.0F, 9.0F, 1.2F, 6.0F),
			PartPose.ZERO
		);
		addIdentificationPanel(body, "left_identification", -4.70F);
		addIdentificationPanel(body, "right_identification", 4.70F);
		body.addOrReplaceChild("rudder",
			CubeListBuilder.create()
				.texOffs(104, 0)
				.addBox(-1.5F, -20.0F, 38.0F, 3.0F, 16.0F, 12.0F)
				.texOffs(104, 0)
				.addBox(-1.2F, -16.0F, 49.5F, 2.4F, 12.0F, 5.5F)
				.texOffs(0, 0)
				.addBox(-1.65F, -15.0F, 44.0F, 3.3F, 5.0F, 4.0F),
			PartPose.ZERO
		);
		PartDefinition tailWing = body.addOrReplaceChild("tail_wings",
			CubeListBuilder.create()
				.texOffs(0, 64)
				.addBox(-18.0F, -10.5F, 39.0F, 36.0F, 2.3F, 11.0F)
				.texOffs(104, 0)
				.addBox(-19.5F, -10.3F, 47.0F, 3.0F, 2.0F, 8.0F)
				.texOffs(104, 0)
				.addBox(16.5F, -10.3F, 47.0F, 3.0F, 2.0F, 8.0F),
			PartPose.ZERO
		);
		tailWing.addOrReplaceChild("elevators",
			CubeListBuilder.create().texOffs(0, 96)
				.addBox(-16.0F, -1.0F, 0.0F, 32.0F, 1.4F, 4.0F),
			PartPose.offset(0.0F, -8.2F, 49.0F)
		);

		// Amphibious landing gear: compact wheels and red protective doors.
		addLandingGear(body, "left_wheel", -7.0F, 5.0F);
		addLandingGear(body, "right_wheel", 7.0F, 5.0F);
		body.addOrReplaceChild("nose_wheel",
			CubeListBuilder.create()
				.texOffs(312, 0)
				.addBox(-2.0F, 6.0F, -20.0F, 4.0F, 6.0F, 5.0F)
				.texOffs(456, 0)
				.addBox(-0.7F, 2.0F, -18.3F, 1.4F, 5.0F, 1.4F)
				.texOffs(104, 0)
				.addBox(-2.7F, 4.0F, -21.0F, 5.4F, 1.0F, 7.0F),
			PartPose.ZERO
		);

		// Instrument children are required by PlaneModel and remain visible in the cockpit.
		PartDefinition speedDial = body.addOrReplaceChild("speed_dial",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-1.5F, -1.5F, -0.05F, 3.0F, 3.0F, 0.1F),
			PartPose.offset(-2.5F, -4.0F, -18.9F)
		);
		speedDial.addOrReplaceChild("speed_needle",
			CubeListBuilder.create().texOffs(104, 0)
				.addBox(-0.25F, -1.2F, -0.1F, 0.5F, 1.2F, 0.1F),
			PartPose.offset(0.0F, 0.0F, -0.1F)
		);
		PartDefinition altDial = body.addOrReplaceChild("alt_dial",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-1.5F, -1.5F, -0.05F, 3.0F, 3.0F, 0.1F),
			PartPose.offset(2.5F, -4.0F, -18.9F)
		);
		altDial.addOrReplaceChild("alt_needle",
			CubeListBuilder.create().texOffs(104, 0)
				.addBox(-0.25F, -1.2F, -0.1F, 0.5F, 1.2F, 0.1F),
			PartPose.offset(0.0F, 0.0F, -0.1F)
		);

		return LayerDefinition.create(mesh, TEX_WIDTH, TEX_HEIGHT);
	}

	private static void addWingFloat(PartDefinition body, String name, float x) {
		PartDefinition wingFloat = body.addOrReplaceChild(name,
			CubeListBuilder.create()
				.texOffs(128, 128)
				.addBox(-3.0F, -1.0F, -6.0F, 6.0F, 4.0F, 13.0F)
				.texOffs(208, 0)
				.addBox(-2.5F, -2.0F, -4.5F, 5.0F, 1.5F, 9.0F)
				.texOffs(312, 0)
				.addBox(-1.5F, 2.5F, -5.0F, 3.0F, 1.2F, 11.0F),
			PartPose.offset(x, 4.5F, 3.0F)
		);
		wingFloat.addOrReplaceChild("front_strut",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-0.6F, -15.0F, -0.6F, 1.2F, 15.0F, 1.2F),
			PartPose.offset(0.0F, -2.0F, -3.0F)
		);
		wingFloat.addOrReplaceChild("rear_strut",
			CubeListBuilder.create().texOffs(456, 0)
				.addBox(-0.6F, -15.0F, -0.6F, 1.2F, 15.0F, 1.2F),
			PartPose.offset(0.0F, -2.0F, 4.0F)
		);
	}

	private static void addSidePanel(PartDefinition body, String name, float x, float z, int textureU) {
		body.addOrReplaceChild(name,
			CubeListBuilder.create().texOffs(textureU, 192)
				.addBox(-0.15F, -6.5F, -9.5F, 0.3F, 13.0F, 19.0F),
			PartPose.offset(x, -0.5F, z)
		);
	}

	private static void addIdentificationPanel(PartDefinition body, String name, float x) {
		body.addOrReplaceChild(name,
			CubeListBuilder.create().texOffs(88, 192)
				.addBox(-0.15F, -2.5F, -4.0F, 0.3F, 5.0F, 8.0F),
			PartPose.offset(x, -1.0F, 32.0F)
		);
	}

	private static void addLandingGear(PartDefinition body, String name, float x, float z) {
		body.addOrReplaceChild(name,
			CubeListBuilder.create()
				.texOffs(312, 0)
				.addBox(-1.8F, 5.5F, -3.5F, 3.6F, 7.0F, 7.0F)
				.texOffs(456, 0)
				.addBox(-0.7F, 0.5F, -0.7F, 1.4F, 6.0F, 1.4F)
				.texOffs(104, 0)
				.addBox(-2.5F, 3.5F, -4.2F, 5.0F, 1.0F, 8.4F),
			PartPose.offset(x, 0.0F, z)
		);
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
