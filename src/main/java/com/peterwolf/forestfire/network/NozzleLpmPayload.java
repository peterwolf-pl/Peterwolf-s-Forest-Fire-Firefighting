package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server left-mouse (LPM) actions for the fire hose nozzle.
 * <ul>
 *   <li>0 = CLICK (press edge) — double-click latch / unlatch continuous</li>
 *   <li>1 = HOLD — key still down this tick (momentary spray when not latched)</li>
 *   <li>2 = RELEASE — key released (stop momentary spray)</li>
 * </ul>
 */
public record NozzleLpmPayload(int action) implements CustomPacketPayload {
	public static final int CLICK = 0;
	public static final int HOLD = 1;
	public static final int RELEASE = 2;

	public static final Type<NozzleLpmPayload> TYPE = new Type<>(ForestFireMod.id("nozzle_lpm"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NozzleLpmPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, NozzleLpmPayload::action,
		NozzleLpmPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
