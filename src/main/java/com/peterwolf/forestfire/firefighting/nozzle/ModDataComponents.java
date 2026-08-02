package com.peterwolf.forestfire.firefighting.nozzle;

import com.mojang.serialization.Codec;
import com.peterwolf.forestfire.ForestFireMod;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Custom item data components for nozzle mode, backpack water, air tank, etc.
 */
public final class ModDataComponents {
	public static final DataComponentType<Integer> NOZZLE_MODE = register("nozzle_mode",
		builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

	/** Stable hose endpoint UUID string; empty/null = disconnected free nozzle. */
	public static final DataComponentType<String> NOZZLE_ENDPOINT_ID = register("nozzle_endpoint_id",
		builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

	public static final DataComponentType<Integer> WATER_AMOUNT = register("water_amount",
		builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final DataComponentType<Integer> AIR_AMOUNT = register("air_amount",
		builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

	public static final DataComponentType<String> FIREFIGHTER_ROLE = register("firefighter_role",
		builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

	private ModDataComponents() {
	}

	public static void register() {
		// static init
	}

	private static <T> DataComponentType<T> register(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
		return Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			ForestFireMod.id(path),
			operator.apply(DataComponentType.builder()).build()
		);
	}
}
