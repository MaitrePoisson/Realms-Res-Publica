# Test guide

## Installation
NeoForge 21.1.x, Minecraft 1.21.1, Java 21. Copy `deliverable/RealmsResPublica.jar` to `mods/`. Solo: open a world with cheats on (integrated server). Dedicated: start the server and give yourself op. The log must print "Realms & Res Publica activé". `/rrp` is an alias of `/respublica`.

## Commands
`help`, `townhall [info|set|build]`, `status [village]`, `village [list|scan|flush]`, `warehouse set` (look at a barrel), `inspect [uuid]` (look at a villager), `setrole <role> [uuid]` (fermier, forestier, mineur, garde, intendant, batisseur, diplomate), `tick <n>` (accelerated time), `famine|overpopulate|insecurity [village]` (debug crises), `territory [incident <owner> <intruder>]` (chunk ownership, doctrines, relations; incident forces a border incident), `reload`.

## Automated checks
- `bash .athanor/check.sh` (or `./gradlew build` + `bash tools/run_sim.sh`): unit tests, balance simulator, 18 headless GameTests (`runGameTestServer`), and a real NeoForge dedicated server run (create village, roles, births, crises, restart persistence).
- GameTests alone: `./gradlew runGameTestServer`.

## Manual checklist
1. Build a village (beds + villagers), look at a barrel and run `/respublica warehouse set`; `/respublica status` shows population, beds, stock, gauges.
2. `/respublica inspect` on a villager: role and morale/satiety/loyalty.
3. `/respublica setrole fermier` then `/respublica tick 600`: the farmer harvests and replants crops near the village; other roles act in their own way (wood, ores, guard strikes monsters, steward serves meals, builder places a hut, diplomat raises relations).
4. Fill the barrel with bread, keep free beds, `tick 6000`: babies appear. Empty the barrel: no births.
5. `/respublica famine`, then `/respublica tick 6000` repeatedly: chat messages announce contestation, then exile; a dissident camp (campfire, wool tents, beds, barrel) appears ~80 blocks away.
6. Keep ticking: the civil war starts (guards of both camps fight); it ends by elimination or truce (reconciliation or autonomous camp).
7. `/respublica overpopulate` and `/respublica insecurity` trigger the other two causes.
8. Restart the server: villages, gauges and roles are restored (`villages.json`).
9. Edit a value in `config.yml`, `/respublica reload`: the new value applies.
10. Urbanism: `/rrp townhall build` at the village centre (or `townhall set` on a gold block under a lectern). Put chests/barrels next to it; foresters, miners and farmers fill them (`/rrp status` shows categories wood/stone/ore/food).
11. Fill the store with logs and stone (60 wood / 40 stone by default) with at least 6 villagers: `/rrp status` lists build orders, then builders level a spot and raise a house, farm or workshop block by block. Leave and come back: unfinished sites resume.
12. Roads appear from the Town Hall to each finished building (dirt path), and to nearby villages or camps; with 60+ stone and 8+ villagers they are paved.
13. At 12+ villagers (or 3+ threats) watchtowers and an oak palisade with gates are built; with 80+ stone and 14+ villagers the palisade is upgraded to stone. Guards stand on towers.
14. Change the quotas in `config.yml` and `/rrp reload` to see growth speed up or slow down.

## How growth is decided (for testing)
- The steward orders a building only when the community store holds at least `expansion_wood_quota` wood AND `expansion_stone_quota` stone (after reserving what queued orders will cost), the village has `expansion_population_quota` villagers, and fewer than `max_build_orders` orders are queued. A village with wood but no stone does not grow.
- Targets: houses when beds < population + `builder_spare_beds`; farms = population/4; workshops = population/6; towers = population/8; wall segments from 12 villagers. The most-needed type that the stock can pay for is ordered.
- If an order cannot be built (no flat spot, missing plan) it is dropped silently; check `/rrp status` for the queue, buildings and roads. Use `/rrp tick <n>` to speed things up.

## Territory, doctrines and relations (for testing)
- `/rrp territory` lists each village's doctrine, owned chunks, contested chunks and the relation between villages (trade / neutral / hostile).
- Doctrine comes from the biome around the Town Hall at founding (badlands/mountains -> warlike empire, plains/beach/river/savanna/desert -> merchant city, swamp/jungle/taiga/forest -> isolationist theocracy; majority vote on a 9x9 biome grid, tie -> merchant) and never changes.
- Put two villages closer than about 10 chunks: borders form where influence overlaps. Let villagers work next to the border: provocation rises (`provocation_per_act`), then warning (3), expulsion by guards (6), border war (10). A merchant city never trespasses; an empire always does.
- `/rrp territory incident <owner> <intruder>` forces an incident for testing; 3 incidents turn the relation hostile. `territory_trespass: 1` lets villagers cross borders freely.
- Keys: `territory_hall_radius`, `territory_outpost_radius`, `territory_owner_threshold`, `territory_contest_margin`, `territory_trespass`, `provocation_per_act|warn|expel|war|decay_s`.
- Border posts (stone bricks, chiseled block, banner in the faction colour) are placed by a builder only when no house is needed; the village needs at least one builder and some stone in the community store. The borders themselves are logical claims that exist without posts.
- Provocation decays slowly (0.02/s): use `/rrp tick <n>` or the `incident` command to see escalation quickly. Two merchant/theocracy villages never trespass on their own; use the `incident` command to test their relation.
- Messages are French only.

## Diplomacy, caravans, wars and sieges (for testing)
- Commands: `/rrp diplomacy` (relation index, treaties), `/rrp caravan` (active caravans), `/rrp regiment` (mobilised guards and gear), `/rrp siege` (engines and breach count), `/rrp war` (wars, verdicts, tribute).
- Relations: build two villages 100-150 blocks apart; `/rrp diplomacy` shows the index. `/rrp territory incident <owner> <intruder>` lowers it by `relation_incident_penalty`.
- Caravans: fill one warehouse above `caravan_surplus` and leave the partner short; a trader with escorts walks over within `caravan_interval_s`. Use `/rrp tick <n>` to speed up.
- War: 3 incidents (`diplomacy_war_incidents`) give an ultimatum, then a war declaration after `diplomacy_war_delay_s`. Guards are mobilised, builders assemble a ram then a catapult near the border; breaches are capped by `siege_block_cap`.
- Outcome: the verdict (REPELLED / PILLAGE / ANNEXATION / DESTRUCTION) appears in chat and `/rrp war`; annexation transfers claims and starts tribute. Ceasefire restores peace and the relation.
- Automated: `bash .athanor/check.sh quick war` replays a two-village war on a real dedicated server (log assertions + top-down export).
- Keys: `relation_*`, `caravan_*`, `diplomacy_*`, `mobilization_*`, `siege_*`, `war_*` in `config.yml`.

## Player roles (for testing; need a real player, so op in solo/LAN or on a server)
- `/rrp role set sovereign`, build a lectern on a gold block: a village is founded (`found_*` limits). `/rrp tax set food 20`, `/rrp law ban on`, `/rrp law embargo <village>`, `/rrp plan zone market` (or two corners with a wooden shovel), `/rrp plan show`.
- `/rrp role set mercenary`, `/rrp contract list` then `accept 1`; kill the raiders of the camp (or win the war) and the escrowed emeralds are paid to the purse (`/rrp role purse`).
- `/rrp role set merchant`, `/rrp market prices`, buy where a resource is in surplus, sell in a village that lacks it; the daily cap (`market_daily_limit`) stops abuse. Relations between the villages improve.
- Sovereign: right-click a cartography table for the political map; `/rrp register` lists relations with [Commerce] [Alliance] [Cessez-le-feu] [Ultimatum] buttons.
- Automated: these flows run as GameTests with mock players (`sovereign*`, `mercenarycontracts`, `freemerchant`, `politicalmap`...) because a headless dedicated server has no client to log in; the console part (villages, wars, persistence) runs on a real server in `bash .athanor/check.sh`.

## Level of detail, performance and biome compatibility (for testing)
- `/rrp lod` lists every village as ACTIVE or ABSTRACT; `/rrp lod force <village> abstract|active|auto` overrides it; `/rrp lod perf [reset]` prints the mod's cost in ms per tick since the last reset.
- Walk away from a village (or unload its chunks): after `lod_grace_s` it turns ABSTRACT; its stock still changes slowly (harvests, meals, births) and returning makes it ACTIVE at once with the real warehouse reconciled. Armies and caravans of abstract villages are points on the political map and appear as real villagers when they reach a loaded village area.
- Performance proof: `bash tools/run_perf_server.sh 40` boots a real dedicated server with 40 force-loaded villages (8 villagers each), measures `rrp lod perf` and vanilla `tick query` in ACTIVE mode, forces everything ABSTRACT and measures again. Last result (`.athanor/visuels/m67-perf.txt`): mod cost 2.30 ms/tick ACTIVE vs 0.24 ms/tick ABSTRACT (about 10x less for the mod's own work), 40 villages, no player. Honest limits: vanilla average tick time was about the same in both modes (3.6 vs 3.7 ms) because the villager entities stay loaded and NoAI in this test and chunks are force-loaded; the test measures the mod's own per-tick cost, not the saving of truly unloaded chunks, and was run on a vanilla flat world (Terralith classification is covered by name/tag unit tests only).
- Biomes: with Terralith (or any biome mod) installed, found villages in its biomes; `/rrp territory` shows the doctrine chosen. Unclassifiable biomes must never produce an error in the log; force a doctrine with `biome_overrides` in `config.yml` and `/rrp reload`.
