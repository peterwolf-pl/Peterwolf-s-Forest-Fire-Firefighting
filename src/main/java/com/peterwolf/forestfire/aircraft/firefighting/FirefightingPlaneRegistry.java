package com.peterwolf.forestfire.aircraft.firefighting;

import com.peterwolf.forestfire.ForestFireMod;
import com.piotrek.peterwolfsplanes.item.PlaneItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * Registers firefighting plane entity + item. Only called when Planes is present.
 */
public final class FirefightingPlaneRegistry {
	public static final ResourceKey<EntityType<?>> ENTITY_KEY =
		ResourceKey.create(Registries.ENTITY_TYPE, ForestFireMod.id("firefighting_plane"));

	public static EntityType<FirefightingPlaneEntity> ENTITY;
	public static Item ITEM;

	private FirefightingPlaneRegistry() {
	}

	public static void register() {
		ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			ENTITY_KEY,
			EntityType.Builder.<FirefightingPlaneEntity>of(FirefightingPlaneEntity::new, MobCategory.MISC)
				.sized(3.64f, 2.2f)
				.clientTrackingRange(10)
				.build(ENTITY_KEY)
		);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ForestFireMod.id("firefighting_plane"));
		ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			itemKey,
			new PlaneItem(
				new Item.Properties().setId(itemKey).stacksTo(1),
				world -> new FirefightingPlaneEntity(ENTITY, world)
			)
		);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(ITEM));
		CreativeModeTabEvents.modifyOutputEvent(
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, ForestFireMod.id("main"))
		).register(output -> output.accept(ITEM));

		ForestFireMod.LOGGER.info("Registered firefighting_plane entity and item.");
	}
}
