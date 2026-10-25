# Log: older entries

Archived automatically by the pilot. Nothing in it is modified.

## 2026-10-23 — Framing (phase 0)
- Choice: plugin Paper 1.20.4 / Java 17 / Gradle, pure-Java testable core + CLI simulator + selftest on a headless Paper server.
- Written: docs/REQUIREMENTS.md, DESIGN.md, ARCHITECTURE.md, PLAN.md (22 milestones), DECISIONS.md.
- critic: review of the plan; corrections applied (selftest early in M8, war tension formula, camp status, milestones split, headless constraints).
- Next step: M1 (Gradle skeleton).

## 2026-10-23 — M1: Gradle skeleton
- Done: build.gradle.kts (Paper API 1.20.4, JUnit 5, release 17), Gradle wrapper 8.10.2, plugin.yml, config.yml, RrpPlugin, Role (7 roles), RoleTest, .gitignore.
- Verified: `./gradlew build` OK → build/libs/RealmsResPublica.jar with plugin.yml; RoleTest 2/2; Java 17.0.20. Minimal check.sh created (extended in M2).
- Next step: M2 (headless Paper server + full check.sh).

## 2026-10-23 — M2: headless Paper server + check.sh
- Done: tools/fetch_paper.sh (Paper 1.20.4, flat world fixed), tools/run_server_test.sh (no FIFO: subshell that sends "stop", timeout), full check.sh (quick / quick <motif> / quick server / full).
- Verified: `check.sh quick`, `quick server` and full → CHECK: PASSED; plugin enabled then disabled cleanly, no residual Paper java (an orphan server from a previous attempt was stopped).
- Next step: M3 (core model and configuration).

## 2026-10-23 — M3: core model and configuration
- Done: Faction, Resource, CrisisLevel, VillagerState, VillageState, SimEvent (sealed record), SimConfig (41 keys with defaults and bounds, fromMap with warnings), config.yml generated then commented, SimConfigTest (8 tests).
- Verified: code defaults = config.yml, each key present and commented, bounds; full `check.sh` → CHECK: PASSED.
- Next step: M4 (gauge dynamics).

## 2026-10-23 — M4: gauge dynamics
- Done: Gauges (satiety decay, meals from stock, hungriest first, target morale = base + satiety + bed + safety − overpopulation, loyalty toward average morale + diplomat bonus, accelerated loss in contestation, canWork for disobedience); GaugesTest (15 deterministic tests).
- Verified: `check.sh quick Gauges` then full `check.sh` → CHECK: PASSED.
- Next step: M5 (core demography).

## 2026-10-23 — M5: core demography
- Done: DemographyEngine (timer capped at the interval; conditions food ≥ threshold, free bed, pop < max; consumes food, emits Birth; the caller adds the villager); DemographyTest (11 tests).
- Verified: `check.sh quick Demography` then full `check.sh` → CHECK: PASSED. Stagnation without food or without a bed over 1 h, growth with both.
- Next step: M6 (core crisis engine).

## 2026-10-23 — M6: core crisis engine
- Done: CrisisEngine (famine/overpopulation/insecurity indicators, escalation STABLE→CONTESTATION→EXIL→GUERRE_CIVILE, exile with camp creation, tension T, truce, elimination, slow recovery), Simulation (gauges + demography + crises), SimEvent.Exile enriched (campId, foodMoved), VillageState.parentId; CrisisTest (27 tests).
- Verified: `check.sh quick Crisis` then full `check.sh` → CHECK: PASSED. No randomness (determinism test).
- Next step: M7 (CLI simulator and SVG reports).

## 2026-10-23 — M7: CLI simulator and SVG reports
- Done: rrp.sim.{Scenario,ScenarioRunner,Report,SimCli} (4 scenarios, virtual economy, CSV + SVG 2×2), tools/run_sim.sh, ScenarioTest (7 tests), simulator hooked into check.sh (quick, quick sim, full). Loyalty defaults rebalanced (see DECISIONS.md) because famine never led to exile.
- Verified: `check.sh quick sim` then full `check.sh` → CHECK: PASSED; report sim-famine.svg rendered and reviewed (readable curves, exile marker).
- Results: prosperous 9→33 stable; famine contestation 120 s then exile 2140 s; overpopulation contestation 120 s then exile 345 s; insecurity contestation 155 s.
- Next step: M8 (server selftest framework + tick command).

## 2026-10-23 — M8: server selftest framework + tick command
- Done: Commands (/respublica help|selftest|tick), Selftest (SelftestCase, selftest-result.json), TickService, TestWorld (forceload, teleportation, villagers, bed, barrel, cleanup), BaseCases (3 cases), Lang + lang_fr.yml, plugin.yml (command, permission); run_server_test.sh runs the selftest via the console and reads the result.
- Verified: `check.sh quick server` OK; a deliberately failing case does make the check fail (code 1); full `check.sh` → CHECK: PASSED (3/3 cases).
- Next step: M9 (Bukkit villages, warehouse, beds, persistence).

## 2026-10-23 — M9: Bukkit villages, warehouse, beds, persistence
- Done: VillageSite, VillageRegistry (creation, lookup, merge, remove), VillageScanner (budgeted scan in slices over tile entities, discovery of ≥2 villagers + ≥1 bed), WarehouseService (stock = real barrel content, deposit/withdraw), Persistence (villages.json, buffer + periodic flush + shutdown, atomic write); RrpPlugin wired (SimConfig from config.yml, world timer); commands village list|scan|flush, warehouse set, status; texts in lang_fr.yml.
- Verified: 9 new selftest server cases (real stock, real beds outside the radius excluded, creation, non-creation, merge, scan budget ≤ max, villages.json round trip, batched writes, commands); run_server_test.sh requires villages.json after shutdown; full `check.sh` → CHECK: PASSED (12/12).
- Next step: M10 (villager adoption, roles and gauges).

## 2026-10-23 — M10: villager adoption, roles and gauges
- Done: core/RoleBalance (role choice, essential roles guard/steward/diplomat depending on population) + RoleBalanceTest (7 tests); bukkit/{RoleAssigner (profession → index), VillagerAdapter (PDC rrp:*, adoption, reread, displayed name, EntityBreedEvent cancelled, write on unload), GaugeTicker (real barrel stock, beds, threats, eaten food removed from the real barrel, batched PDC writes)}; commands inspect and setrole; key show_role_names; 7 RoleCases.
- Verified: `check.sh quick RoleBalance`, `check.sh quick server` (19/19), then full `check.sh` → CHECK: PASSED.
- Next step: M11 (Farmer, Forester, Miner behaviors).

## 2026-10-23 — M11: Farmer, Forester, Miner behaviors
- Done: bukkit/behaviors/{RoleBehavior (canWork, runOnce), BehaviorContext, Farmer, Forester, Miner}; BehaviorScheduler (cadence behavior_interval_s, work radius work_radius, disobedience morale < disobey_morale, capped catch-up) wired in RrpPlugin; wheat counts as food in the warehouse.
- Verified: 6 BehaviorCases (harvest+replant → food, log → wood + sapling growth, ore/stone → loot, low morale ⇒ nothing, outside radius ⇒ nothing, cadence); full `check.sh` below.
- Next step: M12 (Steward and Guard).

## 2026-10-23 — M12: Steward and Guard behaviors
- Done: behaviors/Steward (removes real food from the barrel, serves the hungriest first, up to threshold+20) and Guard (strikes the nearest hostile monster within the work radius, village threats −1); registered in BehaviorScheduler; 4 selftest cases (service, no food/demoralized, killing, outside radius/demoralized). Threats are still counted by GaugeTicker in VillageState.
- Verified: `check.sh quick server` 29/29, then full `check.sh` below.
- Next step: M13 (Builder and Diplomat).

## 2026-10-23 11:41: folder check (Athanor)

- Unticked: M8 (missing: plugin.yml); M9 (missing: lang_fr.yml).

## 2026-10-23 — M13: Builder and Diplomat behaviors
- Done: behaviors/Builder (consumes `hut_wood` from the barrel, places a 4×3 hut with a real bed, beds +1, only if beds are missing) and Diplomat (visits the neighbouring village: relations +`relations_per_visit` on both sides; without a neighbour, talks to villagers: loyalty +`talk_loyalty_bonus`); BehaviorContext receives the registry; keys hut_wood and talk_loyalty_bonus; TestWorld.rememberArea; 4 BehaviorCases.
- Environment: project moved to this Linux PC: JDK 17 reinstalled in $TOOLS/jdk17 (check.sh detects it), chmod +x gradlew and scripts, JUnit java.io.tmpdir = $TMPDIR (sandbox). REVERIFY handled: M8 (plugin.yml) and M9 (lang_fr.yml) are indeed present.
- Verified: full `check.sh` → CHECK: PASSED (77 JUnit tests, server selftest 33/33).
- Next step: M14 (births in the world).
## 2026-10-23 — M14: births in the world
- Done: bukkit/BirthService (no birth every 5 s of game time, bounded catch-up under accelerated time; DemographyEngine decides from the real stock and the real beds; food is removed from the real barrel; a villager baby appears at the centre; a baby reserves its bed; at maturity VillagerAdapter.sync adopts it with role + gauges) wired in RrpPlugin (worldTimer); 6 BirthCases cases.
- Verified: `check.sh quick server` 39/39, then full `check.sh` → CHECK: PASSED. Pitfall: the test world persists; babies/adults born during earlier passes are removed by the cases (clearBabies).
- Next step: M15 (contestation and exile with a dissident camp).

## 2026-10-23 15:16: folder check (Athanor)

- Unticked: M8 (missing: plugin.yml); M9 (missing: lang_fr.yml).

## 2026-10-23 — M15: contestation and exile with a dissident camp
- Done: bukkit/CrisisService (CrisisEngine applied to the real villages every 5 s of game time, real stock re-read, contestation/calm messages to nearby players and to the console; exile applied to the world: exiles teleported, HOME/JOB_SITE memories cleared, PDC updated, food transferred barrel to barrel); CampBuilder (campfire, barrel, wool tents with real beds, site avoiding water/trees/slope, up to 12 tents); VillageRegistry.adopt; crisis.* keys of lang_fr.yml.
- Verified: 5 CrisisCases cases (famine → contestation then exile, real camp + food + memories + persistence, second exile in the same camp, overpopulation → contestation, camp avoiding water); full `check.sh` → CHECK: PASSED (selftest 44/44).
- Next step: M16 (insecurity and civil war).

## 2026-10-23 — M15b: village visuals (request from the owner at 15:34)
- Done: bukkit/VisualExport takes a snapshot, at the end of each selftest case (before cleanup, hook in VillageCases.of), of the villages created: beds, barrel, huts, wool tents, campfire, water and villagers by role (tooltip with gauges); HTML page with one SVG per scene (scenes with a village + dissident camp first) written by Selftest; tools/render_visuels.sh copies it and produces a PNG with Edge.
- Files to open: `.athanor/visuels/index.html` (interactive) and `.athanor/visuels/apercu.png`. Regenerated at each server `check.sh` (so at the end of each milestone).
- Verified: selftest 44/44; PNG read: tents, beds, barrel and villagers visible for the exile.
- Next step: M16 (insecurity and civil war).

## 2026-10-23 — M16: insecurity and civil war
- Done: bukkit/WarService (combat tick per game second: the guards of both sides move closer — bounded deterministic movement to stay within their own camp — then strike for `war_damage` HP; dead units removed from the state immediately by sync); CrisisService exposes event listeners; end of war: elimination, truce with reconciliation (cause gone: the camp returns to the village, loyalty raised) or autonomous camp (cause persistent); VillageSite.reach() widens membership during the war (not persisted); key `war_damage` (config.yml + SimConfig); crisis.war_* messages; 4 WarCases cases (insecurity → contestation, elimination, truce-reconciliation, truce-autonomy).
- Verified: server selftest 48/48, then full `check.sh` below. Visuals regenerated in `.athanor/visuels/index.html`.
- Pitfall: accelerated time = several turns in the same tick, hence `setNoDamageTicks(0)` before each strike.
- Next step: M17 (crisis debug commands and full selftest).

## 2026-10-23 — M17: crisis debug commands and full selftest
- Done: Commands gains `famine` (actually removes food from the barrel), `overpopulate` (spawns real villagers beyond beds × ratio, then sync), `insecurity` (persistent zombies that do not burn, more numerous than the guards cover) and `reload` (re-reads config.yml); village as argument or the player's own; help and tab-completion updated, lang_fr keys (help.*, debug.*, reload.done). TestWorld.track, Selftest.names. 5 new cases (CommandCases): one per command + help/reload + a coverage case (growth, stagnation, 7 roles, 3 crises, exile, war).
- Verified: server selftest 53/53; `check.sh` full → CHECK: PASSED.
- Next step: M18 (multi-village performance).

## 2026-10-23 — M18: multi-village performance
- Done: TickService times the cost of the timers (resetCost/averageMsPerTick); VillagerAdapter counts PDC writes (pdcWrites) and the grouped flush no longer writes a villager whose PDC is already up to date; PerfCases (2 cases): 5 villages × 40 villagers over 1200 ticks, average cost logged, scan ≤ max_scan_blocks_per_tick, grouped PDC writes.
- Verified: measured cost 0.015 ms/tick (ceiling 5), max scan 410 blocks/tick (budget 2000), 200 PDC writes for 300 gauge steps; selftest 55/55; `check.sh` full → CHECK: PASSED.
- Next step: M19 (in-game readability, texts and help).

## 2026-10-23 — M19: in-game readability, texts and help
- Done: LangKeysTest (JUnit) checks that every language key referenced by the code exists in lang_fr.yml (literals of the lang.get/msg calls, ternaries included), that the dynamic crisis.cause.* families are complete, and that every subcommand is in SUBS and has its help.* text; ActionBarService (village/population/food/crisis action bar to players in a village, key `show_action_bar` in SimConfig + annotated config.yml); `/respublica status` shows the French label of the crisis; selftest case strengthened (help, status, action bar). Crisis messages to nearby players: already ensured by CrisisService.tell (radius + 48), kept.
- Verified: the test does fail when a key is removed; selftest 55/55; `check.sh` full → CHECK: PASSED.
- Pitfall: backslashes in a heredoc/python are eaten by the Bash tool; write the Java regexes with Write/Edit.
- Next step: M20 (independent tester).
- Request re-read (remarks from the visuals of 15:34/16:00 already handled by M15b); plan unchanged.

## 2026-10-23 — M20: full phase end, independent tester
- tester: full check replayed, logs read, debug commands replayed on the server; 1 P0 + 6 P1 + 14 P2 recorded in `.athanor/BUGS.md`.
- Fixed (all P0/P1): tolerant persistence + `.bak` copy; unique camp id; lasting insecurity → `morale_insecurity_penalty` (escalation up to war in the simulator); village without warehouse = no hunger or famine; unloaded village = crises and gauges frozen; forester (foliage required) and miner (outcrops only); selftest case "full chain famine → war" without priming.
- Verified: JUnit + simulator + server selftest 57/57, full `check.sh` → CHECK: PASSED. Remaining P2 listed in BUGS.md for M21.
- Next step: M21 (polish: critic then tester).

## 2026-10-23 — Framing cycle (resume)
- The request only said "resume the project". Existing plan, REQUIREMENTS, DESIGN and ARCHITECTURE reviewed: valid, M1–M20 ticked.
- Plan unchanged: remaining M21 (polish: critic then tester; HANDOFF.md holds the critic's correction plan) and M22 (delivery).
- No code, no installation this cycle. Next step: M21.

## 2026-10-23 — M21: polish (critic then tester)
- critic: corrections applied (relations reset + diplomat counterpart, no births in crisis/stale stock, builder spare beds, exile cap with a loyal guard, richer status, behaviors spread over ticks, config keys). tester: 3 stale selftest cases (P1) fixed; open P2 listed in BUGS.md.
- Env: Gradle needs JDK 17 (installed in $TOOLS/jdk17); run_server_test.sh forces UTF-8 stdout. REVERIFY handled (folder moved, CRLF noise only).
- Verified: JUnit + simulator + server selftest 57/57, full check.sh → CHECK: PASSED. Next step: M22 (delivery).

## 2026-10-23 — M22: delivery
- Done: deliverable/ (jar identical to the fresh build, annotated config.yml), README.md, TEST_GUIDE.md, docs/CREDITS.md, .athanor/FINAL_REPORT.md (STATUS: DONE).
- Verified: jar rebuilt by full check and loaded by clean Paper server, selftest 57/57, CHECK: PASSED.

## 2026-10-23 — Resume: NeoForge port framing
- New request: port the Paper plugin to a NeoForge 1.21.1 mod (server-side, one jar). Plan extended with M23–M30 (build, commands, villagers/persistence, ticks/births, behaviors, crises/war, GameTests, delivery + Paper cleanup). No code this cycle.
- Next step: M23.

## 2026-10-23 — M23: NeoForge build skeleton
- ModDevGradle 2.0.78 + NeoForge 21.1.172, Java 21 (JDK in $TOOLS/jdk21). Paper code moved to legacy-paper/ (outside the build, deleted in M30). New rrp.neoforge: RrpMod (@Mod, server events), Lang, ConfigLoader, FlatYaml (no snakeyaml dependency).
- tools/run_neoforge_server.sh boots the real dedicated server via the generated launch script and stops it cleanly; check.sh adapted. LangKeysTest reads the Paper reference until Commands.java is ported (M24).
- Verified: JUnit + sim + server boot, full check → CHECK: PASSED. Next step: M24.

## 2026-10-23 — M24: Commands + Lang + Config
- rrp.neoforge.Commands: Brigadier /respublica and /rrp (permission level 2), same sub-commands as Paper minus selftest (replaced by GameTests in M29). help and reload are live; the other sub-commands are registered through Commands.register(sub, handler) by the services of M25–M28 and answer "pas encore disponible" until then.
- LangKeysTest now scans rrp.neoforge (+ the Paper reference while it exists). Server script sends console commands and checks the replies.
- Verified: full check → CHECK: PASSED. Next step: M25.

## 2026-10-23 — M25: Villagers, village scanner, persistence
- Ported to rrp.neoforge: VillageSite/VillageRegistry/Persistence (MC-free, Gson, villages.json in <world>/respublica/), RrpAttachments (data attachment respublica:villager = role, gauges, faction), VillagerAdapter (sync/adoption, names, vanilla births cancelled), VillageScanner (budgeted, loaded chunks only, bed block entities), WarehouseService (barrel), RoleAssigner, TickService (ServerTickEvent), Worlds. Commands now live: tick, village, warehouse, status, inspect, setrole (VillageCommands).
- Decision: villager data is flushed to the attachment on EntityLeaveLevelEvent, every persistence_flush_s and at server stop. Console `warehouse set` picks the nearest barrel.
- Verified: PersistenceTest (JUnit) + server script in 2 phases (village created from 2 villagers + bed + barrel, role changed, stop, restart: village-1, roles Diplomate/Fermier and 2 villagers restored). Full check → CHECK: PASSED. Next step: M26.

## 2026-10-23 — M26: Gauges, births, action bar
- rrp.neoforge: GaugeTicker (core Gauges.step with real barrel stock, beds, hostile mobs in radius; food eaten withdrawn from the barrel; frozen when chunks unloaded), BirthService (DemographyEngine decides, food removed from barrel, baby villager spawned, babies reserve free beds), ActionBarService (village line for players inside, show_action_bar). All driven by TickService via the mod timer in RrpMod.
- Server script phase 2 extended: 3 beds + bread, `rrp tick 12000`: satiety 62→56, food 64→54, 2 births logged and 8 food removed.
- Verified: full check → CHECK: PASSED. Next step: M27.

## 2026-10-23 — M27: Role behaviors
- rrp.neoforge.behaviors: Farmer, Forester, Miner, Steward, Guard, Builder, Diplomat (written earlier) + new BehaviorScheduler (cadence behavior_interval_s, behaviors_per_tick spread, morale disobedience, loaded villages only), wired in RrpMod timer. New `/rrp village act` forces one action per villager.
- Verified: server script grows wheat (age 7) at noon, sets farmers, `village act` → 1 villager acted (harvest into the barrel). GameTests per role come in M29. Full check → CHECK: PASSED. Next step: M28.

## 2026-10-23 — M28: Crises, dissident camp, civil war
- rrp.neoforge: CrisisService (real world state -> core crisis engine, thresholds from config.yml), CampBuilder (wool tents, fire, barrel, beds), WarService (loyalist vs dissident guards, truce/elimination), CrisisCommands (`/rrp famine` etc.).
- Verified: real-server phase 3 on a fresh world: famine -> contestation -> exile (camp village-1-camp built) -> civil war -> end -> calm. GameTests per stage come in M29. Full check -> CHECK: PASSED. Next step: M29.

## 2026-10-23 — M29: GameTests
- rrp.neoforge.gametest.RrpGameTests (8 headless GameTests on a generated 24x10x24 arena template, tools/gen_gametest_structure.py): base, commands, village, roles, births, behaviors (farmer/forester/miner/guard), crisesAndWar (famine -> exile + camp -> civil war), perf. check.sh runs `runGameTestServer` (new `quick gametest` mode).
- Fix found by the real-server run: Builder could wall a living villager inside a new hut (suffocation); fits() now rejects spots containing living entities.
- Visual export not kept (no Paper-style export on GameTest); camp wool/barrel verified by a log line in the real-server phase 3.
- Verified: full check -> CHECK: PASSED. Next step: M30.

## 2026-10-23 — M30: Delivery and cleanup
- Removed legacy-paper/, Paper scripts (fetch_paper, run_server_test, render_visuels); README/TEST_GUIDE/CREDITS now describe NeoForge solo and dedicated installs; jar rebuilt into deliverable/.
- tester: no open P0/P1; two P3 cosmetic leftovers (Bukkit in comments), stale test constant fixed. FINAL_REPORT.md STATUS: DONE.
- Verified: full check -> CHECK: PASSED. All milestones done.

## 2026-10-23 — Resume: economy and urbanism request
- Read the new request, planned M31–M39 (Town Hall, community chests, expansion planner, .nbt blueprint pool, autonomous builders, roads, defenses, tests, delivery) in PLAN.md. No code change; planning only.
- Decision: Town Hall = vanilla marker structure (no custom block/client asset), keeps the mod server-only. Next step: M31.

## 2026-10-23 — M31: Town Hall master block
- rrp.neoforge.TownHall (lectern on gold block, vanilla marker), VillageSite hall + inCore(), persisted in villages.json, scanner auto-detects it (and forgets a destroyed one), Builder places one near the centre when missing, `/rrp townhall info|set|build`, config town_hall_radius (core radius), lang keys.
- Note: the hall delimits the core (radius) but the village centre itself is unchanged.
- Verified: new GameTest townHall (9 GameTests), persistence unit test; full check -> CHECK: PASSED. Next step: M32.

## 2026-10-24 — M32: Community chests and collectors
- WarehouseService now spans the designated barrel plus chests/barrels within `store_radius` of the Town Hall (deposit/withdraw/read across containers); a full store makes collectors place a new chest near the hall (cap `max_chests`); a hall without a warehouse gets a barrel (ensureForHall). `/rrp status` shows the community chest count. Forester/miner/farmer use the shared deposit path, steward withdraws from the same store.
- GameTest communityStore added; hall tests moved to their own batches (shared world made the `behaviors` test see their halls).
- Verified: full check -> CHECK: PASSED (10 GameTests, real server run). Next step: M33.

## 2026-10-24 — M33: Expansion planner
- rrp.neoforge.urban: BuildType (costs), BuildOrder, ExpansionPlanner (pure logic: wood+stone+population quotas, queued orders reserve stock, cap, need-based type choice, ring-slot site around the hall). Steward issues orders each action; orders and built counts persisted in villages.json; `/rrp status` lists orders; config keys expansion_wood_quota, expansion_stone_quota, expansion_population_quota, max_build_orders.
- Verified: ExpansionPlannerTest (6) + persistence round trip; full check -> CHECK: PASSED. Next step: M34.

## 2026-10-24 — M34: Blueprint pool
- tools/gen_blueprints.py (deterministic) writes 35 .nbt blueprints (house, big_house, farm, workshop, watchtower, wall_segment, gate × plains/desert/snow/savanna/jungle) into data/respublica/structure. urban.BiomeStyle (biome -> family), urban.BlueprintPool (Kind, id, load through StructureTemplateManager; Kind.of(BuildType)).
- Verified: BiomeStyleTest; GameTest `blueprints` loads all 35 and places desert/snow houses with the right palette and a bed; full check -> CHECK: PASSED. Next step: M35.

## 2026-10-24 — M35: Autonomous builders
- urban.TerrainLeveler (spot search in the borders on near-flat natural ground, no building/entity in the footprint; row-by-row cut/fill/clear with a 1-block margin), urban.ConstructionSite (persisted type/origin/step: levelling rows, then the blueprint row by row via a bounding box, then a final whole-blueprint pass so beds/fences/panes connect). Builder: takes the first BuildOrder, pays wood+stone from the community store, walks to the site, 6 steps per action, finishes (order removed, built counter, beds counted); unreachable orders are dropped; legacy hut kept when no order. Builder is moved out of the footprint; other living entities delay a step. VillageSite/Persistence store the construction.
- Verified: GameTest `builders` (waits without materials, consumes stock, reload mid-build resumes, house + bed + margin cleared, no one walled in; 12 GameTests); full check -> CHECK: PASSED. Next step: M36.

## 2026-10-24 — M36: Roads
- urban.RoadNetwork: pure A* on a height grid (4 neighbours, step ≤ 1, water/buildings/cliffs blocked), Road (endpoints, path, laid/paved cursors), tile laying (dirt path, 2 wide for hall-to-building roads, 1 wide for links), paving (cobblestone/stone bricks, 1 stone per 2 positions). Persisted in villages.json (`roads`). Builder: finished buildings queue a road from the hall; neighbouring villages/camps within `road_link_distance` are linked; roads are laid when no construction is possible, paved when stone stock ≥ `road_pave_stone_stock` and population ≥ `road_pave_population`. New config keys road_pave_stone_stock, road_pave_population, road_link_distance.
- Verified: RoadNetworkTest (3), GameTest `roads` (detour around a pond, connected tiles, persistence mid-way, paving consumes stone; 13 GameTests; crisesAndWar moved to its own batch, it made `behaviors` flaky); full check -> CHECK: PASSED. Next step: M37.

## 2026-10-24 — M37: Defensive infrastructure
- urban.DefensePlan (pure: start/upgrade thresholds, tower sites at corners then side middles, square ring geometry, gates at road crossings), DefensePlanner (world: oak-fence palisade 3 high, stone-brick walls, fence gates, 8 columns per builder step, costs wood/3 and stone/2 per column), Defense (persisted state in villages.json). Builder.workOnDefense: starts on population >= defense_population or threats >= defense_threats, orders watchtowers (4, 8 when pop >= 24), lays the palisade, upgrades to stone when stock and population allow. Guards go to a tower post (stable by UUID) and get +8 strike range there. New config keys defense_population, defense_threats, defense_ring_radius, defense_stone_stock, defense_stone_population; `/rrp status` shows defenses.
- Verified: DefensePlanTest (5), GameTest `defense` (trigger, tower order, closed palisade, gate at road crossing, stone upgrade, persistence; 14 GameTests); full check -> CHECK: PASSED. Next step: M38.

## 2026-10-24 — M38: Integration tests and real-server run
- New GameTest `planner` (steward issues a house order on a bed shortage, capped; 15 GameTests). `/rrp status` now shows buildings and roads. tools/run_neoforge_server.sh phase 4: fresh world, 10 villagers, hall, stocked store, 90 acts -> {HOUSE=6, FARM=2, WORKSHOP=1, TOWER=1}, 7 roads / 119 tiles; tick P50 0.4 ms, P95 2.5 ms. The script cleans up its extra worlds (they leaked into the GameTest server). tools/topdown.py renders .athanor/visuels/m38-village.png (houses, farms, roads visible).
- Verified: full check -> CHECK: PASSED. Next step: M39 (delivery).

## 2026-10-24 — M39: Delivery
- README/TEST_GUIDE/CREDITS describe Town Hall, store, expansion rules, roads, defenses, config keys. tester + critic run: P0 stale jar fixed (check.sh now copies jar+config into deliverable/), growth rules documented. Fixed GameTest pollution: run_neoforge_server.sh now removes run/rrp-test-world. FINAL_REPORT STATUS: DONE.
- Verified: full check -> CHECK: PASSED.

## 2026-10-25 — Resume: territory and factions framed
- Request 3 (borders, influence map, doctrines) split into M40–M46 in PLAN.md (core influence map, world markers, provocation, doctrines, doctrine behaviours, relations + server run, delivery). No code changed. Next step: M40.

## 2026-10-25 — M40: Influence map core
- rrp.core.territory: ChunkPos, Claim, InfluenceMap (linear-decay markers per chunk, cumulative per faction, owner above threshold, contested within margin, deterministic tie-break). Config keys territory_hall_radius, territory_outpost_radius, territory_owner_threshold, territory_contest_margin.
- Verified: InfluenceMapTest (8), SimConfigTest; full check -> CHECK: PASSED. Next step: M41.

## 2026-10-25 — M41: Territory markers in the world
- core BorderPlan (posts on the edge of claimed chunks, JUnit BorderPlanTest); neoforge TerritoryService builds the influence map from villages.json (hall, defense towers) and places border posts (stone bricks + chiseled + faction-coloured banner), idempotent so persistence/resume needs no extra file. Builder places missing posts when no house is needed. `/rrp territory` prints an ASCII chunk grid with legend (FR lang keys).
- GameTest `territory` (16 GameTests): hall chunk claimed, far chunk free, builder places first post with banner. Note: a first gametest run failed on a transient world DirectoryLock; rerun passed.
- Verified: full check -> CHECK: PASSED. Next step: M42.

## 2026-10-25 — M42: Exploitation radius and provocation
- core Provocation (score per owner>intruder pair, WARN/EXPEL/WAR thresholds, decay); neoforge ExploitationWatcher: Farmer/Forester/Miner skip foreign chunks unless territory_trespass=1, working there raises the owner's provocation; WARN message, EXPEL teleports intruders home, WAR registers a border war fought by WarService.borderRound (guards, no camp clamp) until the score decays. New config keys territory_trespass, provocation_{per_act,warn,expel,war,decay_s}; FR messages.
- Verified: ProvocationTest (4), GameTest `exploitation` (17 GameTests); full check -> CHECK: PASSED. Next step: M43.

## 2026-10-25 — M43: Doctrines
- core Doctrine (MERCHANT_CITY, WARLIKE_EMPIRE, ISOLATIONIST_THEOCRACY; role weights, claim radius, expansion aggressiveness, foreigner distrust, wall priority) and DoctrineSelector (biome path -> doctrine, majority vote, tie -> merchant). Modifiers live in the enum (decision: no new config keys).
- VillageSite.doctrine persisted in villages.json; TerritoryService.ensureDoctrine samples a 9x9 biome grid around the Town Hall at founding (via WarehouseService.ensureForHall), fixed afterwards.
- Verified: DoctrineTest (4); full check -> CHECK: PASSED. Next step: M44.

## 2026-10-25 — M44: Doctrine behaviours
- RoleBalance.choose(…, doctrine) weights roles (merchant: diplomats, empire: guards/miners); ExpansionPlanner.plan(…, doctrine): MARKET / BARRACKS / MONUMENT / BREWERY build types (+11 blueprints kinds via tools/gen_blueprints.py, 20 new .nbt), doctrine-specific buildings take priority, wallPriority scales towers/walls. ExploitationWatcher.aggressive: merchant never trespasses (armed neutrality), empire always; theocracy guards expel strangers from its chunks (expelStrangers); claim radius scaled by doctrine.claimRadiusFactor (strong empire claims more neighbour land).
- Decisions: roads not modelled as separate order (market only); no new config keys.
- Verified: ExpansionPlannerTest/RoleBalanceTest additions, GameTest doctrineBehaviours (18 GameTests); full check -> CHECK: PASSED. Next step: M45.

## 2026-10-25 — M45: Inter-faction relations and real-server run
- core FactionRelations (Stance TRADE/NEUTRAL/HOSTILE from the doctrine pair, degraded by border incidents; 3+ incidents = hostile); Diplomat scales its relations gain by the stance. ExploitationWatcher counts incidents per village pair and exposes an admin `rrp territory incident <owner> <intruder>`; `rrp territory` now lists doctrines and relations. Fixed: `territory` was missing from the Commands dispatch switch (unreachable from the console).
- Server phase 5: two villages 150 blocks apart, badlands under village 2 (fillbiome): MERCHANT_CITY vs WARLIKE_EMPIRE, relation, forced incident (WAR), relation degraded. Top-down export .athanor/visuels/m45-borders.png (flat world, villages only; borders are logical chunk claims, border posts need builders).
- Verified: FactionRelationsTest, 18 GameTests, full check -> CHECK: PASSED. Next step: M46.

## 2026-10-25 — M46: Delivery
- README and TEST_GUIDE now document borders, claims, provocation, doctrines, relations and the config keys; FINAL_REPORT.md updated, STATUS: DONE.
- tester: no P0-P3 (jar fresh, config identical, docs match code). critic: docs stale (fixed), border/banner observability notes added to TEST_GUIDE; design limits listed in the report.
- Verified: full check -> CHECK: PASSED (18 GameTests, real server run).

## 2026-10-25 — Resume: request 01-4 planned
- Turned the diplomacy/geopolitics request into M47–M54 (relation index, caravans, treaties, mobilisation, siege engines, war resolution, real-server run, delivery). Next step: M47.

## 2026-10-25 — M47: Relation index
- core diplomacy/RelationMatrix (index -100..+100 per village pair, baseline from doctrine pair, drift toward baseline, clamp, snapshot/restore) + 6 config keys (relation_*); neoforge RelationService (pairs declared per tick, incidents -penalty, Diplomat visits +gain scaled by stance, persisted in villages.json "relations"); `rrp territory` prints index + stance. FactionRelations kept for doctrine base/incident view.
- Not yet driving the index: shortages and shared enemies (come with M49 decider); trade (M48).
- Verified: RelationMatrixTest, 18 GameTests, real server phase 5 (pattern updated); full check -> CHECK: PASSED. Known flakes (pre-existing): fillbiome timing and a 0-actor role action on the real server; a leftover server process locks files after a timed-out run.
- Next step: M48.

## 2026-10-25 — M48: Caravans
- core diplomacy/TradePlanner (surplus/deficit matching, fixed exchange ratios, ambush chance reduced by escorts; JUnit TradePlannerTest) + neoforge CaravanService (wandering-trader caravan with guard escorts, withdraw from origin warehouse, swap at destination, return; success raises relation, ambush/loss lowers it; abort returns goods on shutdown) + CARAVAN_* config keys, `rrp` command hook, lang keys.
- GameTest `caravan`: iron for wheat delivered between two warehouses.
- Verified: full check -> CHECK: PASSED (19 GameTests, real server run). A leftover server process from an earlier run locked world files; killed it.
- Next step: M49.

## 2026-10-25 — M49: Treaties and declarations
- core diplomacy/Treaty (PEACE/TRADE_TREATY/DEFENSIVE_ALLIANCE/WAR per pair, declareWar breaks the treaty and drags defender's allies in) + DiplomacyDecider (pure rules; JUnit DiplomacyDeciderTest) + neoforge DiplomacyService (periodic evaluation, announced ultimatum with delay, Diplomat visit calms and cancels it, persisted in villages.json "treaties"), 6 diplomacy_* config keys, lang keys, `rrp diplomacy [evaluate]`; caravans skip pairs at war.
- Fixed a flake: TerritoryService.ensureDoctrine now loads each sampled chunk (an unloaded chunk returned the generator biome).
- GameTest `diplomacy` (alliance vs common enemy, shortage ultimatums, calm, war opens, ally joins). Verified: full check -> CHECK: PASSED (20 GameTests, real server).
- Next step: M50.

## 2026-10-25 — M50: Mobilisation and local equipment
- core diplomacy/Regiment (size capped by population share, gear priority sword > armor > bow bought from ore/wood stock, strength formula; JUnit RegimentTest) + neoforge MobilizationService (guards form a regiment at war, equipped from the warehouse, march to the enemy Town Hall, demobilise at peace), 5 mobilization_* config keys, lang keys, `rrp regiment`.
- GameTest `mobilization` (equip from stock, cap, strength, demobilise). check.sh: deletes run/rrp-*-world before the GameTests (stale level-name caused mass failures) and the server step timeout is 840 s (top-down export pushed it past 600 s).
- Verified: full check -> CHECK: PASSED (21 GameTests, real server run).
- Next step: M51.

## 2026-10-25 — M51: Siege engines
- core diplomacy/Siege (Engine HP, Budget cap, next-engine order, defender damage; JUnit SiegeTest) + neoforge SiegeService (builders assemble a ram then a catapult from new blueprints near the enemy border, ram breaks doors/gates, catapult breaches wall blocks, defenders' guards destroy engines, per-siege block cap, breach logged), 11 siege_* config keys, lang keys, `rrp siege`, ram/catapult blueprints x5 biomes (tools/gen_blueprints.py).
- GameTest `siege` (ram first, door costs 2 of the cap, catapult breach never beyond the cap, engine destroyed leaves no blocks, siege lifted at peace). Terrain-safe targeting: never below the village floor.
- Verified: full check -> CHECK: PASSED (22 GameTests, real server run).
- Next step: M52.

## 2026-10-25 — M52: War resolution
- core diplomacy/WarOutcome (power = strength x survivors x (1 + breach share); REPELLED / PILLAGE / ANNEXATION / DESTRUCTION by power ratio, destruction needs a full breach cap; capped loot and tribute; JUnit WarOutcomeTest) + neoforge WarResolution (ripens after war_resolve_min_s on dead regiment, full breach cap or war_resolve_max_s; pillage from the loser warehouse, annexation = claims transferred in TerritoryService + periodic capped tribute persisted in villages.json, destruction removes the hall and merges the population into the nearest village; ceasefire sets PEACE and restores the relation), 12 war_* config keys, lang keys, `rrp war`.
- GameTest `warresolution` (annexation verdict, ceasefire, relation restored, claims, tribute and pillage within caps). Tribute is a share of the loser's stock per day (no production meter exists): see DECISIONS.
- Verified: full check -> CHECK: PASSED (23 GameTests, real server run; first server run hit the known flaky phase-5 biome doctrine, passed on rerun).
- Next step: M53.

## 2026-10-25 — M53: Two-village real-server war run
- New tools/run_war_server.sh (phase 6 of check.sh, fresh rrp-war-world): two villages 150 blocks apart; relation/trade treaty, caravan delivery, 4 border incidents, ultimatum + war declaration, regiments mobilised, ram assembled, ANNEXATION verdict with tribute, demobilisation, all asserted in the server log; top-down export .athanor/visuels/m53-war.png. `check.sh quick war` runs it alone.
- Fixed siege.assembled/destroyed messages showing a doubled "[Res Publica]" prefix (SiegeService.kindName uses lang.get).
- Next step: M54 (delivery).

## 2026-10-25 — M54: Delivery
- README/TEST_GUIDE describe relations, caravans, treaties, mobilisation, sieges, outcomes, commands and config keys. tester sub-agent: 2 P1 (unloaded-chunk survivor count; pillage/tribute goods lost on failed deposit) fixed in WarResolution; critic: 7/10, no blocking P0/P1 (remaining: weak visual proof, docs terse). FINAL_REPORT STATUS: DONE.
- Verified: full check -> CHECK: PASSED.

## 2026-10-25 — Resume 02-5 (framing)
- Request "player integration and freedom" turned into M55–M62 (roles, sovereign founding/laws/planning, mercenary contracts, merchant arbitrage, political map + diplomatic register, delivery). Planning only, no code.
- Next step: M55.

## 2026-10-25 — M55: Player profile and roles
- core/player PlayerRole + PlayerLedger (default OBSERVER, role switch with config cooldown, Observer return always free, emerald purse; JUnit PlayerLedgerTest + persistence round-trip test). neoforge PlayerService (MC-free holder) + PlayerCommands (`rrp role show|set|purse deposit|withdraw`), persisted in villages.json "players"; /rrp now open to all players for `role`, other subs stay op-only (error.op_only). Config role_switch_cooldown_s, lang keys.
- Observer guarantee: nothing in the mod targets players by role; no new messages are sent unprompted.
- Verified: full check -> CHECK: PASSED. Next step: M56.

## 2026-10-25 — M56: Sovereign — found a faction
- core/player/Founding (pure verdict: role, purse, min distance, max villages; JUnit FoundingTest). neoforge SovereignService: a SOVEREIGN completing a Town Hall (lectern on gold block) founds a village owned by the player (cost from purse, hall registered, scan), AI keeps running it offline. VillageSite.owner persisted in villages.json, shown in `rrp status`. Config found_cost / found_min_distance / found_max_per_player, lang keys. GameTest sovereignfounding (observer refused, too poor, ok, owner, purse, min distance).
- Fixed the phase-5 real-server test: fillbiome now fills 5 layers in 32-wide slices (thin 1-layer fills left scattered plains, doctrine flipped to MERCHANT).
- Verified: full check -> CHECK: PASSED. Next step: M57.

## 2026-10-25 — M57: Sovereign — laws and taxes
- core/player Taxation (clamp, yield, unrest), VillageLaws, Laws (embargo both ways, conscription cap); JUnit LawsTest + persistence round-trip. neoforge LawService: `rrp law` (borders, ban, conscript, embargo) and `rrp tax set|show` for the ruler (open to non-ops), daily levy into the warehouse, loyalty loss above tax_free_burden, building ban event. Read by MobilizationService (share), CaravanService (embargo), ExploitationWatcher (open borders). Laws persisted in villages.json "laws". Config law_conscription_max, tax_*.
- GameTest lawsandtaxes (levy, unrest, cap, embargo, build ban, observer exempt); sovereignfounding now wipes its hall (leak broke townhall/exploitation).
- Verified: full check -> CHECK: PASSED. Next step: M58.

