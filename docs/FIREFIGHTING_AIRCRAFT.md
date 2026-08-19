# Firefighting Water Bomber — Architecture & Implementation Plan

## 1. Architecture analysis

### Peterwolf's Planes

| Area | Finding |
|------|---------|
| Base class | `PlaneEntity` owns full flight physics, pilot input, damage, combat, passengers |
| Variants | Subclasses (`LargePlaneEntity`, `WaterPlaneEntity`, …) override drop item / size / water taxi |
| Physics hooks | `getThrustForce()`, `getBaseDrag()`, `getSpeedDrag()`, bank/yaw/pitch limits are already `protected` |
| Input | Client keys → `PlaneInputPayload` (throttle, rudder, combat, guns, bomb) → server `applyCombatInput` |
| Defaults | **V** combat arm, **B** bomb, **H** paraglider lift HUD |
| HUD | Client-only overlay on `PlaneEntity` |
| Fuel | **Not implemented** — no fuel system to extend |
| Extension API | **None** before this work — specialized aircraft must subclass + small hooks |

### Forest Fire

| Area | Finding |
|------|---------|
| Suppression | `ForestFireApi.applyWater` / `FireSimulation.applyWater` cool heat, add moisture, wet lines |
| Wetness | `FirebreakTracker.markWet` + `BurnStage.WET` |
| Wind | `WindSystem` (yaw + speed) available for drop drift |
| Config | JSON `ForestFireConfig.Data` |
| Soft deps | Pattern: load only when optional mod present (tree felling style) |
| Roadmap | Explicit “Aircraft / air support role” |

### Integration strategy

1. **Planes** gains a small documented specialized-aircraft API (cargo mass + control profile).
2. **Forest Fire** owns the firefighting aircraft as an optional compat module (`compat/planes/…`).
3. Entity **extends** `LargePlaneEntity` so all flight/networking/camera/physics stay in Planes.
4. Firefighting actions use a **separate C2S payload** (not combat V/B), server-validated.
5. When piloting the water bomber, combat V/B are disabled; V/B/H drive drop/hose systems.
6. Water drops are **lightweight server payloads** (not thousands of water blocks); suppression calls `applyWater`.

### Soft dependency (product rule)

**Neither mod requires the other.**

| Installed | Result |
|-----------|--------|
| Only Planes | Normal aircraft; no Forest Fire types |
| Only Forest Fire | Full firefighting; water bomber **not** registered |
| **Both** | Firefighting Water Bomber available |

Implementation:

- `fabric.mod.json`: `peterwolfs_planes` under **`suggests` only** (never hard `depends`)
- Runtime: `FabricLoader.isModLoaded("peterwolfs_planes")` then reflective `PlanesCompat.init()`
- Entrypoints never statically import Planes types (avoids class-load crash when Planes is missing)

## 2. Files added / modified

### Planes (minimal)

| Path | Change |
|------|--------|
| `api/SpecializedPlaneControls.java` | **New** — combat vs specialized control profile |
| `api/PlaneCargoMass.java` | **New** — variable cargo mass factor |
| `entity/PlaneEntity.java` | Cargo mass affects thrust/drag; optional interface checks |
| `client/PeterwolfsPlanesClient.java` | Skip combat keys/HUD weapons when plane opts out |
| `docs/SPECIALIZED_AIRCRAFT_API.md` | **New** — API docs |

### Forest Fire

| Path | Change |
|------|--------|
| `compat/planes/PlanesCompat.java` | Soft bootstrap when Planes is loaded |
| `aircraft/firefighting/FirefightingPlaneEntity.java` | Entity + tank + intake + release |
| `aircraft/firefighting/FirefightingPlaneItem.java` | Placeable item |
| `component/water_tank/AircraftWaterTank.java` | Tank state helper |
| `network/firefighting/FireplaneActionPayload.java` | C2S actions |
| `simulation/water_drop/WaterDropSimulator.java` | Aerial payloads + budget |
| `simulation/water_drop/AerialWaterPayload.java` | Single drop corridor element |
| `command/FireplaneCommands.java` | `/fireplane …` |
| `config/ForestFireConfig.java` | `firefightingAircraft` section |
| `client/…` | Keys, HUD, model, renderer |
| Resources | lang, recipe, item model, texture, tag, sounds |
| `src/fireSpreadTest/.../FirefightingAircraftLogicTest.java` | Unit tests |

## 3. Integration risks

| Risk | Mitigation |
|------|------------|
| V/B/H conflict with combat / lift HUD | Disable combat on this entity; FF keys only while piloting it; all rebindable |
| Class loading without Planes | Reflective bootstrap; no hard `depends` on planes |
| Performance during big fires | Raycast scoop; bounded suppression ops/tick; no water source spam |
| Tank desync / dupes | Server authority; synched data only for HUD/render; rate-limit actions |
| Physics discontinuity when dumping | Gradual tank drain → smooth cargo mass factor |
| Missing models/sounds | Procedural model from LargePlane + vanilla particle/sound fallbacks |
| No fuel system in Planes | Documented limitation; weight effect only |

## 4. Milestones

1. **M1** — Planes API + cargo mass + combat opt-out  
2. **M2** — Entity, tank, config, network, item registration  
3. **M3** — Scooping (≤3 blocks from nozzle), release corridor, `applyWater`  
4. **M4** — Client keys, HUD, model animations  
5. **M5** — Commands, tests, full build  

Subsystem damage is **off by default** (config flag) for first stable version.

## Implementation status (2026-08)

| Milestone | Status |
|-----------|--------|
| M1 Planes specialized API + cargo mass | Done |
| M2 Entity, tank, config, network, item | Done |
| M3 Scooping + aerial suppression | Done |
| M4 Client keys, HUD, renderer | Done (model reuses large biplane mesh) |
| M5 Commands + logic tests + build | Done |

### How to test in-game

1. Install **both** `peterwolfs-planes` and `peterwolfs-forest-fire`.
2. Creative / craft: **Firefighting Water Bomber**.
3. Place, board, fly with normal Planes controls (WASD throttle/rudder + look).
4. **V** arm drop · hold **B** release water · **H** scoop hose.
5. Scoop: deploy hose (H), low pass so the nozzle is **in the water** (not high above).
6. Ops: `/fireplane tank fill`, `/fireplane hose deploy`, `/fireplane debug`.

### Config

`config/peterwolfs_forestfire.json` → `firefightingAircraft` section.
