# Peterwolf's Forest Fire & Firefighting

**Minecraft Java 26.2 · Fabric Loader · Fabric API · Dedicated server ready**

Cooperative wildfire and firefighting simulator for multiplayer.

> Dedicated to all firefighters who risk their lives protecting forests, wildlife and communities.

This mod does **not** use real firefighting agency logos, protected insignia, or copyrighted branding.

## Features (1.0.0 playable core)

1. **Realistic long-burning fuel** — grass, leaves, logs and structures heat, ignite, flame, smoulder and form hotspots
2. **Wind-driven spread** — gradual wind system affects surface fire, crown fire and embers
3. **Portable fire pump** — fuel, intake, pressure, overload/overheat states
4. **Hose network** — intake hose, attack hose, splitter, pressure loss
5. **Fire hose nozzle** — straight stream / narrow fog / wide fog / shutoff
6. **Heat & wetness** — water cools heat and adds moisture; wet lines resist reignition
7. **Firebreaks** — rake, shovel, Pulaski and wet lines reduce spread
8. **Hotspots & overhaul** — thermal scanner; incidents stay open until cooled
9. **Multiplayer incidents** — server-authoritative incident manager + persistence
10. **Command post & mission scoring** — containment, structures, lives, teamwork

## Repository

https://github.com/peterwolf-pl/Peterwolf-s-Forest-Fire-Firefighting

## Install

1. Install Minecraft **26.2** with **Fabric Loader ≥ 0.19.3**
2. Install **Fabric API** `0.153.0+26.2` (or compatible)
3. Drop the mod JAR into `mods/`
4. For dedicated servers, install the same JAR on the server (client is only needed for HUD/dedication/particles)

## Quick start (multiplayer)

1. `/fireincident create 4` near a forest
2. Place a **Portable Fire Pump** next to water
3. Place **Intake Hose** from water to the pump (or stand the pump near water)
4. Place **Fire Hose** from the pump toward the fire
5. Hold the **Fire Hose Nozzle** near the hose, hold use to spray (sneak-use cycles modes)
6. Use tools to cut firebreaks; place a **Command Post** (sneak-use to bind incident)
7. After flames die, use the **Thermal Scanner** and cool hotspots
8. `/fireincident control <id>` then `/fireincident close <id>`

## Commands

See [docs/ADMIN_COMMANDS.md](docs/ADMIN_COMMANDS.md).

## Configuration

`config/peterwolfs_forestfire.json` — wind, max burning blocks, hose length, wetness, smoke, scoring weights.

See [docs/SERVER_CONFIG.md](docs/SERVER_CONFIG.md) and [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Documentation

| Doc | Description |
|-----|-------------|
| [docs/PLAYER_GUIDE.md](docs/PLAYER_GUIDE.md) | How to fight fires |
| [docs/ADMIN_COMMANDS.md](docs/ADMIN_COMMANDS.md) | Admin / OP commands |
| [docs/SERVER_CONFIG.md](docs/SERVER_CONFIG.md) | Server configuration |
| [docs/API.md](docs/API.md) | Integration API |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Performance tuning |
| [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) | Compatibility notes |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | Known limitations |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Development roadmap |
| [docs/CREDITS.md](docs/CREDITS.md) | Credits |

## Build

```bash
./gradlew build
```

Requires **Java 25** (Minecraft 26.2 toolchain).

## Licence

MIT — see [LICENSE](LICENSE).

## Dedication

*Dedicated to all firefighters who risk their lives protecting forests, wildlife and communities.*
