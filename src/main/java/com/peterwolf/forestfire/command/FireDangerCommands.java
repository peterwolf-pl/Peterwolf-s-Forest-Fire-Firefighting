package com.peterwolf.forestfire.command;

import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class FireDangerCommands {
	private FireDangerCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("firedanger")
				.executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
					WindSystem wind = IncidentManager.get(level).wind();
					FireDangerLevel danger = WeatherFireModel.danger(level, pos, wind);
					float humidity = WeatherFireModel.humidity01(level, pos);
					ctx.getSource().sendSuccess(() -> Component.literal("Fire danger: " + danger.name()), false);
					ctx.getSource().sendSuccess(() -> Component.literal(String.format("Humidity: %.0f%%", humidity * 100.0F)), false);
					ctx.getSource().sendSuccess(() -> Component.literal(String.format(
						"Wind: %.0f blocks/%s",
						wind.speed() * 30.0F,
						wind.compassLabel()
					)), false);
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Recent rainfall: " + WeatherFireModel.recentRainfallLabel(level, pos)
					), false);
					return 1;
				})
		));
	}
}
