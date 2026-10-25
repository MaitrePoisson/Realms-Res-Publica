# Design — Realms & Res Publica

## Concepts
- **Village**: set of villagers of one faction around a centre (configurable radius, default 64). Has a **communal warehouse** (designated barrel), a number of **beds** (real beds in the radius) and a **security** level.
- **Faction**: `LOYALIST` (original village) or `DISSIDENT` (camp). Each faction has an id.
- **Villager**: role, morale/satiety/loyalty (0–100), faction; stored in the PDC.

## Roles (behaviour)
| Role | Concrete action | Effect on the village |
|---|---|---|
| Farmer | harvests nearby ripe crops, replants, drops into stock | + food |
| Forester | cuts nearby logs (replants a sapling), drops | + wood |
| Miner | breaks ore/stone near their post, drops | + ore/stone |
| Steward | manages the warehouse: redistributes food to the hungry, counts stocks | fair access to food |
| Builder | spends wood to build a small hut with a bed | + beds |
| Guard | patrols, spots monsters, attacks them | + security |
| Diplomat | meets the other faction, negotiates | + relations, calms war |

## Gauges
- **Satiety**: drops over time; rises by eating from the warehouse (Steward); 0 = starving.
- **Morale**: tends toward a target = f(satiety, assigned bed, security, overpopulation).
- **Loyalty**: tends toward a target = f(village average morale, presence of a diplomat); drops when morale is low.

## Demography
Periodically per village: if stored food ≥ threshold AND a free bed AND population < max → birth (consumes food). Otherwise nothing.

## Crises (escalation per village)
Real indicators: famine (days of food left < threshold), overpopulation (villagers > beds × ratio), insecurity (nearby hostile threats vs guards).
1. **STABLE**
2. **CONTESTATION**: indicator sustained for `contest_after` seconds → morale/loyalty drop faster, disobedience (villagers stop working below a morale threshold, deterministic).
3. **EXILE**: loyalty < `exile_loyalty` for `exile_after` → the discontented leave and build a **dissident camp** (campfires, wool tents, beds) at `camp_distance`.
4. **CIVIL WAR**: loyalist/dissident tension sustained for `war_after` → fighting between guards/soldiers of both sides. End: one side has no fighters left, or a diplomat's truce → reconciliation or camp autonomy.
When the indicator disappears, the escalation recedes slowly.

## Debug
`/respublica status|inspect|village list|warehouse set|famine|overpopulate|insecurity|setrole|tick <n>|selftest|reload`.

## Text
Messages in French (`lang_fr.yml`), prefix `[Res Publica]`, sober colours.

## Clarifications (after plan review)
- **Role ↔ vanilla profession**: the role is stored in the PDC, independent of the profession; the profession is only a hint for initial assignment (farmer→Farmer, stone mason/armorer/smith→Miner or Guard, librarian→Diplomat, etc.). Vanilla emerald trades stay intact.
- **War tension** `T = ((100 - avg_dissident_loyalty) + (100 - avg_loyalist_loyalty)) / 2 + no_diplomat_penalty`, with `no_diplomat_penalty = war_no_diplomat_penalty` (config). War if `T ≥ war_tension` for `war_after` s; truce if a living diplomat has reached `relations ≥ truce_relations`. `relations` (0–100) is a `VillageState` field, raised by diplomat visits.
- **Dissident camp**: has its own `VillageState` (DISSIDENT faction), its beds (tents), a camp barrel (own stock, filled at departure with the carried food share `exile_food_share`). Exiles become Guards (share `camp_guard_share`) or keep their role; they have their own gauges and loyalty to the camp.
- **Diplomat in peace**: regularly visits the nearest neighbouring village (or the centre/bell of their own), which raises `relations`; with no neighbour, talks to other villagers (+loyalty).
- **Disobedience**: if morale < `disobey_morale`, the villager does not work (all roles, via `RoleBehavior.canWork`).
- **Visibility**: displayed name `Role` (configurable setCustomName) + `/respublica inspect` from M8.

## Headless server constraints (to respect in the implementation)
- Flat world, force-loaded chunks, `online-mode=false`, `eula.txt`; `options.release = 17`.
- `/respublica tick <n>` advances plugin timers; tests use `setAge`, teleportation and forced behaviour execution (`RoleBehavior.runOnce`) rather than vanilla AI; assertions on final state.
- Vanilla AI rewrites targets: movement goes through `Mob#getPathfinder` and, as a last resort, teleportation; clear HOME/JOB_SITE/MEETING_POINT memories of exiles; cancel `EntityBreedEvent` (births are handled by `BirthService`).
- Batched PDC writes (buffer, periodic flush and on shutdown). Cap on scanned blocks per tick (`max_scan_blocks_per_tick`).
- Paths with spaces and `&`: quote everything in check.sh.
