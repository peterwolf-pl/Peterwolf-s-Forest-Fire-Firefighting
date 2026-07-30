package com.peterwolf.forestfire.fire.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Active wildfire incident record — server authority for mission state.
 * Codec is split into nested parts because RecordCodecBuilder.group is capped at 16 fields.
 */
public final class FireIncident {
	public static final String[] NAME_POOL = {
		"Pine Ridge Fire",
		"North Valley Fire",
		"Old Forest Fire",
		"Riverbank Fire",
		"Western Hills Fire",
		"Cedar Hollow Fire",
		"Smoke Creek Fire",
		"Ashwood Fire"
	};

	private static final Codec<CoreData> CORE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("id").forGetter(CoreData::id),
		Codec.STRING.fieldOf("name").forGetter(CoreData::name),
		BlockPos.CODEC.fieldOf("ignition").forGetter(CoreData::ignition),
		Codec.LONG.fieldOf("discoveryTime").forGetter(CoreData::discoveryTime),
		Codec.STRING.fieldOf("status").forGetter(CoreData::status),
		Codec.FLOAT.optionalFieldOf("estimatedArea", 0.0F).forGetter(CoreData::estimatedArea),
		Codec.INT.optionalFieldOf("burningBlocks", 0).forGetter(CoreData::burningBlocks),
		Codec.INT.optionalFieldOf("threatenedStructures", 0).forGetter(CoreData::threatenedStructures),
		Codec.INT.optionalFieldOf("endangeredLives", 0).forGetter(CoreData::endangeredLives),
		Codec.FLOAT.optionalFieldOf("intensity", 0.0F).forGetter(CoreData::intensity),
		Codec.FLOAT.optionalFieldOf("windDir", 0.0F).forGetter(CoreData::windDir),
		Codec.FLOAT.optionalFieldOf("windSpeed", 0.0F).forGetter(CoreData::windSpeed),
		Codec.FLOAT.optionalFieldOf("humidity", 0.5F).forGetter(CoreData::humidity),
		Codec.FLOAT.optionalFieldOf("containment", 0.0F).forGetter(CoreData::containment),
		Codec.FLOAT.optionalFieldOf("waterUsed", 0.0F).forGetter(CoreData::waterUsed),
		Codec.FLOAT.optionalFieldOf("score", 0.0F).forGetter(CoreData::score)
	).apply(instance, CoreData::new));

	private static final Codec<ExtraData> EXTRA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("peakBurning", 0).forGetter(ExtraData::peakBurning),
		Codec.INT.optionalFieldOf("structuresLost", 0).forGetter(ExtraData::structuresLost),
		Codec.INT.optionalFieldOf("livesLost", 0).forGetter(ExtraData::livesLost),
		Codec.INT.optionalFieldOf("hotspots", 0).forGetter(ExtraData::hotspots),
		Codec.STRING.listOf().optionalFieldOf("participants", List.of()).forGetter(ExtraData::participants),
		IncidentMarker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(ExtraData::markers),
		FireSector.CODEC.listOf().optionalFieldOf("sectors", List.of()).forGetter(ExtraData::sectors)
	).apply(instance, ExtraData::new));

	public static final Codec<FireIncident> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		CORE_CODEC.fieldOf("core").forGetter(FireIncident::toCore),
		EXTRA_CODEC.optionalFieldOf("extra", ExtraData.EMPTY).forGetter(FireIncident::toExtra)
	).apply(instance, FireIncident::fromParts));

	public final int id;
	public String name;
	public BlockPos ignitionPos;
	public long discoveryTime;
	public IncidentStatus status;
	public float estimatedFireArea;
	public int burningBlocks;
	public int threatenedStructures;
	public int endangeredLives;
	public float fireIntensity;
	public float windDirection;
	public float windSpeed;
	public float humidity;
	public float containmentPercent;
	public float waterUsed;
	public float incidentScore;
	public int peakBurningBlocks;
	public int structuresLost;
	public int livesLost;
	public int hotspotCount;
	public final Set<UUID> participants = new HashSet<>();
	public final List<IncidentMarker> markers = new ArrayList<>();
	public final List<FireSector> sectors = new ArrayList<>();

	public FireIncident(int id, String name, BlockPos ignitionPos, long discoveryTime) {
		this.id = id;
		this.name = name;
		this.ignitionPos = ignitionPos.immutable();
		this.discoveryTime = discoveryTime;
		this.status = IncidentStatus.REPORTED;
		ensureDefaultSectors();
	}

	private record CoreData(
		int id,
		String name,
		BlockPos ignition,
		long discoveryTime,
		String status,
		float estimatedArea,
		int burningBlocks,
		int threatenedStructures,
		int endangeredLives,
		float intensity,
		float windDir,
		float windSpeed,
		float humidity,
		float containment,
		float waterUsed,
		float score
	) {
	}

	private record ExtraData(
		int peakBurning,
		int structuresLost,
		int livesLost,
		int hotspots,
		List<String> participants,
		List<IncidentMarker> markers,
		List<FireSector> sectors
	) {
		static final ExtraData EMPTY = new ExtraData(0, 0, 0, 0, List.of(), List.of(), List.of());
	}

	private CoreData toCore() {
		return new CoreData(
			id, name, ignitionPos, discoveryTime, status.name(),
			estimatedFireArea, burningBlocks, threatenedStructures, endangeredLives,
			fireIntensity, windDirection, windSpeed, humidity, containmentPercent, waterUsed, incidentScore
		);
	}

	private ExtraData toExtra() {
		List<String> parts = new ArrayList<>(participants.size());
		for (UUID uuid : participants) {
			parts.add(uuid.toString());
		}
		return new ExtraData(peakBurningBlocks, structuresLost, livesLost, hotspotCount, parts, markers, sectors);
	}

	private static FireIncident fromParts(CoreData core, ExtraData extra) {
		FireIncident incident = new FireIncident(core.id, core.name, core.ignition, core.discoveryTime);
		incident.status = parseStatus(core.status);
		incident.estimatedFireArea = core.estimatedArea;
		incident.burningBlocks = core.burningBlocks;
		incident.threatenedStructures = core.threatenedStructures;
		incident.endangeredLives = core.endangeredLives;
		incident.fireIntensity = core.intensity;
		incident.windDirection = core.windDir;
		incident.windSpeed = core.windSpeed;
		incident.humidity = core.humidity;
		incident.containmentPercent = core.containment;
		incident.waterUsed = core.waterUsed;
		incident.incidentScore = core.score;
		incident.peakBurningBlocks = extra.peakBurning;
		incident.structuresLost = extra.structuresLost;
		incident.livesLost = extra.livesLost;
		incident.hotspotCount = extra.hotspots;
		incident.participants.clear();
		for (String raw : extra.participants) {
			try {
				incident.participants.add(UUID.fromString(raw));
			} catch (IllegalArgumentException ignored) {
			}
		}
		incident.markers.clear();
		incident.markers.addAll(extra.markers);
		if (!extra.sectors.isEmpty()) {
			incident.sectors.clear();
			incident.sectors.addAll(extra.sectors);
		}
		return incident;
	}

	private static IncidentStatus parseStatus(String raw) {
		try {
			return IncidentStatus.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return IncidentStatus.ACTIVE;
		}
	}

	public void ensureDefaultSectors() {
		if (!sectors.isEmpty()) {
			return;
		}
		sectors.add(new FireSector("Sector Alpha"));
		sectors.add(new FireSector("Sector Bravo"));
		sectors.add(new FireSector("Sector Charlie"));
		sectors.add(new FireSector("Sector Delta"));
	}

	public void addParticipant(UUID playerId) {
		participants.add(playerId);
	}

	public void recordWater(float units) {
		waterUsed += Math.max(0.0F, units);
	}

	public String summaryLine() {
		return String.format(
			Locale.ROOT,
			"#%d %s [%s] burn=%d area=%.1f cont=%.0f%% score=%.0f @ %s",
			id,
			name,
			status,
			burningBlocks,
			estimatedFireArea,
			containmentPercent,
			incidentScore,
			ignitionPos.toShortString()
		);
	}

	public List<String> infoLines() {
		List<String> lines = new ArrayList<>();
		lines.add("Incident #" + id + " — " + name);
		lines.add("Status: " + status);
		lines.add("Ignition: " + ignitionPos.toShortString());
		lines.add("Burning blocks: " + burningBlocks + " (peak " + peakBurningBlocks + ")");
		lines.add(String.format(Locale.ROOT, "Estimated area: %.1f blocks²", estimatedFireArea));
		lines.add(String.format(Locale.ROOT, "Intensity: %.2f", fireIntensity));
		lines.add(String.format(Locale.ROOT, "Containment: %.1f%%", containmentPercent));
		lines.add(String.format(Locale.ROOT, "Wind: %.2f @ %.0f°", windSpeed, windDirection));
		lines.add(String.format(Locale.ROOT, "Humidity: %.0f%%", humidity * 100.0F));
		lines.add("Threatened structures: " + threatenedStructures);
		lines.add("Endangered lives: " + endangeredLives);
		lines.add("Hotspots: " + hotspotCount);
		lines.add(String.format(Locale.ROOT, "Water used: %.0f", waterUsed));
		lines.add("Firefighters: " + participants.size());
		lines.add(String.format(Locale.ROOT, "Score: %.0f", incidentScore));
		lines.add("Sectors: " + sectors.size() + " | Markers: " + markers.size());
		return lines;
	}
}
