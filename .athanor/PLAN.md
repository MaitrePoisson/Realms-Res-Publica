# Plan — Realms & Res Publica (Paper 1.20.4 plugin, Java 17)

> Older completed milestones: `.athanor/archives/PLAN_TERMINE.md`.

> Completed milestones M1–M20 (written in French): `.athanor/archives/PLAN_TERMINE.md`.

Docs: `.athanor/docs/REQUIREMENTS.md`, `DESIGN.md` (incl. §Clarifications and §Headless server constraints), `ARCHITECTURE.md`. Java package `rrp`. Bug severity: P0 = crash/data loss; P1 = essential feature wrong; P2 = minor. Zero open P0/P1 to deliver. Quote every path (the folder contains "&" and spaces).

## Port to NeoForge 1.21.1 (NeoForge 21.1.x, Java 21) — M23–M30
Rules: rrp.core and rrp.sim stay unchanged (JUnit + simulator keep passing). New layer `rrp.neoforge` replaces `rrp.bukkit`; server-side logic only (no client screens/rendering; chat + action bar), one jar for singleplayer (integrated server) and dedicated server. Java 21 JDK goes into $TOOLS/jdk21 (Gradle toolchain). Old Paper plugin is deleted only in M30, after the port is verified. Keep Paper code as the reference to port from (`git show` / the folder) until then.

## Local economy and autonomous urbanism — M31–M39
Rules: same as the port (server-side only, one jar, no client assets; vanilla blocks only; rrp.core changes allowed only with JUnit updated; every tunable in config.yml + lang_fr.yml keys, covered by LangKeysTest/SimConfigTest). Everything autonomous: no player action required (commands only for debug/forcing). Work is spread over ticks (BehaviorScheduler), persisted in villages.json, resumes after restart. Decision: the "Town Hall" master block is a vanilla structure marker (lectern on a gold block, built by the scanner/builder) so no registered block/client asset is needed.

## Territory and factions — M40–M46
Rules: same as M31–M39 (server-side only, vanilla blocks, one jar, tunables in config.yml + lang_fr.yml, core changes with JUnit). Pure logic goes in rrp.core / rrp.neoforge.territory (JUnit), world code in GameTests. Persisted in villages.json. Request text is truncated ("Génération de f…"): scope = border generation (markers, influence map, per-chunk claim/exploitation radius, provocation/war on foreign chunks) + emergent doctrines by starting biome.

## Diplomacy and geopolitical events — M47–M54
Rules: same as M40–M46 (server-side, vanilla blocks, one jar, tunables in config.yml + lang_fr.yml, core logic with JUnit in rrp.core.diplomacy / rrp.neoforge.diplomacy, world code in GameTests, persisted in villages.json). Scope: relation index, caravans, treaties/war declarations, armed regiments, siege engines, conflict resolution.

## Player integration and freedom — M55–M62
Rules: same as M47–M54 (server-side only, vanilla blocks/items, one jar, no client screens: chat with clickable components, books, vanilla filled maps; tunables in config.yml + lang_fr.yml keys covered by LangKeysTest; pure logic in rrp.core.player (JUnit), world code in GameTests; persisted in villages.json; no mod forces anything on a player who chose Observer).

## Technical constraints and performance — M63–M67
Rules: same as M55–M62 (server-side, vanilla blocks, one jar, tunables in config.yml + lang_fr.yml keys covered by LangKeysTest, pure logic in rrp.core.abstractsim (JUnit), world code in GameTests, persisted in villages.json). Request: (1) villages whose chunks are not loaded run a cheap mathematical simulation (probabilistic harvests, abstract army movement on the global map); full physical behaviour/pathfinding only where chunks are loaded; (2) full compatibility with vanilla biomes and terrain-generation mods (Terralith): no hard-coded biome lists that break on unknown biomes.

- [x] M64 : Abstract economy (probabilistic harvests) — Files: core/abstractsim/AbstractEconomy. Acceptance: for ABSTRACT villages, harvest/consumption/births use expected-value + seeded binomial draws per `abstract_step_ticks` from role counts and biome yield (no entity ticking), catching up elapsed ticks on a large dt in O(1) steps; resuming ACTIVE reconciles stocks without duplication or loss; JUnit shows abstract result within tolerance of the full Simulation over N days; GameTest on a far village's stock evolving.
- [x] M65 : Abstract armies and caravans on the global map — Files: core/abstractsim/AbstractMarch, neoforge hooks in WarService/CaravanService. Acceptance: regiments and caravans of ABSTRACT sources/targets move as points along straight or road-weighted routes at a configured speed, arrival resolves the siege/trade by existing resolution maths (WarResolution) without loading chunks; when the route enters an ACTIVE area the party materialises physically (and vice versa); JUnit on march/ETA/arrival, GameTest of an abstract war ending and a caravan delivering.
- [x] M66 : Terralith / modded biome compatibility — Files: core/territory/BiomeClassifier, neoforge adapters. Acceptance: doctrine, yields, village site suitability classify any biome id (vanilla, `terralith:*`, unknown namespace) by tags (`is_forest`, `is_mountain`, `is_ocean`…) with name-keyword and temperature/downfall fallback, never throwing on an unknown id; no hard-coded vanilla-only maps left; config override list `biome_overrides`; JUnit with a table of vanilla + terralith ids, GameTest with a custom-registered biome id.
- [x] M67 : Performance proof and delivery — Files: tools perf script, README/TEST_GUIDE, .athanor/FINAL_REPORT.md. Acceptance: server run with e.g. 40 villages, 1 player: `tick query` / TickService cost shows ABSTRACT mode far cheaper than all-ACTIVE (numbers in report, `.athanor/visuels/`); docs for LOD config and biome compatibility; tester then critic, no open P0/P1; FINAL_REPORT.md STATUS: DONE; full check CHECK: PASSED.
