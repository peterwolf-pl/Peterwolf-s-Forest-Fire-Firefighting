# API Documentation

Package: `com.peterwolf.forestfire.api.ForestFireApi`

Server-side helpers for other mods:

```java
Collection<FireIncident> open = ForestFireApi.getOpenIncidents(level);
Optional<FireIncident> inc = ForestFireApi.getIncident(level, id);
FireIncident created = ForestFireApi.createIncident(level, pos, size);
boolean cooled = ForestFireApi.applyWater(level, pos, 1.0F, 2.0F);
FireDangerLevel danger = ForestFireApi.getFireDanger(level, pos);
int cells = ForestFireApi.getActiveFireCellCount(level);
```

## Notes

- Simulation is **server-authoritative**
- Fire cells are compact in-memory maps, persisted via world SavedData
- Prefer the API package over internal packages for cross-mod integration

## Networking

Clientbound payloads (Fabric networking):

- `peterwolfs_forestfire:incident_hud`
- `peterwolfs_forestfire:wind_sync`

## TODO (optional future API)

- Event bus for incident status changes
- Fluid tank capability bridge for modded tanks
- Shader smoke density hooks
