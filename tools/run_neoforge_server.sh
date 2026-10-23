#!/usr/bin/env bash
# Boots a real NeoForge dedicated server with the mod (generated launch script), waits for "Done", sends "stop".
# Usage: run_neoforge_server.sh [timeout_seconds]  -> log in .athanor/.verif/server.log
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIMIT="${1:-300}"
LOG="$ROOT/.athanor/.verif/server.log"
mkdir -p "$ROOT/run" "$ROOT/.athanor/.verif"
cd "$ROOT" || exit 1
./gradlew classes createServerLaunchScript --console=plain -q > "$ROOT/.athanor/.verif/prep.log" 2>&1 || { echo "SERVER: preparation failed"; tail -n 15 "$ROOT/.athanor/.verif/prep.log"; exit 1; }
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt
[ -f run/server.properties ] || printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\n' > run/server.properties
: > "$LOG"
(
  for i in $(seq 1 "$LIMIT"); do
    grep -q -E "Done \(|Realms & Res Publica activ|Failed to start|Exception" "$LOG" 2>/dev/null && break
    sleep 1
  done
  sleep 3
  # console commands (the mod's reply must appear in the log)
  for c in "rrp help" "respublica reload" "rrp bogus" "rrp village list"; do echo "$c"; sleep 2; done
  sleep 2
  echo stop
  sleep 30
) | timeout $((LIMIT + 60)) cmd //c "$(cygpath -w "$ROOT/build/moddev/runServer.cmd")" > "$LOG" 2>&1
if ! grep -q "Realms & Res Publica activé" "$LOG"; then
  echo "SERVER: mod not activated"; grep -n -E "ERROR|Exception|FATAL" "$LOG" | head -10; tail -n 15 "$LOG"; exit 1
fi
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG"; then echo "SERVER: mod error in log"; exit 1; fi
grep -q "Realms & Res Publica désactivé" "$LOG" || { echo "SERVER: no clean stop"; exit 1; }
for pat in "Commandes disponibles" "config.yml relu" "Sous-commande inconnue" "pas encore disponible"; do
  grep -q "$pat" "$LOG" || { echo "SERVER: command reply missing: $pat"; exit 1; }
done
echo "SERVER: mod loaded, commands answered, stopped cleanly"
