# Server Configuration Guide

Config path: `config/peterwolfs_forestfire.json`

The file is created with defaults on first launch.

## Wind

| Key | Default | Notes |
|-----|---------|-------|
| `windEnabled` | true | Global wind simulation |
| `dynamicWind` | true | Gradual weather changes; false holds the configured wind |
| `configuredWindSpeed` | 0.25 | Initial speed and the speed used by fixed mode |
| `configuredWindYawDegrees` | 0.0 | Minecraft yaw: 0=S, 90=W, 180=N, -90=E |
| `minimumWindSpeed` | 0.0 | Lower bound for dynamic and command-set wind |
| `maximumWindSpeed` | 1.0 | Upper bound for dynamic and command-set wind |
| `windChangeIntervalTicks` | 6000 | ~5 minutes |
| `windChangeStrength` | 0.15 | Maximum speed change per weather update |
| `windDirectionChangeDegrees` | 21.0 | Maximum turn per weather update |
| `windSmoothingFactor` | 0.02 | Per-tick transition speed, 0.001–1.0 |

Wind speed is a gameplay scale: `0.0` is calm and `1.0` is strong ambient
wind. Firestorms may add a local boost up to `firestormWindBoostMax`.

Runtime controls:

```text
/wind
/wind set speed <0..4>
/wind set direction <-180..180>
/wind set fixed <speed> <degrees>
/wind mode dynamic
/wind mode fixed
/wind enable
/wind disable
/wind reset
/wind reload
```

Reading `/wind` is available to players. Changing or reloading wind requires
gamemaster permission. In dynamic mode, `set speed` and `set direction` change
the current world's wind until later weather changes it. Fixed mode writes its
speed/direction back to the JSON config.

## Performance

| Key | Default | Notes |
|-----|---------|-------|
| `maxBurningBlocksPerWorld` | 8000 | Hard cap |
| `fireTickInterval` | 5 | Simulate every N ticks |
| `maxSpreadChecksPerTick` | 400 | Spread budget |
| `maxEmbersPerTick` | 12 | Sparks spawned per sim tick budget |
| `maxActiveIncidents` | 8 | |
| `chunkUnloadRetentionRadius` | 2 | Temporary loaded-chunk buffer around active fire; 0 disables |
| `maxFireChunkTickets` | 128 | Hard cap on coalesced active-fire ticket centres |

Active-fire tickets are temporary and non-persistent. They prevent an artificial
straight fire edge at a loaded-chunk boundary, overlap where possible, and are
removed after the front cools or the world closes.

## Firestorm (large fire blow-up)

When one **compact cluster reaches ≥ 20 flaming blocks** (configurable), that
incident becomes a self-feeding heat column. Separate small fires are not added
together, and the configured maximum is a hard multiplier cap.

| Key | Default | Notes |
|-----|---------|-------|
| `firestormEnabled` | true | Master switch |
| `firestormMinBurningBlocks` | 20 | Threshold to activate |
| `firestormSpreadMultiplierMax` | 2.8 | Max spread speed scale |
| `firestormThresholdSpreadBonus` | 0.35 | Immediate bump at threshold |
| `firestormWindBoostMax` | 0.65 | Extra wind from convection |
| `firestormThresholdWindBonus` | 0.12 | Immediate wind bump |
| `firestormSparkMultiplier` | 2.2 | More airborne sparks |
| `firestormSelfHeat` | 0.8 | Mass of fire heats itself |

HUD shows `FIRESTORM x…` when active. Wind readout includes fire-driven boost.

## Sparks (heat-lifted plant fragments)

| Key | Default | Notes |
|-----|---------|-------|
| `sparksEnabled` | true | Glowing litter lifted by convection |
| `sparkPoolMultiplier` | 10 | Max concurrent sparks ≈ embers/tick × this |
| `emberSpotFireChance` | 0.12 | Base chance a landing spark ignites fuel |
| `sparkHeatBonus` | 0.55 | Extra spawn chance from cell heat |

Sparks rise on heat columns, drift with wind, then fall ahead of the fire front (spot fires). Rain strongly reduces launches and ignition.

Direct spread uses continuous preheating rather than a minimum random ignition
chance: head fire runs fastest, backing fire remains possible, uphill fuel
preheats faster, wet fuel must dry, and crown flame only crosses a one-block
canopy gap. Larger gaps require airborne sparks.

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

Reload: use `/wind reload` after editing JSON. It re-reads the complete config;
wind takes its configured speed and direction immediately.
