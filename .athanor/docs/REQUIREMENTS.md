# Requirements — Realms & Res Publica

## Goal
A **Paper** plugin for Minecraft Java Edition 1.20.4 that turns villagers into a living society: seven roles, three individual gauges (morale, satiety, loyalty), demography driven by real stocks and beds, and progressive social crises (contestation → exile/dissident camp → civil war).

## Audience
Survival-server players and admins who want to watch villages evolve organically. Testers: debug commands to trigger famine, overpopulation and insecurity.

## Ranked features
### Essential
1. **Seven roles**: Builder, Forester, Miner, Guard/Soldier, Farmer, Steward, Diplomat — each with a distinct, observable behaviour.
2. **Per-villager gauges**: morale, satiety, loyalty (0–100), evolving from the real world state, saved with the world.
3. **Demography**: a birth happens only if (the communal warehouse holds enough food) AND (a bed is free). Otherwise the population stagnates.
4. **Crises**: prolonged famine, overpopulation, insecurity → contestation → exile + dissident camp → civil war. Configurable thresholds and durations.
5. **No artificial random events**: everything follows from real stocks, beds and threats.
6. **Configuration**: `config.yml` holds all balancing parameters.
7. **Debug/test**: `/respublica` commands (status, inspect, famine, overpopulate, insecurity, tick, village list…).
8. **Persistence**: village and villager state saved and reloaded.

### Important
- Communal warehouse designated by the player (barrel + command), managed by the Steward.
- Gauge display (inspect command + action bar/name).
- Diplomat: relations between factions, can calm tensions and avoid war.
- Performance: 5 villages × 40 villagers without noticeable TPS loss (< 5 ms/tick average for the plugin).
- Offline simulator (CLI) producing curves for balancing.

### Bonus
- Village ambience messages/journal; FR/EN localisation; reconciliation after war.

## Out of scope
Fabric/Forge mods, modified client, custom textures/models, graphical interface, changing emerald trades with players.

## Measurable success criteria
1. Each of the 7 roles performs a visible in-game action (integration test on a real Paper server).
2. Gauges evolve deterministically and are tested (JUnit) against hunger/security/housing.
3. Village with food + beds: population grows (test). Without food OR without beds: it stagnates (test).
4. Prolonged famine/overpopulation/insecurity trigger contestation, exile with a camp (real blocks), then civil war (selftest scenarios on a real server).
5. `bash .athanor/check.sh`: build + JUnit + simulator + Paper server selftest → `CHECK: PASSED`.
6. Deliverable: `deliverable/RealmsResPublica.jar` + annotated `config.yml` + `TEST_GUIDE.md`.
