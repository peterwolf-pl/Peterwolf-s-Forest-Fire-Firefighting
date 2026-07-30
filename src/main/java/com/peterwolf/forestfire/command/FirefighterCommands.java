package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.peterwolf.forestfire.firefighting.equipment.FirefighterRole;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import com.peterwolf.forestfire.item.AirTankItem;
import com.peterwolf.forestfire.item.BackpackSprayerItem;
import com.peterwolf.forestfire.item.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Role helpers and full firefighter equipment kit.
 */
public final class FirefighterCommands {
	private FirefighterCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
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
					.then(Commands.literal("kit")
						.executes(FirefighterCommands::giveKit)
						.then(Commands.literal("full").executes(FirefighterCommands::giveKit)))
					.then(Commands.literal("gear").executes(FirefighterCommands::giveKit))
			);

			// Short alias for the full kit
			dispatcher.register(
				Commands.literal("firekit")
					.executes(FirefighterCommands::giveKit)
			);
			dispatcher.register(
				Commands.literal("firesprzet")
					.executes(FirefighterCommands::giveKit)
			);
		});
	}

	/**
	 * Give the complete firefighter loadout: gear, tools, pump/hose gear, tanks, fuel.
	 */
	private static int giveKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Inventory inv = player.getInventory();
		int given = 0;

		// Protective gear
		given += give(inv, ModItems.FF_HELMET, 1);
		given += give(inv, ModItems.FF_CHESTPLATE, 1);
		given += give(inv, ModItems.FF_LEGGINGS, 1);
		given += give(inv, ModItems.FF_BOOTS, 1);
		given += give(inv, ModItems.BREATHING_MASK, 1);
		ItemStack air = new ItemStack(ModItems.AIR_TANK);
		AirTankItem.setAir(air, AirTankItem.MAX_AIR);
		if (inv.add(air)) {
			given++;
		} else {
			player.drop(air, false);
			given++;
		}

		// Tools
		given += give(inv, ModItems.FIRE_AXE, 1);
		given += give(inv, ModItems.PULASKI, 1);
		given += give(inv, ModItems.FIRE_SHOVEL, 1);
		given += give(inv, ModItems.FIRE_RAKE, 1);
		given += give(inv, ModItems.THERMAL_SCANNER, 1);
		given += give(inv, ModItems.FIREFIGHTER_INFO, 1);

		// Water attack
		given += give(inv, ModItems.FIRE_HOSE_NOZZLE, 1);
		ItemStack backpack = new ItemStack(ModItems.BACKPACK_SPRAYER);
		BackpackSprayerItem.setWater(backpack, com.peterwolf.forestfire.config.ForestFireConfig.get().backpackCapacity);
		if (inv.add(backpack)) {
			given++;
		} else {
			player.drop(backpack, false);
			given++;
		}

		// Pump / hose / supply (deployable)
		given += give(inv, ModItems.PORTABLE_PUMP, 1);
		given += give(inv, ModItems.HOSE_SPLITTER, 2);
		given += give(inv, ModItems.FIRE_HOSE, 32);
		given += give(inv, ModItems.INTAKE_HOSE, 16);
		given += give(inv, ModItems.INTAKE_STRAINER, 1);
		given += give(inv, ModItems.WATER_TANK_SMALL, 1);
		given += give(inv, ModItems.WATER_TANK_MEDIUM, 1);
		given += give(inv, ModItems.WATER_TANK_LARGE, 1);
		given += give(inv, ModItems.PORTABLE_SPRINKLER, 2);
		given += give(inv, ModItems.COMMAND_POST, 1);
		given += give(inv, ModItems.PUMP_FUEL_CAN, 8);

		final int total = given;
		ctx.getSource().sendSuccess(() -> Component.translatable(
			"message.peterwolfs_forestfire.kit_given",
			total
		), false);
		player.sendSystemMessage(Component.literal(
			"Sprzęt strażacki: hełm, ubranie, pompa, węże, prądownica, narzędzia, skaner, stanowisko dowodzenia."
		));
		return total;
	}

	private static int give(Inventory inv, Item item, int count) {
		ItemStack stack = new ItemStack(item, count);
		if (!inv.add(stack) && !stack.isEmpty()) {
			inv.player.drop(stack, false);
		}
		return 1;
	}
}
