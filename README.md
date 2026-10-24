# Realms & Res Publica

A NeoForge 1.21.1 mod (Java 21) that turns Minecraft villagers into a living society.

- **Seven roles**: farmer, forester, miner, guard, steward, builder, diplomat — each with its own real in-world behavior.
- **Three gauges per villager**: morale, satiety, loyalty (visible with `/respublica inspect`, saved in the villager's data).
- **Organic demography**: births depend on the food really stored in the village barrel and on the real number of beds; hunger and overcrowding make villagers leave.
- **Crises with real causes**: famine, overpopulation, insecurity → contestation → exile (a dissident camp is built) → civil war → truce (reconciliation or autonomy).
- **Town Hall and community store**: a lectern on a gold block marks the village core (`/rrp townhall set|info|build`). Foresters, miners and farmers deposit wood, stone, ores and crops into chests/barrels next to it; the steward draws from the same store, and a new chest is placed when it is full (`max_chests`).
- **Autonomous expansion**: once the stock (`expansion_wood_quota`, `expansion_stone_quota`) and population (`expansion_population_quota`) quotas are met, the steward orders a building (house, big house, farm, workshop, tower) from a biome-adaptive pool of generated `.nbt` blueprints. Builders level the terrain and place the blocks row by row, fully on their own; construction resumes after a restart.
- **Roads**: dirt paths between the Town Hall, buildings and neighbouring villages/camps (`road_link_distance`), paved with cobblestone/stone bricks when stone and population allow (`road_pave_stone_stock`, `road_pave_population`).
- **Defenses**: watchtowers, an oak palisade then stone walls with gates at road crossings, started by population or threats (`defense_population`, `defense_threats`, `defense_ring_radius`, `defense_stone_stock`, `defense_stone_population`); guards man the towers.
- All balancing lives in `config.yml`; messages are in French (`lang_fr.yml`).

## Install
- **Solo / LAN (integrated server)**: install NeoForge 21.1.x for Minecraft 1.21.1, copy `deliverable/RealmsResPublica.jar` into the `mods/` folder, start the game and open a world. Commands (`/rrp`) need cheats enabled.
- **Dedicated server**: run the NeoForge 21.1.x server installer, copy the jar into the server's `mods/`, start once, accept the EULA. Give yourself op.
- The first launch copies the annotated config to `config/respublica/config.yml` (a copy is in `deliverable/config.yml`); edit it and run `/rrp reload`.

## Build from source
`./gradlew build` (JDK 21) -> `build/libs/RealmsResPublica.jar`. Simulator: `bash tools/run_sim.sh`. Headless GameTests: `./gradlew runGameTestServer`.

See `TEST_GUIDE.md` for commands and a manual checklist. Credits: `docs/CREDITS.md`.
