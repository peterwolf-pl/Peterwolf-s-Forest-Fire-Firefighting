package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hose roll inventory item with remaining deployable length in blocks.
 */
public class HoseRollItem extends Item {
	private final int capacity;

	public HoseRollItem(Properties properties, int capacity) {
		super(properties.stacksTo(1));
		this.capacity = capacity;
	}

	public int capacity() {
		return capacity;
	}

	public static int getRemaining(ItemStack stack) {
		if (!(stack.getItem() instanceof HoseRollItem roll)) {
			return 0;
		}
		return stack.getOrDefault(ModDataComponents.HOSE_REMAINING, roll.capacity);
	}

	public static void setRemaining(ItemStack stack, int remaining) {
		if (!(stack.getItem() instanceof HoseRollItem roll)) {
			return;
		}
		int clamped = Math.max(0, Math.min(roll.capacity, remaining));
		stack.set(ModDataComponents.HOSE_REMAINING, clamped);
	}

	@Override
	public ItemStack getDefaultInstance() {
		ItemStack stack = super.getDefaultInstance();
		setRemaining(stack, capacity);
		return stack;
	}
}
