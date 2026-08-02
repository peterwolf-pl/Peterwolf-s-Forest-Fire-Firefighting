package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.peterwolf.forestfire.aircraft.firefighting.FirefightingPlaneEntity;
import com.peterwolf.forestfire.component.water_tank.AircraftWaterTank;
import com.peterwolf.forestfire.simulation.water_drop.WaterDropSimulator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FireplaneCommands {
	private FireplaneCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fireplane")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

			root.then(Commands.literal("tank")
				.then(Commands.literal("fill").executes(ctx -> tankSet(ctx.getSource(), AircraftWaterTank.capacity())))
				.then(Commands.literal("empty").executes(ctx -> tankSet(ctx.getSource(), 0)))
				.then(Commands.literal("set")
					.then(Commands.argument("amount", IntegerArgumentType.integer(0))
						.executes(ctx -> tankSet(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount"))))));

			root.then(Commands.literal("hose")
				.then(Commands.literal("deploy").executes(ctx -> hose(ctx.getSource(), true)))
				.then(Commands.literal("retract").executes(ctx -> hose(ctx.getSource(), false))));

			root.then(Commands.literal("drop")
				.then(Commands.literal("arm").executes(ctx -> drop(ctx.getSource(), true)))
				.then(Commands.literal("disarm").executes(ctx -> drop(ctx.getSource(), false))));

			root.then(Commands.literal("debug").executes(ctx -> debug(ctx.getSource())));

			dispatcher.register(root);
		});
	}

	private static FirefightingPlaneEntity requirePlane(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer player
			&& player.getVehicle() instanceof FirefightingPlaneEntity plane) {
			return plane;
		}
		source.sendFailure(Component.literal("You must be piloting a firefighting plane."));
		return null;
	}

	private static int tankSet(CommandSourceStack source, int amount) {
		FirefightingPlaneEntity plane = requirePlane(source);
		if (plane == null) {
			return 0;
		}
		plane.setWaterAmount(amount);
		source.sendSuccess(
			() -> Component.literal("Tank set to " + plane.getWaterAmount() + " / " + AircraftWaterTank.capacity()),
			true
		);
		return 1;
	}

	private static int hose(CommandSourceStack source, boolean deploy) {
		FirefightingPlaneEntity plane = requirePlane(source);
		if (plane == null) {
			return 0;
		}
		plane.forceHose(deploy);
		source.sendSuccess(() -> Component.literal(deploy ? "Hose deployed" : "Hose retracted"), true);
		return 1;
	}

	private static int drop(CommandSourceStack source, boolean arm) {
		FirefightingPlaneEntity plane = requirePlane(source);
		if (plane == null) {
			return 0;
		}
		plane.forceDropArmed(arm);
		source.sendSuccess(() -> Component.literal(arm ? "Drop ARMED" : "Drop DISARMED"), true);
		return 1;
	}

	private static int debug(CommandSourceStack source) {
		FirefightingPlaneEntity plane = requirePlane(source);
		if (plane == null) {
			return 0;
		}
		plane.setDebugMode(!plane.isDebugMode());
		boolean on = plane.isDebugMode();
		var nozzle = plane.getHoseNozzlePosition(1.0F);
		var probe = plane.probeWaterBelowNozzle(
			com.peterwolf.forestfire.config.ForestFireConfig.get().firefightingAircraft.maximumWaterDistanceBlocks
		);
		double speed = plane.getDeltaMovement().horizontalDistance();
		String sim = source.getLevel() instanceof ServerLevel level
			? WaterDropSimulator.get(level).debugSummary()
			: "n/a";
		source.sendSuccess(() -> Component.literal(String.format(
			"debug=%s nozzle=(%.1f,%.1f,%.1f) waterDist=%.2f valid=%s speed=%.3f pitch=%.1f roll=%.1f tank=%d status=%s sim[%s]",
			on,
			nozzle.x, nozzle.y, nozzle.z,
			probe.distance(),
			probe.valid(),
			speed,
			plane.getXRot(),
			plane.getRoll(),
			plane.getWaterAmount(),
			plane.getIntakeStatus(),
			sim
		)), false);
		return 1;
	}
}
