# Player Firefighting Guide

## Establishing a water supply

1. Find a river, lake, ocean, or place a **Portable Water Tank**.
2. Place the **Portable Fire Pump** on solid ground (next to water is easiest).
3. **Water intake** (one of these):
   - put the pump within ~3 blocks of water, **or**
   - lay **Intake Hose** from the pump into the water.
4. **Intake Strainer** (recommended): place the strainer **in the water** (or next to the intake hose end / pump). It is a real block — right-click water/ground to place it.
5. Fuel: right-click the pump with a **Pump Fuel Can**.
6. **Start the pump: right-click the pump** (empty hand). Wait ~2 seconds until status says RUNNING.  
   - Right-click again = OFF.  
   - **Sneak + right-click** = status only (does not toggle).

## Connecting hoses

1. Place **Fire Hose** segments from the pump toward the fire edge.
2. Add a **Hose Splitter** to branch attack lines (use to cycle open lines — more lines = less pressure).
3. Optional: place **Portable Sprinkler** on a pressurised line for structure defence.

## Nozzle (handheld + ground)

### Connect

1. Lay **Fire Hose** from the pump (can connect before or after starting).
2. With a free **Fire Hose Nozzle**, do **either**:
   - **Right-click the Fire Hose block**, or
   - **Right-click the ground/top of a block next to the hose** (within 8 blocks of hose).
3. A **ground nozzle** block appears and is connected.

### Pick up / place

- **Right-click** the ground nozzle (empty hand) → pick up into main hand.
- **Sneak + right-click** ground while holding → place it again (water closes).
- Do **not** drop (Q) a connected nozzle — it will refuse; place it or disconnect.

### Spray

| Control | Action |
|---------|--------|
| Hold right-click | Open valve / spray |
| Release | Close valve |
| Sneak + right-click (air) | Cycle mode |

| Mode | Use |
|------|-----|
| Straight Stream | Long range, distant flames |
| Narrow Fog | Trees and structures, strong cooling |
| Wide Fog | Short range defence, wet lines, radiant heat shield |
| Shutoff | Stop flow, preserve pressure |

You can only walk about **16 blocks** past the last hose segment (`nozzleFreeHoseBlocks`). HUD shows hose tension.

### Disconnect

Right-click the **hose coupling** with the connected nozzle while the valve is **closed**.

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
