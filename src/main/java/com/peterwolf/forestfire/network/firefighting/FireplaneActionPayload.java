package com.peterwolf.forestfire.network.firefighting;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server firefighting plane actions.
 * Server validates pilot ownership and plane type.
 */
public record FireplaneActionPayload(byte action, boolean active) implements CustomPacketPayload {
	public static final byte TOGGLE_DROP_ARMED = 1;
	public static final byte SET_RELEASING = 2;
	public static final byte TOGGLE_HOSE = 3;

	public static final Identifier ID = ForestFireMod.id("fireplane_action");
	public static final CustomPacketPayload.Type<FireplaneActionPayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, FireplaneActionPayload> CODEC =
		new StreamCodec<>() {
			@Override
			public FireplaneActionPayload decode(RegistryFriendlyByteBuf buf) {
				return new FireplaneActionPayload(buf.readByte(), buf.readBoolean());
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, FireplaneActionPayload value) {
				buf.writeByte(value.action);
				buf.writeBoolean(value.active);
			}
		};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
