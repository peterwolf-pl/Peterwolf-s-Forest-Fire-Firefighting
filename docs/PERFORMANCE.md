# Performance Tuning Guide

Wildfires can be expensive. This mod uses:

- Cap on burning cells (`maxBurningBlocksPerWorld`)
- Tick interval (`fireTickInterval`)
- Spread/ember budgets per tick
- No persistent smoke entities (particles only)
- Hose networks as blocks + BFS, not per-segment entities
- Chunk-aware skipping of unloaded cells

## Recommended dedicated-server settings

Small community server:

```json
"maxBurningBlocksPerWorld": 4000,
"fireTickInterval": 5,
"maxSpreadChecksPerTick": 250,
"maxEmbersPerTick": 4,
"maxActiveIncidents": 4
```

Event / training server:

```json
"maxBurningBlocksPerWorld": 8000,
"fireTickInterval": 4,
"maxSpreadChecksPerTick": 400,
"maxEmbersPerTick": 8
```

## Profiling

1. `/firetest grid 12`
2. Watch MSPT / TPS
3. `/firetest stats` for cell count
4. `/firetest clear` when finished

## Tips

- Prefer several medium incidents over one unbounded mega-fire
- Peat fire stays **disabled** by default
- Lower `maxSmokeParticles` on low-end clients (client particle load)
