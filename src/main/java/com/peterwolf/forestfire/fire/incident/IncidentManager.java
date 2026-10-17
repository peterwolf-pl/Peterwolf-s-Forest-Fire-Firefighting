package com.peterwolf.forestfire.fire.incident;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import com.peterwolf.forestfire.mission.MissionScorer;
import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.ModNetworking;
import com.peterwolf.forestfire.network.WindSyncPayload;
import com.peterwolf.forestfire.world.FireSavedData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Server-side incident lifecycle manager. Backed by {@link FireSavedData}.
 */
public final class IncidentManager {
	private static final Map<ServerLevel, IncidentManager> CACHE = new HashMap<>();

	private final ServerLevel level;
	private final Map<Integer, FireIncident> incidents = new HashMap<>();
	private int nextId = 1;
	private final WindSystem wind = new WindSystem();
	private boolean dirty;

	private IncidentManager(ServerLevel level) {
		this.level = level;
	}

	public static IncidentManager get(ServerLevel level) {
		return CACHE.computeIfAbsent(level, key -> {
			IncidentManager manager = new IncidentManager(key);
			manager.load();
			return manager;
		});
	}

	public static void invalidate(ServerLevel level) {
		CACHE.remove(level);
	}

	public static void clearAll() {
		CACHE.clear();
	}

	public WindSystem wind() {
		return wind;
	}

	public Collection<FireIncident> all() {
		return incidents.values();
	}

	public List<FireIncident> openIncidents() {
		List<FireIncident> list = new ArrayList<>();
		for (FireIncident incident : incidents.values()) {
			if (incident.status.isOpen()) {
				list.add(incident);
			}
		}
		list.sort(Comparator.comparingInt(i -> i.id));
		return list;
	}

	public Optional<FireIncident> get(int id) {
		return Optional.ofNullable(incidents.get(id));
	}

	public FireIncident createAt(BlockPos pos, int size) {
		ForestFireConfig.Data cfg = ForestFireConfig.get();
		int open = 0;
		for (FireIncident incident : incidents.values()) {
			if (incident.status.isOpen()) {
				open++;
			}
		}
		if (open >= cfg.maxActiveIncidents) {
			throw new IllegalStateException("Maximum active incidents reached (" + cfg.maxActiveIncidents + ")");
		}

		int id = nextId++;
		String name = FireIncident.NAME_POOL[level.getRandom().nextInt(FireIncident.NAME_POOL.length)];
		FireIncident incident = new FireIncident(id, name, pos, level.getGameTime());
		incident.status = IncidentStatus.REPORTED;
		incident.humidity = WeatherFireModel.humidity01(level, pos);
		incident.windDirection = wind.yawDegrees();
		incident.windSpeed = wind.speed();
		incidents.put(id, incident);
		dirty = true;

		FireSimulation sim = FireSimulation.get(level);
		int ignited = sim.igniteArea(pos, Math.max(1, size), id);
		incident.burningBlocks = ignited;
		incident.peakBurningBlocks = ignited;
		incident.estimatedFireArea = Math.max(ignited, size * size);
		incident.fireIntensity = Math.min(1.0F, 0.35F + ignited / 80.0F);
		if (ignited > 0) {
			incident.status = IncidentStatus.ACTIVE;
		} else {
			// Still register incident so admins can see a failed ignition (e.g. desert)
			incident.status = IncidentStatus.REPORTED;
		}
		alertNearbyPlayers(incident);
		markDirty();
		return incident;
	}

	public boolean remove(int id) {
		FireIncident removed = incidents.remove(id);
		if (removed == null) {
			return false;
		}
		FireSimulation.get(level).clearIncident(id);
		markDirty();
		return true;
	}

	public boolean setStatus(int id, IncidentStatus status) {
		FireIncident incident = incidents.get(id);
		if (incident == null) {
			return false;
		}
		incident.status = status;
		if (status == IncidentStatus.CLOSED || status == IncidentStatus.CONTROLLED) {
			incident.incidentScore = MissionScorer.score(incident);
		}
		if (status == IncidentStatus.CLOSED) {
			// Keep residual hotspot tracking until overhaul complete — do not wipe cells immediately
		}
		markDirty();
		return true;
	}

	public void extinguishAll(int id) {
		FireIncident incident = incidents.get(id);
		if (incident == null) {
			return;
		}
		FireSimulation.get(level).extinguishIncident(id);
		incident.burningBlocks = 0;
		incident.hotspotCount = 0;
		incident.containmentPercent = 100.0F;
		incident.status = IncidentStatus.CONTROLLED;
		incident.incidentScore = MissionScorer.score(incident);
		markDirty();
	}

	public void tick() {
		wind.tick(level);
		FireSimulation sim = FireSimulation.get(level);
		sim.tick(this);

		for (FireIncident incident : incidents.values()) {
			if (!incident.status.isOpen()) {
				continue;
			}
			sim.updateIncidentStats(incident);
			incident.windDirection = wind.yawDegrees();
			incident.humidity = WeatherFireModel.humidity01(level, incident.ignitionPos);
			var storm = sim.firestorm();
			incident.windSpeed = storm.active && storm.incidentId == incident.id
				? wind.firestormSpeed()
				: wind.speed();
			if (storm.active && storm.incidentId == incident.id) {
				// Intensity readout for command post / scoring context
				incident.fireIntensity = Math.max(incident.fireIntensity, 0.55F + storm.intensity * 0.45F);
			}

			// Auto state transitions
			if (incident.burningBlocks == 0 && incident.hotspotCount == 0
				&& incident.status == IncidentStatus.ACTIVE) {
				incident.status = IncidentStatus.CONTAINED;
				incident.containmentPercent = 100.0F;
			} else if (incident.burningBlocks == 0 && incident.hotspotCount > 0
				&& (incident.status == IncidentStatus.ACTIVE || incident.status == IncidentStatus.CONTAINED)) {
				incident.status = IncidentStatus.OVERHAUL;
			}

			if (incident.burningBlocks > ForestFireConfig.get().maxBurningBlocksPerWorld * 0.9F
				&& incident.status != IncidentStatus.ESCAPED) {
				incident.status = IncidentStatus.ESCAPED;
			}

			// Participation: players near fire
			AABB box = new AABB(incident.ignitionPos).inflate(96.0);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
				incident.addParticipant(player.getUUID());
			}
		}

		// Compact HUD sync every second
		if (level.getGameTime() % 20L == 0L) {
			syncHudToPlayers();
		}

		if (dirty) {
			save();
			dirty = false;
		} else if (level.getGameTime() % 200L == 0L) {
			save();
		}
	}

	private void syncHudToPlayers() {
		FireIncident primary = null;
		for (FireIncident incident : openIncidents()) {
			primary = incident;
			break;
		}
		for (ServerPlayer player : level.players()) {
			ModNetworking.sendWind(player, new WindSyncPayload(wind.speed(), wind.yawDegrees()));
			if (primary != null) {
				var storm = FireSimulation.get(level).firestorm();
				boolean stormAffectsPrimary = storm.active && storm.incidentId == primary.id;
				String nozzleHint = stormAffectsPrimary
					? String.format("FIRESTORM x%.1f", storm.spreadMultiplier)
					: "";
				ModNetworking.sendHud(player, new IncidentHudPayload(
					primary.id,
					primary.name,
					primary.status.name(),
					primary.burningBlocks,
					primary.containmentPercent,
					primary.windSpeed,
					primary.windDirection,
					stormAffectsPrimary ? storm.intensity : 0.0F,
					nozzleHint
				));
			}
		}
	}

	public void markDirty() {
		dirty = true;
	}

	public void load() {
		FireSavedData data = FireSavedData.get(level);
		incidents.clear();
		nextId = 1;
		for (FireIncident incident : data.incidents()) {
			incidents.put(incident.id, incident);
			nextId = Math.max(nextId, incident.id + 1);
		}
		wind.load(data.windSpeed(), data.windYaw());
		FireSimulation.get(level).loadCells(data.cells());
	}

	public void save() {
		FireSavedData data = FireSavedData.get(level);
		data.replaceIncidents(new ArrayList<>(incidents.values()));
		// Fire-driven boost is transient and must not become ambient wind after reload.
		data.setWind(wind.baseSpeed(), wind.yawDegrees());
		data.replaceCells(FireSimulation.get(level).exportCells());
		data.setDirty();
	}

	private void alertNearbyPlayers(FireIncident incident) {
		AABB box = new AABB(incident.ignitionPos).inflate(256.0);
		Component message = Component.translatable(
			"message.peterwolfs_forestfire.incident_alert",
			incident.name,
			incident.ignitionPos.getX(),
			incident.ignitionPos.getY(),
			incident.ignitionPos.getZ()
		);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
			player.sendSystemMessage(message);
			incident.addParticipant(player.getUUID());
		}
		// Also broadcast to all players on the dimension as a soft alert
		for (ServerPlayer player : level.players()) {
			if (!box.contains(player.position())) {
				player.sendOverlayMessage(message);
			}
		}
	}
}
