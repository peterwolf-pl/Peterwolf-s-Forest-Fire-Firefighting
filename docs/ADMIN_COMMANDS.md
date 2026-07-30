# Administrator Command Guide

All `/fireincident` and `/firetest` commands require **gamemaster** permission (op level 2+ by default).

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
- `remove` — deletes incident and its cells

## Fire danger

```
/firedanger
```

## Roles

```
/firefighter roles
/firefighter role
/firefighter role CREW_MEMBER
/firefighter role INCIDENT_COMMANDER
```

## Testing / profiling

```
/firetest grid
/firetest grid <size>
/firetest stats
/firetest clear
```

Use `grid` to spawn a controlled burn for TPS profiling. Watch `stats` for active cell counts.
