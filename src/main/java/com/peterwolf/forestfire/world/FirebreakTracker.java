package com.peterwolf.forestfire.world;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/**
 * Tracks temporary wet lines and permanent firebreak positions.
 */
public final class FirebreakTracker {
	public enum Type {
		CLEARED_GRASS,
		BARE_DIRT,
		TRENCH,
		GRAVEL,
		ROAD,
		WET_LINE
	}

	private final Map<Long, Entry> breaks = new ConcurrentHashMap<>();

	public void mark(BlockPos pos, Type type, int durationTicks) {
		breaks.put(pos.asLong(), new Entry(type, durationTicks));
	}

	public void markWet(BlockPos pos, int durationTicks) {
		mark(pos, Type.WET_LINE, durationTicks);
	}

	public boolean isFirebreak(BlockPos pos) {
		return breaks.containsKey(pos.asLong());
	}

	public void tick() {
		Iterator<Map.Entry<Long, Entry>> it = breaks.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Entry> entry = it.next();
			Entry value = entry.getValue();
			if (value.durationTicks < 0) {
				continue; // permanent
			}
			value.durationTicks--;
			if (value.durationTicks <= 0) {
				it.remove();
			}
		}
	}

	/**
	 * Returns true if fire should not cross from→to.
	 */
	public boolean blocksSpread(BlockPos from, BlockPos to, float windSpeed, float flameHeat) {
		Entry entry = breaks.get(to.asLong());
		if (entry == null) {
			entry = breaks.get(from.asLong());
		}
		if (entry == null) {
			return false;
		}
		float base = switch (entry.type) {
			case CLEARED_GRASS -> 0.55F;
			case BARE_DIRT -> 0.75F;
			case TRENCH -> 0.85F;
			case GRAVEL, ROAD -> 0.9F;
			case WET_LINE -> 0.7F;
		};
		// Strong wind / intense flame can jump narrow breaks
		float jumpChance = (1.0F - base) + windSpeed * 0.25F + flameHeat * 0.15F;
		if (entry.type == Type.WET_LINE && flameHeat > 0.8F && windSpeed > 0.6F) {
			return false; // embers/flames may cross wet line under extreme conditions — handled by caller chance
		}
		return jumpChance < 0.55F;
	}

	private static final class Entry {
		final Type type;
		int durationTicks;

		Entry(Type type, int durationTicks) {
			this.type = type;
			this.durationTicks = durationTicks;
		}
	}
}
