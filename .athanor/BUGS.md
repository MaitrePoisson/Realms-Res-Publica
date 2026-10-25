# Bugs — M20 (independent tester, 2026-10-23)

Format: `- [ ] Pn — title — how to reproduce — probable file:line`. P0 = crashes/loses data; P1 = essential feature wrong; P2 = minor.
Reproduction probes: `.athanor/tools/M20Probe.java` (core, no server), `.athanor/tools/m20_console.sh` and `m20_perf.sh` (disposable Paper server `.athanor/server_m20`, logs in `.athanor/.verif/m20_*.log`).

## P0

- [x] P0 — villages.json unreadable or from an old version: the villages are lost permanently on shutdown (no backup) — put in villages.json a village missing a required field (e.g. remove `birthTimer` from the 2nd of 3 villages), start the server: ERROR « lecture … impossible : NullPointerException », loading stops at the faulty village, then `onDisable` rewrites the file with only village-1 (verified: 3 villages → 1). A syntactically broken JSON gives an empty file `[]`. No `.bak`, no tolerant reading (`get(..)` without `has`), `version` written but never read — `Persistence.java:85-103` (load), `Persistence.java:141-166` (fromJson), `RrpPlugin.java:86-88` (unconditional flush on shutdown). — **FIXED (M20)**: village-by-village tolerant reading (default values), `.bak-<date>` copy of the faulty file; selftest case for damaged persistence

## P1

- [x] P1 — A second exile after an "autonomous camp" truce creates a camp with the same identifier and overwrites the first — core: exile → truce with persistent cause (`makeAutonomous` sets `parentId=null`), then the cause disappears, the village goes back to contestation and exiles again: `campOf` does not find the autonomous camp, a new state `village-1-camp` is created (2 states with the same id in the world, verified by M20Probe A); `registry.adopt` → `sites.put` replaces the site (tents, barrel and villagers of the autonomous camp leave the registry, then are rediscovered as loyalist "village-N") — `CrisisEngine.java:201-206`, `WarService.java:249-256`, `VillageRegistry.java:75-94`. — **FIXED (M20)**: unique camp identifier (`freeCampId`); JUnit test secondExileNeverReusesTheIdOfAnAutonomousCamp
- [x] P1 — Insecurity alone can never exceed contestation (criterion "insecurity → exile → war" not met) — village fed, enough beds, 5 permanent monsters, no guard, 3 h simulated: maximum level CONTESTATION, morale 60, loyalty 60 stable, no exile (M20Probe D); the check's simulator confirms it ("insecurity … crise finale STABLE", never EXIL). The target morale (10 + 0.5×satiety + 20 bed) stays ≥ `loyalty_low_morale` (55), so loyalty never drops below `exile_loyalty` (30) — `Gauges.java:60-71`, `CrisisEngine.java:158`, defaults `SimConfig.java:26-35` (balancing to review: the security malus should be lasting below 55, or insecurity should act directly on loyalty). — **FIXED (M20)**: `morale_insecurity_penalty` (30) when the threats are not covered; the simulator reaches EXIL then GUERRE_CIVILE; JUnit test
- [x] P1 — Any automatically discovered village has no warehouse: stock 0 → permanent famine → contestation (≈ 2 min) → exile, without any real cause or warning — place 2 villagers and a bed without `warehouse set` (or let 2 villagers move more than 64 blocks away from a village: "village-3 is rebelling: contestation rises (famine)" observed in `m20_perf.log`), `respublica tick 6000`: exile observed (m20_console_1.log: 5 exiles out of 6). `indicators` does not take the absence of a warehouse into account; only `/respublica status` says "no warehouse" — `CrisisEngine.java:42-48`, `CrisisService.java:107`, `VillageScanner.java:243-245` (suggestion: no crisis nor hunger gauge while `hasWarehouse()` is false, or detect a barrel of the village). — **FIXED (M20)**: `foodManaged`: without a warehouse there is no hunger nor famine; JUnit tests
- [x] P1 — A civil war ends "by elimination" and the camp is removed from the registry when the chunks unload (player gone) — a camp whose villagers are no longer loaded has 0 inhabitants and 0 guards (`sync` removes the absent ones): `CrisisEngine.step` emits `WarEnded(truce=false)` then removes the camp from the list, `CrisisService.stepOnce` does `registry.remove`; tents, barrel and villagers remain in the world and will be rediscovered as a loyalist village (M20Probe C; mechanism already observed: a camp of 5 exiles dropped to 2 in m20_console_1.log when its chunk was not loaded) — `CrisisEngine.java:245,275-277`, `VillagerAdapter.java:128-177`, `CrisisService.java:114-117` (suggestion: do not advance nor conclude the crises of a village whose central chunk/warehouse is not loaded). — **FIXED (M20)**: unloaded villages (`VillageSite.loaded`): crises and gauges frozen, `active`; JUnit test inactiveVillageIsFrozen
- [x] P1 — The forester cuts down the trunks of the village houses — `Forester.isLog` retains any non-stripped `*_LOG` block within 16 blocks, without distinguishing tree and construction (village hut pillars, player cabins); it replaces it with a sapling or air without any tree-membership test (leaves) — `Forester.java:29-37,53-56` (the miner also digs under the village: `Miner.java:34-35`, STONE/ANDESITE up to 6 blocks under its feet). — **FIXED (M20)**: the forester only cuts logs carrying foliage; the miner only takes what is exposed; selftest case strengthened
- [x] P1 — The proof "famine/overpopulation/insecurity → contestation → exile → war" is not made end to end — the server cases prime the state (`primeForExile`, `warSetup` sets `EXIL` + `warTimer = war_after-1` + `pin()` loyalty 5 at each step); natural exile proven only for famine (CrisisCases) and overpopulation (simulator, not server); no case goes from famine to war without priming; insecurity stops at contestation (see bug above) — `CrisisCases.java:268-272`, `WarCases.java:75-100`. — **FIXED (M20)**: selftest case "full chain: famine → contestation → exile → civil war" without priming

## P2

- [ ] P2 — Stale stock used when the barrel is not loaded: "phantom" food — `warehouse.refresh` returns false but the state keeps the old stock; the gauges eat and `BirthService` births with that stock while `withdraw` removes 0 from the real barrel (free births and meals, silent failure) — `GaugeTicker.java:91-105`, `BirthService.java:74-89`, `WarehouseService.java:114-117`.
- [ ] P2 — An unloaded exile is not moved (only a WARN): it stays at the village, is re-adopted through its PDC (old faction) while the camp state counts it — `CrisisService.java:207-211`.
- [ ] P2 — The performance test does not measure the heavy behaviors: its villages have no warehouse, so farmer/forester/miner/steward/builder exit immediately; only the average is measured (ceiling 5 ms, measured 0.014) — with a warehouse + 2×40 villagers, real measure (`tick query`, m20_perf.log): average 5.5 ms, P95 15 ms, P99 19 ms per behavior cycle (scan (2r+1)²×height per villager, several times for forester/miner); extrapolated to 5×40: 40-50 ms every 10 s — `PerfCases.java:29-33`, `RoleBehavior.java:34-52`, `Forester.java:28-30`.
- [ ] P2 — Balancing values hard-coded, absent from config.yml: `Guard.STRIKE_DAMAGE=20`, `WarService.REACH=2.5` and `STEP=8`, `+20` reconciliation loyalty (`WarService.java:263`), `Steward.MAX_SERVED/MAX_MEALS/SERVE_MARGIN`, `Farmer/Forester/Miner.MAX_PER_RUN`, `CampBuilder.CAMP_RADIUS/GAP/SLOTS`, `RoleBalance.minPopulation` thresholds (3/4/5), initial gauges 70/80/70 (`VillagerState.java:17`).
- [ ] P2 — Key `day_length_s` unused (never read in the code) and `food_per_villager_per_day=4` inconsistent with the real consumption (3 pts/min ÷ 25 pts/bread × 20 min = 2.4 breads/day): the famine indicator does not track what the villagers eat — `SimConfig.java:42-43`, `CrisisEngine.java:35-40`.
- [ ] P2 — Disobedience is not applied to civil war: guards with low morale fight anyway — `WarService.java:125-153` (no call to `Gauges.canWork`).
- [ ] P2 — The guard strikes at 16 blocks without moving (instant damage) and any `Enemy` in radius is targeted — `Guard.java:24-38`.
- [ ] P2 — The diplomat raises relations with the nearest village, whatever the distance and without any link to the dissident camp; the truce is read on the global `village.relations()` — `Diplomat.java:22-42,44-60`, `CrisisEngine.java:63-65`.
- [ ] P2 — Misleading error messages: `status inconnu` replies "Aucun village ici (précisez son identifiant)"; `famine|overpopulate|insecurity inconnu` reply "Usage …" instead of "village inconnu"; the completion of `setrole` proposes the English names (farmer…) while the usage displays the French names — `Commands.java:131-140,224-229,313-316`.

- [ ] P2 — Debug commands leave permanent entities: `insecurity` spawns persistent zombies that do not burn and kill villagers (no cleanup command; each call adds ≥ 1), `overpopulate` adds ≥ 1 villager even when overpopulation is already reached — `Commands.java:258,278-290`.
- [ ] P2 — `/respublica tick 1000000` blocks the main thread (≈ 2 s measured with 2 villages) and the `selftest` run on a real server generates ~2500 chunks at 12,000 blocks, a 6 s freeze ("Can't keep up") and rewrites villages.json (`persistence().flush()`/`load()`) — no confirmation nor guard — `Commands.java:77-81`, `VillageCases.java:188-215`.
- [ ] P2 — The camp is placed without testing existing constructions (only water, lava, leaves, logs, ice count): it can replace built blocks; it loads ~16 chunks synchronously during the crisis tick; after reconciliation the barrel, food and tents remain without owner — `CampBuilder.java:78-93,100-109`, `WarService.java:259-283`.
- [ ] P2 — A baby appears at the centre of the village without checking that it is free (centre = average of positions, may be inside a wall or in the air) — `BirthService.java:118-124`.
- [ ] P2 — Weak tests: the "coverage" case only checks that case names contain keywords (tautological), "reload" checks `simConfig() != null`, no case measures the real reloading of a modified value — `CommandCases.java:107-124,127-141`.
- [ ] P2 — Deliverable missing: `deliverable/RealmsResPublica.jar`, annotated `config.yml` and `TEST_GUIDE.md` (criterion 6 of REQUIREMENTS.md) do not exist (to handle before delivery).

## M21 — critic findings (polish)
- [x] Relations/diplomat: a rupture resets relations to 0 and the diplomat negotiates with the other camp (CrisisEngine, Diplomat, simulator).
- [x] Births blocked during famine/crisis (DemographyEngine.canBirth); no birth on stale stock (BirthService).
- [x] Builder keeps `builder_spare_beds` free beds; exile capped by `exile_max_share`, one guard always stays loyal.
- [x] `/respublica status` shows average gauges and timers, French faction label; stale warehouse stock ignored (GaugeTicker).
- [x] Behaviors spread over ticks (`behaviors_per_tick`); reconciliation bonus in config (`reconcile_loyalty_bonus`).
- [ ] P2 (accepted, not changed): guards with low morale still fight in civil war (canWork not applied to war, would stall the selftest chain).

## M21 tester
Run: `check.sh quick` PASSED (JUnit + 4 sim scenarios). `check.sh quick server` FAILED: selftest 54 ok / 3 FAIL (log: .athanor/.verif/server.log, selftest-result.json). Cause: M21 changes made without updating the selftest cases (CrisisCases.java / BehaviorCases.java).
- [x] P1: selftest "exil : feux, tentes de laine, lits, tonneau, ..." (CrisisCases:143) expects 5 exiles out of 6 ("tous sauf un"), now capped by exile_max_share=0.5 (+1 guard stays) -> saw 2. Case and its camp assertions (5 lits, 5 tentes, 5 exiles) must be rewritten for the cap.
- [x] P1: selftest "exil : un second exil agrandit le meme campement" (CrisisCases:199) expects first exile = 6 of 8 villagers, cap gives <=4 (saw 3), so camp pop 6/7 assertions fail.
- [x] P1: selftest "batisseur : ne batit ni sans assez de bois..." (BehaviorCases:280) sets beds=population and expects no hut; builder_spare_beds=2 now makes it build. Test must set beds = population + spare_beds.
- [ ] P2: BehaviorScheduler.pending is unbounded: if a cadence cannot be drained within behavior_interval_s (villages x villagers > behaviors_per_tick*200 per 10 s, i.e. >800 villagers) new Turns pile up on top of old ones (duplicates per villager, stale `runs`). Suggest skipping enqueue when pending is non-empty or capping its size. Not reproduced at current scale.
- Verified OK by reading diff: invalid/unloaded villagers are skipped via isValid(); accelerated tick purges budget = per_tick*ticks; diplomat counterpart picks camp/parent; status command prints gauges/timers/French faction label (selftest "commandes" passes); births blocked in crisis/famine (canBirth) with stale-stock guard.

## NeoForge delivery tester
`check.sh quick gametest` PASSED (8 GameTests). Jar has neoforge.mods.toml, 0 rrp/bukkit classes, config.yml identical. README/TEST_GUIDE commands and config path (config/respublica/config.yml) consistent with Commands.java/ConfigLoader.java.
- [ ] P3: Bukkit leftovers in comments: src/main/java/rrp/core/{DemographyEngine:10,Gauges:10,RoleBalance:11,SimEvent:6,SimEvent:19,Simulation:9,VillageState:9} mention "Bukkit"/"couche Bukkit"; should say NeoForge adapter.
- [ ] P3: src/test/java/rrp/LangKeysTest.java:23-24 still references removed legacy-paper/java/rrp/bukkit (transitional code).

## M39 tester
Checks: `check.sh quick gametest` PASSED (15/15), `check.sh quick` PASSED. config.yml == deliverable/config.yml. All documented commands exist (respublica|rrp: tick, village, warehouse, townhall set|info|build, status, inspect, setrole, famine, overpopulate, insecurity, reload).
- [ ] P0 deliverable/RealmsResPublica.jar is STALE (built 2026-10-23 23:37, older than M37/M38 commits): contains no rrp/neoforge/urban classes and only data/respublica/structure/arena.nbt; the biome blueprints (big_house_*, farm_* ...) present in src/main/resources are missing. Rebuild and recopy the jar to deliverable/.
- [ ] P3 Unbounded-loop skim of urban/: loops in ConstructionSite, DefensePlanner, RoadNetwork (A* capped by MAX_NODES) are budget-bounded; hasChunk checks present in DefensePlanner, RoadNetwork, TerrainLeveler. No obvious bug; ConstructionSite and ExpansionPlanner have no hasChunk check (verify placement targets cannot be in unloaded chunks).

## M39 resolutions
- [x] P0 stale deliverable jar: rebuilt; check.sh (full mode) now copies build/libs jar and config.yml into deliverable/.
- [x] P1 growth rules undocumented: added to TEST_GUIDE.md.
- [ ] P1 (accepted): dropped build orders are silent (see `/rrp status`); visual proof shows huts and roads only (.athanor/visuels/m38-village.png).
- [ ] P2 (accepted): Town Hall is a lectern on a gold block (vanilla marker, no custom block); ring slots may overlap defense ring on unusual config.
- [ ] P3: no multi-village pathfinding timing beyond server phase (P95 2.0 ms).

## M46 tester
- Verdict: PASS, no P0-P2. jar fresh (rrp/neoforge, core/territory, nbt blueprints present), config.yml identical, check quick PASSED.
- P3: none blocking; "doctrines" is a concept (derived from biome), not a config key, docs consistent.

## M54 tester
Checks: `check.sh quick` PASSED; deliverable jar (08:32, == build/libs size) and deliverable/config.yml == src config; all README/TEST_GUIDE config keys and `rrp` subcommands (diplomacy/caravan/regiment/siege/war/territory incident) exist. Static review of diplomacy code (no server run).
- [x] P1 — War ends prematurely when soldiers' chunks are unloaded: `alive()` counts via level.getEntity(uuid), null for unloaded chunks, so aliveShare=0 and ripe() resolves the war after war_resolve_min_s (wrong verdict) — repro: start war, move all players away so guards' chunks unload, wait min age — src/main/java/rrp/neoforge/MobilizationService.java:61-69 + WarResolution.java:~118,133
- [x] P1 — Pillage/tribute destroys goods: withdraw from loser then deposit() return (leftover stack) is ignored; if winner warehouse is full or its chunk unloaded (only level!=null checked) goods vanish — repro: winner barrel full or unloaded during pillage/payTribute — src/main/java/rrp/neoforge/WarResolution.java:~180-186 and ~232-239
- [ ] P2 — Caravan finish(): deposit result to origin ignored, and no loaded-check (trader may be teleported on timeout to unloaded warehouse) => returned goods lost silently — CaravanService.java:~257-260
- [ ] P2 — WAR treaty persisted but FORCES/SIEGES/AGES/PENDING ultimatums are not: after restart a persisted war has strength 0 on both sides and resolves REPELLED for the first id after min age; pending ultimatums are dropped — Persistence.java:77-100, WarResolution.java:29, DiplomacyService.java:33
- [ ] P2 — Persistence.load(): one bad treaty state (valueOf) or missing "baseline" key throws before the villages loop, so ALL villages are skipped (n=0); backup is made but next flush overwrites villages.json with empty — Persistence.java:129-150
- [ ] P2 — Destroyed/merged village leaves stale relations, ALLIANCE/TRADE treaties and tributary entries (as winner) with its id; ownerOf can ping-pong on A->B and B->A tributaries (no cycle guard, 5-hop cap) — WarResolution.java:~195 (destroy), :46
- [ ] P3 — DiplomacyService.evaluate/RelationService.onTicks are O(n^2)/O(n^3) per call (common-enemy loop inside pair loop) — DiplomacyService.java:~85-112

## M62 tester
Checks: `check.sh quick gametest` PASSED (29/29 GameTests, log .athanor/.verif/tester.log); deliverable jar identical to build/libs, contains neoforge.mods.toml and all player-layer classes, newer than every src file; deliverable/config.yml == src config; every README/TEST_GUIDE command (role, law, tax, plan, register, contract, market, ...) is registered and every backticked config key exists; every lang key used by the player layer exists in lang_fr.yml. Static review only for the items below (no real client available).
- [ ] P1 — Political map probably overwritten by vanilla terrain: map created via MapItem.create is not locked (data.locked=false), so MapItem.inventoryTick/update repaints terrain colours around the holder over the painted factions; fix: set data.locked=true after paint (repaint still allowed server-side) — repro: take a map from a cartography table, keep it in the hotbar and walk; colours near the player turn to terrain (unverified at runtime, GameTests only check paint()) — PoliticalMap.java:~84-90 (use) / paint
- [ ] P2 — Rulers of several villages can only govern the first one: law/tax/plan/contract post use ownedBy(...).get(0); harmless while found_max_per_player=1 but breaks if raised — LawService.java:157, PlanService.java:~85/~112, ContractService.java:~post command
- [ ] P2 — Contract refund of an escrow >64 emeralds that does not fit drops one oversized ItemEntity (count > max stack size); player-posted pay is unbounded — ContractService.java:332-335
- [ ] P2 — ORIGIN map (camp contract -> parent village) is not persisted: after a restart a completed camp contract no longer shifts the relation — ContractService.java:45,348
- [ ] P2 — Contract kills counted only while role is MERCENARY and any single kill pays out a camp contract cleared by others (kills>0); switching role mid-contract loses kills — ContractService.java:214,251
- [ ] P3 — `/rrp contract post ...` exists but is not documented in README/TEST_GUIDE; `abandon` with a non-number prints "null" id — ContractService.java:~395-420
- [ ] P3 — Faction map colour = id.hashCode() mod 12: two villages can share a colour — PoliticalMap.java:~116
- [ ] P3 — Cartography table always opens the map instead of the vanilla GUI (shift bypasses) — PoliticalMap.java:~49

## M62 critic (delivery)
- [x] P0 — Market sale of non-canonical items (emerald, coal, planks…) allowed cheap-for-dear swaps and emerald minting — MarketService.take now accepts only the canonical item per resource.
- [x] P1 — Buy payments vanished: now deposited as emeralds into the village warehouse.
- [x] P1 — Political map repainted by vanilla terrain: map is now locked (MapItem.lockMap).
- [x] P1 — Pay too low: found_cost 32, contract_pay_base 12, per_raider 3, war_pay 40 (tests updated).
- [ ] P2 — Sale revenue is still credited without debiting the village (arbitrage by design); kill thresholds for contracts, emissary delay/cooldown, register pagination (14 lines), role-switch cleanup, map legend: left for later (see FINAL_REPORT "Known limits").

## M67 tester
- P2: src/main/resources/lang_fr.yml lines 250 (help.lod) and 255 (lod.forced) are double-encoded UTF-8 mojibake ("dÃ©tail", "â€”"); players see garbage in /respublica help and lod force reply. Repro: grep 'Ã' lang_fr.yml. Fix: re-save as UTF-8 with "détail" and "—".
- P3: help.lod text omits `perf [reset]` subcommand (documented in README/TEST_GUIDE and implemented).
- Checks OK: check.sh quick gametest PASSED (34/34 gametests); `rrp lod`, `force`, `perf [reset]` registered; abstract_march_speed, abstract_road_factor, biome_overrides present and identical in src and deliverable config.yml; lod.* lang keys all present.

## M67 critic
- [x] P2 lang_fr garbled accents / help.lod lacks perf: fixed.
- [x] P2 "10x" claim misleading: TEST_GUIDE now states the limits (self-timer, NoAI, force-loaded, vanilla flat world); lod_grace_s advice added to README.
- [ ] P1 Terralith only covered by name/tag unit tests, no real Terralith run (not available offline); documented.
- [ ] P3 perf proof is a single point (40 villages), no scaling curve or variance.
- [ ] P3 flaky GameTest 'exploitation' (farmer worked in a foreign chunk) failed once in 3 runs; passes on rerun
