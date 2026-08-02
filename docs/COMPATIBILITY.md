# Compatibility Notes

## Supported

- Minecraft 26.2
- Fabric Loader ≥ 0.19.3
- Fabric API
- Dedicated servers and multiplayer

## Designed to coexist with

- Minimap mods (not required; command post has built-in info)
- World-edit style tools (admin extinguish/remove available)
- Other structure mods (wooden structures burn via fuel profiles)
- **PeterWolf's Realistic Tree Felling** (recommended) — burning trees fall with full RTF animation and spread fire on impact

## Known interactions

- **Vanilla fire**: adopted into the wildfire simulation inside active areas
- **Realistic Tree Felling** (`peterwolfs_realistic_tree_felling`): soft dependency. When present, weakened burning trunks call `TreeCutManager.forceFallInDirection` and ignite fallen logs/leaves on land via `TreeLandCallbacks`. Without it, a simplified column fall is used.
- **Fluid mods**: tanks are internal units for now; fluid capability bridge is planned

## Shader mods

Smoke uses vanilla particles with distance-friendly rates. Dense volumetric smoke for shaders is a future hook.
