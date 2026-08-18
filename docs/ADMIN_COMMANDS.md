# Administrator Command Guide

All mutating `/fireincident`, `/firetest`, and `/wind` commands require
**gamemaster** permission (op level 2+ by default).

## Incidents

```
/fireincident create
/fireincident create <size>
/fireincident create <x> <y> <z> <size>
/fireincident list
/fireincident info <id>
/fireincident contain <id>
/fireincident control <id>
/fireincident extinguish <id>
/fireincident close <id>
/fireincident remove <id>
/fireincident teleport <id>
```

- `create` — ignites a wildfire and opens an incident (names like *Pine Ridge Fire*)
- `contain` / `control` / `close` — status transitions and scoring
- `extinguish` — forces all cells for that incident out
- `douse [radius]` — extinguish fire and airborne sparks in a sphere around the player (default 16, max 128)
- `remove` — deletes incident and its cells

### Local douse (around player)

```
/fireincident douse
/fireincident douse 32
/fireextinguish 48
/gaspozaru 24
```

Removes simulation heat/flames, vanilla fire blocks, and airborne sparks (origin, flight path or landing inside the sphere) within radius blocks of the player. Leftover sparks will not land and restart the fire.

## Fire danger

```
/firedanger
```

## Wind

```
/wind
/wind status
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

- `/wind` and `/wind status` show the current wind to every player.
- `set speed` / `set direction` immediately change the current dimension.
- `set fixed` sets speed and direction together and switches off automatic drift.
- `mode fixed` captures the current wind; `mode dynamic` resumes gradual changes.
- Yaw uses Minecraft directions: `0=S`, `90=W`, `180=N`, `-90=E`.
- `reload` reads `config/peterwolfs_forestfire.json` without a restart.

## Roles & equipment kit

```
/firefighter roles
/firefighter role
/firefighter role CREW_MEMBER
/firefighter role INCIDENT_COMMANDER

/firefighter kit
/firefighter gear
/firekit
/firesprzet
```

`kit` / `gear` / `firekit` / `firesprzet` — give the full firefighter loadout (armor, tools, pump, hoses, nozzle, tanks, fuel, command post, thermal scanner).

## Testing / profiling

```
/firetest grid
/firetest grid <size>
/firetest stats
/firetest clear
```

Use `grid` to spawn a controlled burn for TPS profiling. Watch `stats` for active cell counts.
