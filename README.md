# Realms & Res Publica

A NeoForge 1.21.1 mod (Java 21) that turns Minecraft villagers into a living society.

- **Seven roles**: farmer, forester, miner, guard, steward, builder, diplomat — each with its own real in-world behavior.
- **Three gauges per villager**: morale, satiety, loyalty (visible with `/respublica inspect`, saved in the villager's data).
- **Organic demography**: births depend on the food really stored in the village barrel and on the real number of beds; hunger and overcrowding make villagers leave.
- **Crises with real causes**: famine, overpopulation, insecurity → contestation → exile (a dissident camp is built) → civil war → truce (reconciliation or autonomy).
- All balancing lives in `config.yml`; messages are in French (`lang_fr.yml`).

## Install
- **Solo / LAN (integrated server)**: install NeoForge 21.1.x for Minecraft 1.21.1, copy `deliverable/RealmsResPublica.jar` into the `mods/` folder, start the game and open a world. Commands (`/rrp`) need cheats enabled.
- **Dedicated server**: run the NeoForge 21.1.x server installer, copy the jar into the server's `mods/`, start once, accept the EULA. Give yourself op.
- The first launch copies the annotated config to `config/respublica/config.yml` (a copy is in `deliverable/config.yml`); edit it and run `/rrp reload`.

## Build from source
`./gradlew build` (JDK 21) -> `build/libs/RealmsResPublica.jar`. Simulator: `bash tools/run_sim.sh`. Headless GameTests: `./gradlew runGameTestServer`.

See `TEST_GUIDE.md` for commands and a manual checklist. Credits: `docs/CREDITS.md`.
