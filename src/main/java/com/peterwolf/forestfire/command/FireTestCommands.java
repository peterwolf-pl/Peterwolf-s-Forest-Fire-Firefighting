package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.peterwolf.forestfire.config.ForestFireConfig;
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
					FireSimulation simulation = FireSimulation.get(level);
					int cells = simulation.cells().size();
					int activeCells = simulation.activeCellCount();
					int chunkTickets = simulation.activeFireChunkTicketCount();
					ForestFireConfig.Data cfg = ForestFireConfig.get();
					int incidents = IncidentManager.get(level).openIncidents().size();
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Fire cells tracked=" + cells
							+ " active=" + activeCells + "/" + cfg.maxBurningBlocksPerWorld
							+ " chunk tickets=" + chunkTickets + "/" + cfg.maxFireChunkTickets
							+ " open incidents=" + incidents
					), false);
					return cells;
				}))
				.then(Commands.literal("clear").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					IncidentManager manager = IncidentManager.get(level);
					for (FireIncident incident : manager.openIncidents().toArray(FireIncident[]::new)) {
						manager.remove(incident.id);
					}
					FireSimulation simulation = FireSimulation.get(level);
					simulation.cells().clear();
					simulation.clearAllSparks();
					manager.markDirty();
					ctx.getSource().sendSuccess(() -> Component.literal("Cleared all fire simulation data."), true);
					return 1;
				}))
				.then(Commands.literal("hoses").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					var mgr = com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager.get(level);
					int n = 0;
					for (var c : mgr.all()) {
						n++;
						ctx.getSource().sendSuccess(() -> Component.literal(
							c.type + " " + c.connectionId.toString().substring(0, 8)
								+ " src=" + c.sourcePos.toShortString()
								+ " tgt=" + c.targetPos.toShortString()
								+ " len=" + c.deployedLength
								+ " path=" + String.format("%.1f", c.currentPathLength)
								+ " " + (c.connected ? "OK" : "DOWN")
						), false);
					}
					int total = n;
					ctx.getSource().sendSuccess(() -> Component.literal("Automatic hose connections: " + total), false);
					return total;
				}))
				.then(Commands.literal("hosekit").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					giveHoseTestKit(player);
					ctx.getSource().sendSuccess(() -> Component.literal("Hose test kit given."), false);
					return 1;
				}))
		));
	}

	private static void giveHoseTestKit(ServerPlayer player) {
		var inv = player.getInventory();
		inv.add(new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.PORTABLE_PUMP));
		inv.add(new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.FIRE_HOSE_NOZZLE));
		inv.add(new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.HOSE_CONNECTOR));
		inv.add(new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.INTAKE_STRAINER));
		inv.add(new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.PUMP_FUEL_CAN, 4));
		var large = new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.HOSE_ROLL_LARGE);
		com.peterwolf.forestfire.item.HoseRollItem.setRemaining(large, 64);
		inv.add(large);
		var std = new net.minecraft.world.item.ItemStack(com.peterwolf.forestfire.item.ModItems.HOSE_ROLL_STANDARD);
		com.peterwolf.forestfire.item.HoseRollItem.setRemaining(std, 32);
		inv.add(std);
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
