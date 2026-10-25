# Final report
STATUS: DONE

## In short
Realms & Res Publica is a server-side NeoForge 1.21.1 mod (Java 21) that makes villages live: seven villager roles, gauges, demography, a crisis chain up to civil war, an autonomous urban layer (Town Hall, expansion, roads, defenses), territory and doctrines, diplomacy (relations, caravans, treaties, wars, sieges, four outcomes) and, new in this request, four player roles: Observer, Sovereign, Mercenary and Merchant.

## How to launch and test
Copy `deliverable/RealmsResPublica.jar` into `mods/` of a NeoForge 21.1 / Minecraft 1.21.1 install (solo or dedicated); `deliverable/config.yml` is the annotated config. `TEST_GUIDE.md` has commands, a manual checklist and the player-role scenarios. `bash .athanor/check.sh` runs JUnit, the balance simulator, 29 GameTests and two real dedicated-server runs.

## What was built
Player layer (M55-M61): role ledger and emerald purse (`/rrp role`); Sovereign founding by Town Hall, laws (borders, building ban, conscription, embargo), taxes, urban planning zones respected by the expansion planner; Mercenary contracts with escrowed pay (camp clearing, war); free Merchant market with stock-based prices, spread and daily saturation; political map on cartography tables; diplomatic register with clickable emissary proposals.

## Deviations from the request and why
- Player-role flows cannot be driven from a headless dedicated-server console (no player can log in): they are verified by GameTests with mock players; village, war and persistence flows run on a real server. 
- No client-side GUI: chat with clickable components, books and vanilla maps instead.

## Known limits and remaining bugs
- Merchant sale income is minted (not debited from the village); buy payments go to the warehouse.
- Contract completion needs only one kill; emissary proposals are instant (no travel, no cooldown); the register shows 14 lines; political map has no legend and faction colours may collide; law/tax/plan act on a ruler's first village only; only the player's own camp-to-village link is lost on restart (relation shift skipped). Details in `.athanor/BUGS.md` (P2/P3).

## Important decisions
See `.athanor/DECISIONS.md`: vanilla-only assets, one jar, every tunable in config.yml, French messages, pay defaults raised after the critic review (found_cost 32, contract_pay_base 12, per_raider 3, war_pay 40).

## Figures (milestones, tests, commits, duration)
62 milestones, 29 GameTests, JUnit suites for core logic, balance simulator, 2 real-server runs; tester and critic passes at M62.

## Ideas for later
Emissary entities with travel time, contract kill thresholds, paginated register, map legend, village-treasury-backed market, per-village targeting for law/tax/plan.

## Request 03 (M63-M67): scalable simulation
Villages are ACTIVE (entity-ticking chunk) or ABSTRACT (seeded probabilistic economy, armies and caravans as map points, reconciled on wake-up); BiomeClassifier handles vanilla, Terralith-style and unknown biomes via tags, name keywords and climate, with `biome_overrides`. Performance (`bash tools/run_perf_server.sh 40`, `.athanor/visuels/m67-perf.txt`): mod cost 2.30 ms/tick ACTIVE vs 0.24 ms/tick ABSTRACT on a real server with 40 villages. 34 GameTests, JUnit, tester and critic passes done.
Limits: vanilla tick time was equal in both modes in that test (force-loaded chunks, NoAI villagers, flat world); no real Terralith run; single scaling point; abstract ledger not persisted across restarts; one flaky GameTest (exploitation) seen once. See `.athanor/BUGS.md`.
