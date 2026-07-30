# Player Firefighting Guide

## Establishing a water supply

1. Find a river, lake, ocean, or place a **Portable Water Tank**.
2. Place the **Portable Fire Pump** on stable ground near the water.
3. Lay **Intake Hose** so it reaches water (or place the pump within ~3 blocks of water).
4. Optional: place **Iron Bars** next to the pump as a stand-in **intake strainer** (reduces debris).
5. Fill the pump with **Pump Fuel Can** (sneak-use / use on pump).
6. **Sneak-use** the pump to start it. Use without sneak to read status.

## Connecting hoses

1. Place **Fire Hose** segments from the pump toward the fire edge.
2. Add a **Hose Splitter** to branch attack lines (use to cycle open lines — more lines = less pressure).
3. Optional: place **Portable Sprinkler** on a pressurised line for structure defence.

## Nozzle patterns

Hold the **Fire Hose Nozzle** within 3 blocks of hose/pump:

| Mode | Use |
|------|-----|
| Straight Stream | Long range, distant flames |
| Narrow Fog | Trees and structures, strong cooling |
| Wide Fog | Short range defence, wet lines, radiant heat shield |
| Shutoff | Stop flow, preserve pressure |

- **Hold use** to spray
- **Sneak + use** to change mode

Water reduces heat and increases moisture. A block may look extinguished while still hot — keep cooling.

## Firebreaks

- **Fire Rake** — clear grass/leaves surface fuel
- **Fire Shovel** — bare dirt / smother small flames
- **Pulaski** — trench / expose roots and hotspots
- **Fire Axe** — access points, burning wood, doors
- **Wet line** — spray unburned fuel ahead of the fire with wide fog

Strong wind can carry embers over narrow breaks. Make breaks wider uphill and on the downwind edge.

## Detecting hotspots

1. After main flames drop, switch to overhaul.
2. Use the **Thermal Scanner** on charred logs, roots, walls and leaf piles.
3. Readings: COLD / WARM / HOT / CRITICAL
4. Dig (Pulaski/shovel) and flood HOT/CRITICAL spots.

## Containment vs control

- **Contained** — fire is not spreading; perimeter held
- **Controlled** — visible fire out; hotspots managed
- **Closed** — overhaul complete; mission scored

Commanders declare status via Command Post workflow and `/fireincident contain|control|close`.

## Wind shifts

Use **Firefighter Information** item or `/firedanger`:

```
Fire danger: EXTREME
Humidity: 18%
Wind: 22 blocks/SE
Recent rainfall: none
```

When wind shifts:

1. Re-check the downwind edge first
2. Move pumps/hoses if the fire is flanking
3. Widen firebreaks on the new head fire side
4. Expect more spot fires from embers

## Roles (optional, not restrictive)

| Role | Focus |
|------|-------|
| Incident Commander | Command post, sectors, containment calls |
| Pump Operator | Pump, fuel, intake, pressure |
| Hose Operator | Attack lines and nozzle work |
| Crew Member | Firebreaks, hose extension, overhaul |
| Scout | Edges, spot fires, escape routes |

Set with `/firefighter role <name>`.

## Safety

Wear firefighter gear near intense fires. Use **Breathing Mask** + **Air Tank** in dense smoke. Wide fog reduces radiant heat temporarily. Gear reduces risk — it does not make you immune.
