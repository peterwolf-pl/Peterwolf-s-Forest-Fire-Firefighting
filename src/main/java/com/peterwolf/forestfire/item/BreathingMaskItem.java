package com.peterwolf.forestfire.item;

import net.minecraft.world.item.Item;

/** Reduces smoke effects when worn in helmet slot or offhand with air tank. */
public class BreathingMaskItem extends Item {
	public BreathingMaskItem(Properties properties) {
		super(properties.stacksTo(1));
	}
}
