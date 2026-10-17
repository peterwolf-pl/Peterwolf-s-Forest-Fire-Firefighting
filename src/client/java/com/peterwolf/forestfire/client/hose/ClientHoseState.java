package com.peterwolf.forestfire.client.hose;

import com.peterwolf.forestfire.network.HoseSyncPayload;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side cache of automatic hose paths for rendering.
 */
public final class ClientHoseState {
	private static final Map<UUID, HoseRenderData> HOSES = new ConcurrentHashMap<>();

	private ClientHoseState() {
	}

	public static void apply(HoseSyncPayload payload) {
		UUID id = payload.id();
		if (payload.removed()) {
			HOSES.remove(id);
			return;
		}
		HOSES.put(id, new HoseRenderData(
			id,
			payload.hoseType(),
			payload.pressurized(),
			payload.deployedLength(),
			payload.pathLength(),
			payload.pointsAsVec3()
		));
	}

	public static void clear() {
		HOSES.clear();
	}

	public static Collection<HoseRenderData> all() {
		return HOSES.values();
	}

	public record HoseRenderData(
		UUID id,
		String type,
		boolean pressurized,
		int deployedLength,
		float pathLength,
		java.util.List<Vec3> points
	) {
	}
}
