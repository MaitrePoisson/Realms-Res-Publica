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
- **Territory and borders**: each Town Hall, outpost, camp and watchtower projects influence over chunks (`territory_hall_radius`, `territory_outpost_radius`); a faction owns a chunk above `territory_owner_threshold`, and a chunk is contested within `territory_contest_margin`. Border posts mark the limits and builders place them. `/rrp territory` shows the map, doctrines and relations.
- **Provocation and border war**: villagers working in a neighbour's chunk raise provocation (`provocation_per_act`); the owner warns (`provocation_warn`), guards expel (`provocation_expel`) and a border war breaks out (`provocation_war`). It decays by `provocation_decay_s`; `territory_trespass` lets villagers work abroad freely.
- **Doctrines**: fixed at founding from the surrounding biome. Merchant city (diplomats, markets, never trespasses), warlike empire (guards, miners, barracks, large claims), isolationist theocracy (monuments, expels strangers, walls first).
- **Inter-faction relations**: trade, neutral or hostile stance from the doctrine pair, degraded by border incidents (3+ = hostile); diplomats gain relations faster when trading.
- **Relation index (-100..+100)**: every pair of villages has a numeric relation, seeded from the doctrine pair (`relation_base_trade`, `relation_base_hostile`), lowered by border incidents (`relation_incident_penalty`) and drifting back to neutral (`relation_decay_s`). At or above `relation_trade_at` the stance is TRADE, at or below `relation_hostile_at` it is HOSTILE.
- **Caravans**: a village with a surplus (`caravan_surplus`) sends a trader with `caravan_escorts` guards to a friendly partner in deficit (`caravan_deficit`), at most `caravan_max_load` items, every `caravan_interval_s`. Delivery raises the relation (`caravan_relation_gain`); an ambush (`caravan_ambush_chance`) or timeout (`caravan_timeout_s`) lowers it (`caravan_relation_loss`).
- **Treaties and war declarations**: states TRADE_TREATY, DEFENSIVE_ALLIANCE (relation >= `diplomacy_alliance_at` and a common enemy) and WAR. After `diplomacy_war_incidents` incidents an ultimatum is issued; war follows after `diplomacy_war_delay_s` unless a calming gesture (`diplomacy_calm_gain`) or a diplomat prevents it. A village in critical food (`diplomacy_critical_food`) avoids war.
- **Mobilisation**: at war, up to `mobilization_share` of the population forms a regiment, equipped from the warehouse with locally forged gear (`mobilization_sword_ore`, `mobilization_armor_ore`, `mobilization_bow_wood`).
- **Sieges**: attacking builders assemble a battering ram (breaks doors and gates) then a catapult (breaches wall blocks) near the enemy border; defending guards destroy engines. Each siege may break at most `siege_block_cap` blocks and never digs below the village floor (`siege_*` keys).
- **War outcomes**: after `war_resolve_min_s` (at most `war_resolve_max_s`) the power ratio decides: REPELLED, PILLAGE (`war_pillage_at`, loot `war_loot_share` capped by `war_loot_cap`), ANNEXATION (`war_annex_at`: claims transferred, periodic tribute `war_tribute_share` capped by `war_tribute_cap` every `war_tribute_period_s`) or DESTRUCTION (`war_destroy_at`, needs a full breach cap: the hall is removed and the population merges into the nearest village). A ceasefire restores peace (`war_peace_gain`).
- All balancing lives in `config.yml`; messages are in French (`lang_fr.yml`).

## Install
- **Solo / LAN (integrated server)**: install NeoForge 21.1.x for Minecraft 1.21.1, copy `deliverable/RealmsResPublica.jar` into the `mods/` folder, start the game and open a world. Commands (`/rrp`) need cheats enabled.
- **Dedicated server**: run the NeoForge 21.1.x server installer, copy the jar into the server's `mods/`, start once, accept the EULA. Give yourself op.
- The first launch copies the annotated config to `config/respublica/config.yml` (a copy is in `deliverable/config.yml`); edit it and run `/rrp reload`.

## Build from source
`./gradlew build` (JDK 21) -> `build/libs/RealmsResPublica.jar`. Simulator: `bash tools/run_sim.sh`. Headless GameTests: `./gradlew runGameTestServer`.

See `TEST_GUIDE.md` for commands and a manual checklist. Credits: `docs/CREDITS.md`.
