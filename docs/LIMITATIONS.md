# Known Limitations (1.0.0)

- Hose visuals are block segments, not continuous flexible mesh
- No fire vehicles or aircraft yet (roadmap Stage 7/8+)
- Command post UI is chat/text based (full GUI map planned)
- Sector assignment is data-ready; interactive map markers UI is partial
- Peat/underground fire disabled by default
- Tree collapse prefers Realistic Tree Felling (soft dependency); without it, simplified column fall is used
- Pump “strainer” uses iron bars adjacency as a simple proxy
- Runtime config reload is available to operators through `/wind reload`
- Sound events currently alias vanilla sounds as placeholders

Critical systems (incident manager, fire simulation, pump/hose/nozzle, wetness, firebreaks, hotspots, persistence, multiplayer entrypoints) are implemented for the first playable release.
