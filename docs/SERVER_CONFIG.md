# Server Configuration Guide

Config path: `config/peterwolfs_forestfire.json`

The file is created with defaults on first launch.

## Wind

| Key | Default | Notes |
|-----|---------|-------|
| `windEnabled` | true | Global wind simulation |
| `minimumWindSpeed` | 0.0 | |
| `maximumWindSpeed` | 1.0 | |
| `windChangeIntervalTicks` | 6000 | ~5 minutes |
| `windChangeStrength` | 0.15 | Gradual drift |

## Performance

| Key | Default | Notes |
|-----|---------|-------|
| `maxBurningBlocksPerWorld` | 8000 | Hard cap |
| `fireTickInterval` | 5 | Simulate every N ticks |
| `maxSpreadChecksPerTick` | 400 | Spread budget |
| `maxEmbersPerTick` | 8 | Spot-fire budget |
| `maxActiveIncidents` | 8 | |

## Water / hose

| Key | Default |
|-----|---------|
| `maxHoseLength` | 48 |
| `pressureLossPerSegment` | 0.02 |
| `wetnessDurationTicks` | 2400 |
| tank capacities | 2000 / 6000 / 16000 |

## Smoke & heat

| Key | Default |
|-----|---------|
| `smokeEnabled` | true |
| `smokeDamagePerSecond` | 0.5 |
| `heatExposureEnabled` | true |

## Advanced

- `peatFireEnabled` — **false** by default (underground fire)
- `treeCollapseEnabled` — true
- `rescueMissionsEnabled` — true

Reload: restart the server (or restart the world) after editing JSON. Runtime `/fireincident` does not re-read config yet — use restart for config changes.
