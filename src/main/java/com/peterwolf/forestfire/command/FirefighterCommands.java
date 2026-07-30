package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.peterwolf.forestfire.firefighting.equipment.FirefighterRole;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class FirefighterCommands {
	private FirefighterCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("firefighter")
				.then(Commands.literal("role")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						String role = player.getMainHandItem().getOrDefault(ModDataComponents.FIREFIGHTER_ROLE, "CREW_MEMBER");
						ctx.getSource().sendSuccess(() -> Component.literal("Current role: " + FirefighterRole.byId(role).displayName), false);
						return 1;
					})
					.then(Commands.argument("name", StringArgumentType.word())
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							FirefighterRole role = FirefighterRole.byId(StringArgumentType.getString(ctx, "name"));
							ItemStack stack = player.getMainHandItem();
							if (!stack.isEmpty()) {
								stack.set(ModDataComponents.FIREFIGHTER_ROLE, role.name());
							}
							// Also store on player persistent data via offhand/info item fallback message
							ctx.getSource().sendSuccess(() -> Component.literal(
								"Role set to " + role.displayName + " (roles do not permanently restrict actions)"
							), false);
							return 1;
						})))
				.then(Commands.literal("roles").executes(ctx -> {
					for (FirefighterRole role : FirefighterRole.values()) {
						ctx.getSource().sendSuccess(() -> Component.literal("- " + role.name() + " : " + role.displayName), false);
					}
					return FirefighterRole.values().length;
				}))
		));
	}
}
