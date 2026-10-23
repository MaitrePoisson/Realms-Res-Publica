# Test guide

## Installation
Paper 1.20.4, Java 17+. Copy `deliverable/RealmsResPublica.jar` to `plugins/`, start the server. The console must print "Realms & Res Publica activé". Give yourself op (permission `respublica.admin`). `/rrp` is an alias of `/respublica`.

## Commands
`help`, `status [village]`, `village [list|scan|flush]`, `warehouse set` (look at a barrel), `inspect [uuid]` (look at a villager), `setrole <role> [uuid]` (fermier, forestier, mineur, garde, intendant, batisseur, diplomate), `tick <n>` (accelerated time), `famine|overpopulate|insecurity [village]` (debug crises), `reload`, `selftest` (57 integration cases, result in `plugins/RealmsResPublica/selftest-result.json`).

## Automated checks
- `bash .athanor/check.sh` (or `./gradlew build` + `bash tools/run_sim.sh`): unit tests, balance simulator, real Paper server selftest.
- On a test server: `/respublica selftest` must end with 57 passed, 0 failed (it creates chunks far from spawn and rewrites villages.json: do not run it on a real world).

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
