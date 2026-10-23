#!/usr/bin/env bash
# Boots a real NeoForge dedicated server with the mod twice on the same fresh world (generated launch script):
#  phase 1 creates a village (villagers, bed, barrel), changes a role, saves and stops;
#  phase 2 restarts and checks that villages.json and the villager data survived.
# Usage: run_neoforge_server.sh [timeout_seconds]  -> logs in .athanor/.verif/server.log (phase 1) and server2.log (phase 2)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIMIT="${1:-300}"
LOG="$ROOT/.athanor/.verif/server.log"
LOG2="$ROOT/.athanor/.verif/server2.log"
mkdir -p "$ROOT/run" "$ROOT/.athanor/.verif"
cd "$ROOT" || exit 1
./gradlew classes createServerLaunchScript --console=plain -q > "$ROOT/.athanor/.verif/prep.log" 2>&1 || { echo "SERVER: preparation failed"; tail -n 15 "$ROOT/.athanor/.verif/prep.log"; exit 1; }
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt
printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-name=rrp-test-world\nspawn-monsters=false\n' > run/server.properties
rm -rf run/rrp-test-world

# run_phase LOGFILE COMMAND... : waits for the mod to be active, sends each command, then stops the server
run_phase() {
  local out="$1"; shift
  : > "$out"
  (
    for i in $(seq 1 "$LIMIT"); do
      grep -q -E "Done \(|Failed to start|Exception" "$out" 2>/dev/null && break
      sleep 1
    done
    sleep 3
    for c in "$@"; do echo "$c"; sleep 2; done
    sleep 2
    echo stop
    sleep 30
  ) | timeout $((LIMIT + 120)) cmd //c "$(cygpath -w "$ROOT/build/moddev/runServer.cmd")" > "$out" 2>&1
}

run_phase "$LOG" "rrp help" "respublica reload" "rrp bogus" "rrp famine" "rrp village list" \
  "difficulty peaceful" \
  "summon minecraft:villager ~1 ~ ~" "summon minecraft:villager ~-1 ~ ~" \
  "setblock ~2 ~ ~2 minecraft:red_bed[part=head,facing=east]" "setblock ~3 ~ ~3 minecraft:barrel" \
  "rrp village scan" "rrp village list" "rrp warehouse set" "execute as @e[type=minecraft:villager,limit=1] at @s run rrp setrole diplomate" "execute as @e[type=minecraft:villager] at @s run rrp inspect" "rrp status village-1" \
  "setblock ~4 ~ ~4 minecraft:red_bed[part=head,facing=east]" "setblock ~5 ~ ~4 minecraft:red_bed[part=head,facing=east]"   "item replace block ~3 ~ ~3 container.0 with minecraft:bread 64" "rrp village scan" "rrp warehouse set" "rrp tick 6500"   "rrp status village-1" "rrp village flush"
if ! grep -q "Realms & Res Publica activé" "$LOG"; then
  echo "SERVER: mod not activated"; grep -n -E "ERROR|Exception|FATAL" "$LOG" | head -10; tail -n 15 "$LOG"; exit 1
fi
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG"; then echo "SERVER: mod error in log"; exit 1; fi
grep -q "Realms & Res Publica désactivé" "$LOG" || { echo "SERVER: no clean stop"; exit 1; }
for pat in "Commandes disponibles" "config.yml relu" "Sous-commande inconnue" "pas encore disponible" "Aucun village connu" \
           "Scan des villages programmé" "village-1 (minecraft:overworld" "2 villageois, 1 lit" "Entrepôt de village-1 désigné" \
           "Rôle changé : Diplomate" "Diplomate — satiété" "Villageois : 2, lits : 1" "villages.json écrit" "Naissance au village village-1" "Stock : Nourriture" "Villageois : 2, lits : 3"; do
  grep -q "$pat" "$LOG" || { echo "SERVER: phase 1 reply missing: $pat"; exit 1; }
done
# gauges must have moved after the accelerated ticks (first vs last status line)
[ "$(grep 'Moyennes' "$LOG" | sed -n 1p | cut -d- -f2-)" != "$(grep 'Moyennes' "$LOG" | tail -n1 | cut -d- -f2-)" ] || { echo "SERVER: gauges did not evolve after rrp tick"; exit 1; }
[ -s run/rrp-test-world/respublica/villages.json ] || { echo "SERVER: villages.json missing from the world folder"; exit 1; }

run_phase "$LOG2" "rrp village list" "execute as @e[type=minecraft:villager] at @s run rrp inspect" "rrp village scan" "rrp status village-1"   "setblock ~5 ~ ~5 minecraft:red_bed[part=head,facing=east]" "setblock ~5 ~ ~7 minecraft:red_bed[part=head,facing=east]" "setblock ~5 ~ ~9 minecraft:red_bed[part=head,facing=east]"   "item replace block ~3 ~ ~3 container.0 with minecraft:bread 64" "rrp village scan" "rrp status village-1" "rrp tick 12000" "rrp status village-1" "rrp village scan" "rrp status village-1"
grep -q "1 village(s) rechargé(s)" "$LOG2" || { echo "SERVER: phase 2 did not reload the village"; tail -n 15 "$LOG2"; exit 1; }
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG2"; then echo "SERVER: mod error in log (phase 2)"; exit 1; fi
for pat in "village-1 (minecraft:overworld" "Diplomate — satiété" "Villageois : 2, lits : 3"; do
  grep -q "$pat" "$LOG2" || { echo "SERVER: phase 2 reply missing: $pat"; exit 1; }
done
grep -q "Naissance au village" "$LOG2" || { echo "SERVER: no birth after 12000 ticks with food and free beds"; exit 1; }
echo "SERVER: mod loaded, commands answered, village + roles survived a restart"
