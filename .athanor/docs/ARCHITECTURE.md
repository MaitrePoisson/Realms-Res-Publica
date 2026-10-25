# Architecture

## Stack (justified)
- **Java 17** (on the PC), **Gradle** (wrapper in the project), **Paper API 1.20.4** (Java 17). A plugin rather than a mod: no modified client, rich API (villagers, PDC), testable on a headless server.
- Tests: **JUnit 5** for the core; a real **headless Paper server** (jar downloaded through the PaperMC API into `.athanor/server/`, outside git) for integration tests driven by `/respublica selftest`.
- Fallback if Paper 1.20.4 is unavailable: an older build compatible with Java 17; if Java 21 is required, a portable JDK 21 in `$TOOLS`.
- Docker unavailable: no `tool`.
- No screenshots (headless server): visual proof = simulator SVG reports and village views in `.athanor/screenshots/` and `.athanor/visuels/`, plus selftest logs.

## Folders
```
build.gradle.kts, settings.gradle.kts, gradlew*
src/main/resources/plugin.yml, config.yml, lang_fr.yml
src/main/java/rrp/
  core/    Pure Java (no Bukkit dependency): Role, Faction, VillagerState, VillageState,
           SimConfig, Gauges, DemographyEngine, CrisisEngine, CrisisLevel, Simulation, SimEvent
  sim/     SimCli (main): scenarios + SVG curves
  bukkit/  RrpPlugin, VillageRegistry, VillageScanner, WarehouseService, VillagerAdapter,
           behaviors/ (Farmer, Forester, Miner, Steward, Builder, Guard, Diplomat),
           BirthService, CrisisService, CampBuilder, WarService, Commands, Persistence, Selftest
src/test/java/rrp/core/*Test.java
tools/   fetch_paper.sh, run_server_test.sh
deliverable/, TEST_GUIDE.md, README.md, docs/CREDITS.md
```

## Shared contracts
- `Role`: BUILDER, FORESTER, MINER, GUARD, FARMER, STEWARD, DIPLOMAT.
- `VillagerState {UUID id; Role role; double morale, satiety, loyalty; String factionId}` (0–100).
- `VillageState {String id; String factionId; int beds; Map<Resource,Integer> stock; List<VillagerState> villagers; int threats; CrisisLevel level; timers}`.
- `SimConfig` loaded from `config.yml` (flat documented keys; in-code defaults identical to the file). Core time is in **simulation seconds**: `Simulation.step(village, dt)`.
- The core never touches the world: it reads a `VillageState` (filled by the Bukkit adapter) and returns **events** (`Birth`, `CrisisChanged`, `Exile(ids)`, `WarStarted`, `WarEnded`…) that `bukkit/` applies. The CLI simulator fills the same state with a virtual economy.
- Persistence: `plugins/RealmsResPublica/villages.json` (villages, crisis levels, timers); a villager's role/gauges live in its PDC (`rrp:role`, `rrp:morale`, `rrp:satiety`, `rrp:loyalty`, `rrp:faction`).
- Warehouse: a `Barrel` designated by `/respublica warehouse set`; stocks = the barrel's real contents.
- Beds = real bed blocks in the radius. Threats = hostile mobs in the radius.

## Test strategy
1. JUnit on all of `core` (deterministic, no randomness or seeded RNG).
2. `SimCli`: scenarios (prosperous, famine, overpopulation, insecurity) → CSV + SVG; assertions in check.
3. Headless Paper server: `Selftest` runs scenarios in game, accelerates time (`/respublica tick`), writes `selftest-result.json`, then `stop`. `check.sh` reads the result.
4. `timeout` on every step; the server is always stopped.

## check.sh
- `bash .athanor/check.sh quick [pattern]`: filtered gradle test + simulator; server selftest only for pattern `server`.
- `bash .athanor/check.sh`: jar + all tests + simulator + full server selftest (10 min timeout); prints `CHECK: PASSED`.
