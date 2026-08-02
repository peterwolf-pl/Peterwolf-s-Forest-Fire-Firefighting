# Automatic Hose Deployment

Player places pump, intake endpoint (via connector), and nozzle. The mod generates intake and attack hose paths automatically — no segment-by-segment placement required.

## Gameplay

### Hose roll → pump (primary)

1. Hold a **Hose Roll**
2. Right-click the **Portable Pump**
   - **back face** = intake
   - **front face** = attack output
3. Then right-click **water** (intake) or use **nozzle on pump** (attack)


1. Place **Portable Fire Pump** near water.
2. Hold **Hose Connector Tool** → right-click pump **back face** (intake) → right-click water / tank / strainer.
3. Intake hose appears; pump recognises the source.
4. Hold **Fire Hose Nozzle** → right-click pump (output) → attack hose + connected nozzle in hand.
5. Right-click nozzle once to start continuous flow; right-click again to stop.
6. Carry **Hose Rolls** (required) (16 / 32 / 64). Deployment consumes path length.
7. Sneak + connector on pump disconnects all lines and returns hose material.

## Items

| Item | Role |
|------|------|
| Hose Connector Tool | Select pump port, connect water / nozzle / splitter, disconnect |
| Hose Roll Small/Standard/Large | Deployable length inventory |
| Hose Anchor | Optional route correction control point |
| Fire Hose / Intake Hose (legacy) | Still work as 1-block material or manual layouts |

## Commands

- `/firekit` — full kit including rolls + connector
- `/firetest hosekit` — minimal auto-hose test kit
- `/firetest hoses` — list automatic connections

## Config (`config/peterwolfs_forestfire.json`)

```json
{
  "automaticHoseConsumesItems": true,
  "creativeModeInfiniteHose": true,
  "automaticHoseRetraction": true,
  "returnFullHoseLength": true,
  "hoseDamageLossEnabled": false,
  "maximumIntakeHoseLength": 24,
  "maximumIntakeVerticalLift": 6,
  "intakeLengthPressureLoss": true
}
```

## Architecture

Server-authoritative `HoseConnectionManager` SavedData stores paths as control points (no per-metre blocks). Client renders polylines via `HoseSyncPayload`.

Manual `fire_hose` / `intake_hose` blocks have been **removed**. Use hose rolls + automatic routing only.
