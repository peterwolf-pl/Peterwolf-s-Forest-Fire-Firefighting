package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentStatus;
import com.peterwolf.forestfire.mission.MissionScorer;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FireIncidentCommands {
	private FireIncidentCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("fireincident")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("create")
					.executes(ctx -> create(ctx, 3, null))
					.then(Commands.argument("size", IntegerArgumentType.integer(1, 32))
						.executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "size"), null)))
					.then(Commands.argument("x", IntegerArgumentType.integer())
						.then(Commands.argument("y", IntegerArgumentType.integer())
							.then(Commands.argument("z", IntegerArgumentType.integer())
								.then(Commands.argument("size", IntegerArgumentType.integer(1, 32))
									.executes(ctx -> create(ctx,
										IntegerArgumentType.getInteger(ctx, "size"),
										new BlockPos(
											IntegerArgumentType.getInteger(ctx, "x"),
											IntegerArgumentType.getInteger(ctx, "y"),
											IntegerArgumentType.getInteger(ctx, "z")
										))))))))
				.then(Commands.literal("list").executes(FireIncidentCommands::list))
				.then(Commands.literal("info")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(FireIncidentCommands::info)))
				.then(Commands.literal("contain")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> setStatus(ctx, IncidentStatus.CONTAINED))))
				.then(Commands.literal("extinguish")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(FireIncidentCommands::extinguish)))
				.then(Commands.literal("remove")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(FireIncidentCommands::remove)))
				.then(Commands.literal("teleport")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(FireIncidentCommands::teleport)))
				.then(Commands.literal("control")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> setStatus(ctx, IncidentStatus.CONTROLLED))))
				.then(Commands.literal("close")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> setStatus(ctx, IncidentStatus.CLOSED))))
		));
	}

	private static int create(CommandContext<CommandSourceStack> ctx, int size, BlockPos pos) throws CommandSyntaxException {
		ServerLevel level = ctx.getSource().getLevel();
		if (pos == null) {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			pos = player.blockPosition();
		}
		try {
			FireIncident incident = IncidentManager.get(level).createAt(pos, size);
			ctx.getSource().sendSuccess(() -> Component.literal("Created " + incident.summaryLine()), true);
			return incident.id;
		} catch (IllegalStateException exception) {
			ctx.getSource().sendFailure(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		IncidentManager manager = IncidentManager.get(ctx.getSource().getLevel());
		if (manager.all().isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No incidents."), false);
			return 0;
		}
		for (FireIncident incident : manager.all()) {
			ctx.getSource().sendSuccess(() -> Component.literal(incident.summaryLine()), false);
		}
		return manager.all().size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		Optional<FireIncident> incident = IncidentManager.get(ctx.getSource().getLevel()).get(id);
		if (incident.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Unknown incident #" + id));
			return 0;
		}
		for (String line : incident.get().infoLines()) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		float score = MissionScorer.score(incident.get());
		ctx.getSource().sendSuccess(() -> Component.literal(
			"Mission grade: " + MissionScorer.grade(score) + " (" + String.format("%.0f", score) + ")"
		), false);
		return 1;
	}

	private static int setStatus(CommandContext<CommandSourceStack> ctx, IncidentStatus status) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		boolean ok = IncidentManager.get(ctx.getSource().getLevel()).setStatus(id, status);
		if (!ok) {
			ctx.getSource().sendFailure(Component.literal("Unknown incident #" + id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Incident #" + id + " -> " + status), true);
		return 1;
	}

	private static int extinguish(CommandContext<CommandSourceStack> ctx) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		Optional<FireIncident> incident = IncidentManager.get(ctx.getSource().getLevel()).get(id);
		if (incident.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Unknown incident #" + id));
			return 0;
		}
		IncidentManager.get(ctx.getSource().getLevel()).extinguishAll(id);
		ctx.getSource().sendSuccess(() -> Component.literal("Extinguished incident #" + id), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		boolean ok = IncidentManager.get(ctx.getSource().getLevel()).remove(id);
		if (!ok) {
			ctx.getSource().sendFailure(Component.literal("Unknown incident #" + id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Removed incident #" + id), true);
		return 1;
	}

	private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(ctx, "id");
		Optional<FireIncident> incident = IncidentManager.get(player.level()).get(id);
		if (incident.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Unknown incident #" + id));
			return 0;
		}
		BlockPos pos = incident.get().ignitionPos;
		player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
		ctx.getSource().sendSuccess(() -> Component.literal("Teleported to " + incident.get().name), false);
		return 1;
	}
}
