# Hose Nozzle System — Architecture & Migration

## Behaviour summary

| State | How | Spray |
|-------|-----|-------|
| **Disconnected** item | Crafted / kit | Cannot spray |
| **Ground** block | Shift-place near hose, or use free nozzle on hose | Never auto-sprays |
| **Held** connected | Right-click ground nozzle empty-handed | Hold use to spray |

### Controls

- **Hold right-click**: open valve / spray (connected only)
- **Release right-click**: close valve
- **Sneak + right-click (air)**: cycle Shutoff → Straight → Narrow Fog → Wide Fog
- **Sneak + right-click (ground)**: place connected nozzle
- **Right-click ground nozzle (empty hand)**: pick up
- **Sneak + right-click ground nozzle**: inspect status
- **Right-click hose with connected nozzle**: disconnect (valve must be closed)
- **Drop (Q)**: blocked for connected nozzles; places at feet if valve closed

### Connection model

Server `HoseEndpointManager` (SavedData `hose_endpoints`) owns:

- endpoint UUID
- hose anchor position
- endpoint position
- location (GROUND / HELD / DISCONNECTED)
- mode, valve, operator UUID
- hose length / max reach / tension / pressure cache

Item NBT only stores `nozzle_endpoint_id` + mode ordinal — never the full network.

### Hose reach

`maxReach = maxHoseLength - distanceToPump + nozzleFreeHoseBlocks` (default free = 16).

Tension: SLACK → NORMAL → TIGHT → MAXIMUM with movement resistance (no hard teleport).

### Migration from old nozzle

Old behaviour: free item sprayed if any hose was within 3 blocks.

New behaviour: must **connect** to the hose network first (place near hose). Existing free nozzles in inventories keep working as free items until connected.

## Known limitations

- Hose-to-hand visual cable is not yet a client renderer (logic + HUD only)
- Dedicated hose-wrench item not added (disconnect via hose interaction)
- Keybind for mode cycle uses sneak+use (configurable keybind optional next)
