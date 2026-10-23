# Realms & Res Publica

A Paper 1.20.4 plugin (Java 17) that turns Minecraft villagers into a living society.

- **Seven roles**: farmer, forester, miner, guard, steward, builder, diplomat — each with its own real in-world behavior.
- **Three gauges per villager**: morale, satiety, loyalty (visible with `/respublica inspect`, saved in the villager's data).
- **Organic demography**: births depend on the food really stored in the village barrel and on the real number of beds; hunger and overcrowding make villagers leave.
- **Crises with real causes**: famine, overpopulation, insecurity → contestation → exile (a dissident camp is built) → civil war → truce (reconciliation or autonomy).
- All balancing lives in `config.yml`; messages are in French (`lang_fr.yml`).

## Install
1. Use a Paper 1.20.4 server with Java 17+.
2. Copy `deliverable/RealmsResPublica.jar` into `plugins/`, start the server once, then edit `plugins/RealmsResPublica/config.yml` (an annotated copy is in `deliverable/config.yml`) and run `/respublica reload`.

## Build from source
`./gradlew build` (JDK 17) → `build/libs/RealmsResPublica.jar`. Simulator: `bash tools/run_sim.sh`.

See `TEST_GUIDE.md` for commands and a manual checklist. Credits: `docs/CREDITS.md`.
