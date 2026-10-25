# Log

> Older entries: `.athanor/archives/JOURNAL_ANCIEN.md`.

## 2026-10-25 — M58: Sovereign — urban planning
- core/player ZoneType + Zones (plots, FORBIDDEN priority, grid spots; JUnit ZonesTest). ExpansionPlanner.plan(..., zones): farm/house/market/barracks go into a plot of their type (favoured), other sites avoid every plot, nothing in forbidden. neoforge PlanService: `rrp plan show|clear|zone <type> [x1 z1 x2 z2]` + wooden shovel two-corner drawing, claimed-land and size checks; persisted in villages.json "zones"; Steward reads them. Open to non-ops like role/law/tax.
- Tests: ExpansionPlannerTest zones, PersistenceTest.zonesSurviveReload, GameTest urbanplanning.
- Verified: full check -> CHECK: PASSED. Next step: M59.

## 2026-10-25 — M59: Mercenary contracts
- core/player Contract + Contracts (OPEN/ACCEPTED/DONE/FAILED/EXPIRED/CANCELLED, accept limit, abandon, timeout, reputation; JUnit ContractsTest). neoforge ContractService: autonomous villages auto-post CLEAR_CAMP (against their own dissident camps, pay base + per raider) and war contracts, pay escrowed in emeralds out of the warehouse (WarehouseService.count/take); `rrp contract list|status|accept|abandon|post camp|post war` (open to non-ops, mercenary role to accept, ruler to post). Kills counted by LivingDeathEvent; camp cleared or war won (WarResolution history) with at least one kill pays the purse; timeout/lost war fails, vanished objective cancels, escrow returns (retried while the warehouse is unloaded; a full one drops the rest). Relation shift on success. Persisted in villages.json (contracts, reputation). Config contract_* (9 keys).
- Tests: ContractsTest, PersistenceTest.contractsAndReputationSurviveReload, GameTest mercenarycontracts (camp clearing payout, late failure refund, war win, ceasefire refund).
- Verified: full check -> CHECK: PASSED. Next step: M60.

## 2026-10-25 — M60: Free merchant — price arbitrage
- core/player PriceModel (stock vs need price multiplier, spread, per-resource daily saturation window; JUnit PriceModelTest). neoforge MarketService: `rrp market prices|buy|sell <food|wood|ore|stone> <n>` (open to non-ops, merchant role required), real items moved to/from the village warehouse, purse charged/credited, relation index between the village and the trader's origin improves, MARKET_DAILY_LIMIT cap per village/resource. Config market_* (8 keys), lang_fr entries.
- GameTest freemerchant (observer refused, buy low in surplus village, sell high in starving one with profit, goods land in second warehouse, relation up, daily cap, empty purse).
- Verified: full check -> CHECK: PASSED. Next step: M61.

## 2026-10-25 — M61: Political map and diplomatic register
- core/player MapRaster (faction cells, borders, war hatching, caravan routes, village markers) and Proposals (trade/alliance via DiplomacyDecider, ceasefire, ultimatum). neoforge PoliticalMap: right-click on a cartography table gives/repaints a vanilla filled map named "Carte politique" (scale 3, colours match border banners); CaravanService.routes. DiplomaticRegister: `rrp register` with clickable [Commerce][Alliance][Cessez-le-feu][Ultimatum] for rulers (read-only otherwise), `rrp register propose <kind> <from> <to>`; DiplomacyService.ultimatum/conclude. `market` was missing from the command switch: fixed.
- Tests: PoliticalMapTest (JUnit), GameTest politicalmap (map colours, in-place refresh, clickable vs read-only, trade refused/accepted, ultimatum, ceasefire).
- Verified: full check -> CHECK: PASSED. Next step: M62 delivery.

## 2026-10-25 — M62: Delivery
- Docs: README (four player roles, config keys), TEST_GUIDE (player-role scenarios). tester: 29/29 GameTests, jar/config consistent, no P0. critic: market exploits (non-canonical items), vanishing buy payments, unlocked political map, low pay -> fixed (canonical-item sell only, payments to warehouse, MapItem.lockMap, found_cost 32 / pay 12+3/raider / war 40); remaining P2 in FINAL_REPORT.
- FINAL_REPORT.md STATUS: DONE. Verified: full check below.
- Request 03 (perf/abstract sim, Terralith) planned as M63-M67; first leftover uncommitted probe work from M62 to validate then commit.

## 2026-10-25 — M63: Chunk-activity tracker + LOD switch
- core/abstractsim Lod + LodPolicy (immediate wake-up, grace period before turning abstract; JUnit LodPolicyTest). neoforge ActivityTracker (every lod_check_ticks, village is live when its hall chunk is entity-ticking), VillageSite lod/forced fields, WarehouseService.loaded now false for ABSTRACT villages so every physical behaviour gate (behaviors, births, gauges, caravans, wars, market...) skips them. `rrp lod [force <village> active|abstract|auto]`; config lod_enabled/lod_check_ticks/lod_grace_s.
- GameTest lodswitch (30 GameTests pass). Also committed the leftover M62 probe driver, contract kills and register changes.
- Verified: full check -> CHECK: PASSED. Next step: M64 abstract economy.

## 2026-10-25 — M64: Abstract economy
- core/abstractsim AbstractEconomy (seeded binomial harvests per role from BEHAVIOR_INTERVAL, doctrine-based YieldProfile, Gauges for eating/morale, DemographyEngine for births with pending babies occupying beds, at most abstract_max_chunks chunks per gap) + 7 config keys abstract_*. neoforge AbstractService: stock baseline at sleep, exact diff deposited/withdrawn on wake, pending babies spawned (bounded by free beds); ActivityTracker.apply hooks it (automatic wake waits until the warehouse is readable).
- Tests: AbstractEconomyTest (expected value over 200 seeds, biome scaling, determinism, full Simulation comparison, beds), GameTest abstracteconomy (31 GameTests pass).
- Known limit: the abstract ledger is not persisted across a restart (stock gained while abstract is then replaced by the real warehouse content).
- Next step: M65 abstract armies and caravans.

## 2026-10-25 — M65: Abstract armies and caravans
- core/abstractsim AbstractMarch (point on a straight route, speed x road factor, ETA/progress; JUnit AbstractMarchTest) + AbstractRoutes (laid-road detection) + config abstract_march_speed / abstract_road_factor.
- MobilizationService: ABSTRACT villages raise a regiment from the in-memory state (guards, ore/wood) that marches as a point; re-formed when the LOD changes. WarResolution: an abstract army still on the road blocks resolution; abstract forces skip the alive-share check.
- CaravanService: abstract-endpoint caravans (stock in memory via take/put), abstract march with ambush, materialise (trader spawned) when the point enters a live village area, dematerialise when a physical caravan heads to a sleeping village outside live areas.
- GameTests abstractcaravan, abstractwar (33 pass). Full check -> CHECK: PASSED. Next step: M66 biome compatibility.

## 2026-10-25 — M66: Biome compatibility
- core/territory BiomeClassifier (override > tags > name keywords > temperature/downfall; never throws, UNKNOWN fallback; `cold` flag for the snow style) + parseOverrides; DoctrineSelector and BiomeStyle rebuilt on it (no vanilla-only maps). neoforge BiomeAdapter (holder tags + climate); config `biome_overrides` (string, SimConfig.biomeOverrides()).
- Tests: BiomeClassifierTest (vanilla + terralith table, tags, climate, overrides, null safety), GameTest biomecompat (every registry biome + an unregistered hot/dry Holder.direct -> desert; 34 pass).
- Next step: M67 performance proof and delivery.

## 2026-10-25 — M67: Performance proof and delivery
- tools/run_perf_server.sh + `rrp lod perf`; numbers in .athanor/visuels/m67-perf.txt; README/TEST_GUIDE docs for LOD config and biomes.
- tester: 34 GameTests pass, 2 lang bugs (fixed). critic: perf evidence weak (documented honestly), Terralith untested for real (documented). FINAL_REPORT STATUS: DONE. Full check CHECK: PASSED.
