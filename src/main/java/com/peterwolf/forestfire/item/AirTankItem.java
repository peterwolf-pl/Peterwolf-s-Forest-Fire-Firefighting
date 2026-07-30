package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class AirTankItem extends Item {
	public static final int MAX_AIR = 1200; // ticks of clean air (~60s)

	public AirTankItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static int getAir(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.AIR_AMOUNT, MAX_AIR);
	}

	public static void setAir(ItemStack stack, int amount) {
		stack.set(ModDataComponents.AIR_AMOUNT, Math.max(0, Math.min(MAX_AIR, amount)));
	}

	public static boolean consume(ItemStack stack, int amount) {
		int air = getAir(stack);
		if (air <= 0) {
			return false;
		}
		setAir(stack, air - amount);
		return true;
	}
}
