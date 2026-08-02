# Modrinth Page: Peterwolf's Forest Fire & Firefighting

---

## Summary (max ~140 characters)

**EN:**
> Cooperative wildfire sim: wind-driven fire, pumps, hoses, tools, air tanker (with Planes). Multiplayer-ready.

**PL:**
> Kooperacyjny symulator pożarów: wiatr, pompy, węże, narzędzia, samolot gaśniczy (z Planes). Multiplayer.

---

## Description (paste into Modrinth)

```markdown
# 🔥 Peterwolf's Forest Fire & Firefighting

**Cooperative wildfire and firefighting simulator** for Minecraft — realistic fuel, heat, moisture, wind and multiplayer incident command.

> *Dedicated to all firefighters who risk their lives protecting forests, wildlife and communities.*

This mod does **not** use real firefighting agency logos, protected insignia, or copyrighted branding.

---

## ✨ Features

### Wildfire simulation
- Long-burning **fuel** (grass, leaves, logs, structures) with heat, moisture and staged burn
- **Wind-driven** surface & crown spread, embers / sparks, optional firestorm behaviour
- Hotspots, smouldering and reignition risk after extinguish
- Server-authoritative simulation — built for multiplayer / dedicated servers

### Ground firefighting
- **Portable fire pump** — fuel, intake, pressure, overload / overheat
- **Automatic hose system** — rolls, connector, anchors, path network, pressure loss
- **Fire hose nozzle** — stream / fog modes, free hose length from last anchor
- **Portable water tanks** (S / M / L) and **sprinkler**
- **Tools** — axe, Pulaski, shovel, rake for firebreaks and overhaul
- **Thermal scanner** — find residual heat after the flames die
- **Firefighter gear** — jacket, trousers, boots, helmet, breathing mask, air tank
- **Backpack sprayer** for light / mop-up work
- **Command post** — bind incidents, mission scoring hooks

### ✈️ Optional air support (Peterwolf's Planes)
Forest Fire and **[Peterwolf's Planes](https://modrinth.com/mod/peterwolfs-planes)** are **independent** — neither requires the other.

| Installed | Result |
|-----------|--------|
| Forest Fire only | Full ground firefighting |
| Planes only | Normal aircraft |
| **Both** | **Firefighting Water Bomber** unlocked |

**Water bomber controls** (while piloting):
| Key | Action |
|-----|--------|
| **V** | Arm / disarm water drop + **top-down aim camera** |
| **B** | Hold to release water (strong aerial dump) |
| **H** | Deploy / retract scoop hose |

- High-wing yellow/red tanker look, water tank HUD, altitude above water
- Scoop only when the **hose nozzle is in the water**
- Heavy aerial suppression corridor (much stronger than hand nozzles)

---

## 🎮 Quick start

1. `/fireincident create 4` near a forest  
2. Place **Portable Pump** by water + strainer / intake  
3. Deploy hose rolls with the **Hose Connector** toward the fire  
4. Use the **Nozzle** to spray; tools for firebreaks  
5. Place **Command Post**, cool hotspots with the **Thermal Scanner**  
6. *(optional)* Craft the **Firefighting Water Bomber**, scoop from a lake, drop with **V** + **B**

---

## 🛠️ Crafting recipes (survival)

Craft in a **Crafting Table** (3×3 unless noted).

### Core water system

#### Portable Fire Pump
```
I I I
I B I
I R I
```
- **I** iron ingot · **B** bucket · **R** redstone

#### Hose Roll — Small (16)
```
L L L
L R L
L L L
```
- **L** leather · **R** red dye

#### Hose Roll — Standard (32)
```
L L L
L I L
L L L
```
- **L** leather · **I** iron ingot

#### Hose Roll — Large (64)
```
L L L
L C L
L L L
```
- **L** leather · **C** copper ingot

#### Hose Connector Tool
```
  I
I S I
  I
```
- **I** iron ingot · **S** stick

#### Hose Anchor ×4
```
  I
  I
S S S
```
- **I** iron nugget · **S** stone

#### Intake Strainer
```
I I I
I   I
I I I
```
- **I** iron nugget

#### Hose Splitter
```
  I
I H I
  I
```
- **I** iron ingot · **H** hose roll (small)

#### Fire Hose Nozzle
```
  I I
I H I
    I
```
- **I** iron ingot · **H** hose roll (small)

#### Portable Sprinkler
```
  I
  H
  I
```
- **I** iron ingot · **H** hose roll (small)

#### Water Tank — Small
```
  I
I B I
  I
```
- **I** iron ingot · **B** bucket

#### Water Tank — Medium
```
I I I
I B I
I I I
```
- **I** iron ingot · **B** bucket

#### Water Tank — Large
```
I I I
I B I
I I I
```
- **I** iron block · **B** bucket

#### Pump Fuel Can
```
  I
I C I
  I
```
- **I** iron ingot · **C** coal

#### Backpack Sprayer
```
  L
L B L
  L
```
- **L** leather · **B** bucket

---

### Tools & recon

#### Fire Axe
```
I I
I S
  S
```

#### Pulaski
```
I I
  S I
  S
```

#### Fire Shovel
```
  I
  S
  S
```

#### Fire Rake
```
I I I
  S
  S
```
- **I** iron nugget · **S** stick

#### Thermal Scanner
```
I G I
  R
  I
```
- **I** iron · **G** glass pane · **R** redstone

#### Command Post
```
W W W
W R W
W W W
```
- **W** oak planks · **R** red banner

#### Firefighter Information
```
  P
P P P
  P
```
- **P** paper

---

### Protective gear

#### Helmet / Jacket / Trousers / Boots
Leather armour-style patterns (helmet / chest / legs / boots) using **leather**.

#### Breathing Mask
```
L L L
L G L
```
- **L** leather · **G** glass pane

#### Air Tank
```
  I
I B I
  I
```
- **I** iron · **B** glass bottle

---

### Optional air support (needs Peterwolf's Planes installed to use)

#### Firefighting Water Bomber
```
W B W
I I I
P S P
```
- **W** blue wool · **B** bucket · **I** iron ingot · **P** oak planks · **S** stick  

Appears in the **Planes** creative tab and Forest Fire tab when both mods are loaded.

---

## ⚙️ Requirements

| | |
|--|--|
| Minecraft | **26.2** |
| Loader | **Fabric** ≥ 0.19.3 |
| API | **Fabric API** `0.153.0+26.2` (or compatible) |
| Java | **25+** |
| Optional | [Peterwolf's Planes](https://modrinth.com/mod/peterwolfs-planes) for the water bomber |
| Recommended | [Peterwolf's Realistic Tree Felling](https://modrinth.com/mod/peterwolfs-realistic-tree-felling) for burning tree fall |

**Client + server:** install the same JAR on dedicated servers (simulation is server-side).

---

## 🧾 Config

`config/peterwolfs_forestfire.json`

- Wind (dynamic / fixed), fire performance budgets  
- Hose / pump / wetness  
- **`firefightingAircraft`** — tank size, scoop limits, drop strength / radius  

Admin commands: `/fireincident`, `/wind`, `/fireplane` (with Planes), and more — see docs in the repo.

---

## 📚 Links

- **Source:** https://github.com/peterwolf-pl/Peterwolf-s-Forest-Fire-Firefighting  
- **Planes (optional):** https://github.com/peterwolf-pl/peterwolfs-planes  

---

## 📄 Licence

**MIT**

---

*Stay safe. Fight fire as a team.*
```

---

## PL Description (optional second language / body)

```markdown
# 🔥 Peterwolf's Forest Fire & Firefighting

**Kooperacyjny symulator pożarów lasów i gaszenia** — paliwo, ciepło, wilgotność, wiatr i dowodzenie akcją w multiplayerze.

> *Dedykowane wszystkim strażakom, którzy ryzykują życie chroniąc lasy, zwierzęta i ludzi.*

Mod **nie używa** prawdziwych logo służb, chronionych insygniów ani zastrzeżonych znaków.

## Funkcje

- Realistyczne **paliwo** i stadia spalania, wiatr, iskry, firestorm  
- **Pompa**, węże automatyczne, prądownica, zbiorniki, tryskacz  
- Narzędzia: siekiera, Pulaski, łopata, grabie — pasy przeciwpożarowe  
- Skaner termiczny, sprzęt strażacki, plecak gaśniczy, posterunek dowodzenia  
- **Opcjonalny samolot gaśniczy** przy zainstalowanym **Peterwolf's Planes** (V = zrzut + widok z góry, B = zrzut wody, H = wąż nabierający; nabór tylko gdy dysza jest **w wodzie**)

## Samodzielność modów

| Mody | Efekt |
|------|--------|
| Tylko Forest Fire | Pełne gaszenie naziemne |
| Tylko Planes | Zwykłe samoloty |
| **Oba** | Water Bomber |

## Wymagania

Minecraft **26.2**, Fabric, Fabric API, Java **25+**.

## Receptury

Zobacz sekcję **Crafting recipes** w angielskim opisie powyżej (te same kształty 3×3).

## Licencja

MIT
```

---

## Categories / tags (Modrinth)

Suggested: `gameplay`, `worldgen` *(if applicable)*, `utility`, `multiplayer`, `management`  
Tags: fire, firefighting, multiplayer, survival, fabric, realistic, adventure

## Sidebar

- **Environment:** Client & Server  
- **Loaders:** Fabric  
- **Game versions:** 26.2  
```
