package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Controlled fire grids for profiling and automated testing.
 */
public final class FireTestCommands {
	private FireTestCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("firetest")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("grid")
					.executes(ctx -> grid(ctx.getSource().getPlayerOrException(), 5))
					.then(Commands.argument("size", IntegerArgumentType.integer(1, 24))
						.executes(ctx -> grid(ctx.getSource().getPlayerOrException(),
							IntegerArgumentType.getInteger(ctx, "size")))))
				.then(Commands.literal("stats").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					int cells = FireSimulation.get(level).cells().size();
					int incidents = IncidentManager.get(level).openIncidents().size();
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Active fire cells=" + cells + " open incidents=" + incidents
					), false);
					return cells;
				}))
				.then(Commands.literal("clear").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					IncidentManager manager = IncidentManager.get(level);
					for (FireIncident incident : manager.openIncidents().toArray(FireIncident[]::new)) {
						manager.remove(incident.id);
					}
					FireSimulation.get(level).cells().clear();
					manager.markDirty();
					ctx.getSource().sendSuccess(() -> Component.literal("Cleared all fire simulation data."), true);
					return 1;
				}))
		));
	}

	private static int grid(ServerPlayer player, int size) {
		ServerLevel level = player.level();
		BlockPos origin = player.blockPosition();
		FireIncident incident = IncidentManager.get(level).createAt(origin, size);
		// Additional controlled grid pattern
		FireSimulation sim = FireSimulation.get(level);
		int ignited = 0;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockPos pos = origin.offset(x - size / 2, 0, z - size / 2);
				if (sim.tryIgnite(pos, incident.id, 70, true)) {
					ignited++;
				}
				if (sim.tryIgnite(pos.above(), incident.id, 60, true)) {
					ignited++;
				}
			}
		}
		player.sendSystemMessage(Component.literal(
			"Test grid size=" + size + " ignited=" + ignited + " incident=#" + incident.id
		));
		return ignited;
	}
}
