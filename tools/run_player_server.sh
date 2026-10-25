#!/usr/bin/env bash
# M62: real NeoForge dedicated server, two villages 150 blocks apart; a fake "probe" player (rrp probe, op only) plays the
# four roles: Sovereign (found a village, tax, law, plan zone, political map, register + emissary), Merchant (buy low, sell high),
# Mercenary (war contract). Everything is observed in the server log ([probe] lines).
# Usage: run_player_server.sh [timeout_seconds]  -> .athanor/.verif/server7.log, top-down export in .athanor/visuels/m62-players.png
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIMIT="${1:-300}"
LOG="$ROOT/.athanor/.verif/server7.log"
mkdir -p "$ROOT/run" "$ROOT/.athanor/.verif" "$ROOT/.athanor/visuels"
cd "$ROOT" || exit 1
./gradlew classes createServerLaunchScript --console=plain -q > "$ROOT/.athanor/.verif/prep.log" 2>&1 || { echo "PLAYERS: preparation failed"; tail -n 15 "$ROOT/.athanor/.verif/prep.log"; exit 1; }
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt
printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-name=rrp-players-world\nspawn-monsters=false\nlevel-type=minecraft\:flat\ngenerate-structures=false\n' > run/server.properties
rm -rf run/rrp-players-world

run_phase() {
  local out="$1"; shift
  : > "$out"
  (
    for i in $(seq 1 "$LIMIT"); do
      grep -q -E "Done \(|Failed to start|Exception" "$out" 2>/dev/null && break
      sleep 1
    done
    sleep 3
    for c in "$@"; do echo "$c"; sleep "${PHASE_DELAY:-2}"; done
    sleep 2
    echo stop
    sleep 30
  ) | timeout $((LIMIT + 240)) cmd //c "$(cygpath -w "$ROOT/build/moddev/runServer.cmd")" > "$out" 2>&1
}

CL=()
for X in 0 150; do
  for d in "1 0" "-1 0" "0 1" "0 -1" "2 1" "-2 -1" "1 2" "-1 -2"; do
    set -- $d
    CL+=("summon minecraft:villager $((X+$1)) -60 $2 {NoAI:1b}")
  done
  CL+=("setblock $((X+3)) -60 3 minecraft:barrel")
  for b in 2 4 6 8 10 12 14 16; do CL+=("setblock $((X+b)) -60 5 minecraft:red_bed[part=head,facing=east]"); done
done
for i in 0 1 2 3 4 5; do CL+=("item replace block 3 -60 3 container.$i with minecraft:bread 64"); done
for i in 6 7 8 9; do CL+=("item replace block 3 -60 3 container.$i with minecraft:iron_ingot 64"); done
for i in 0 1 2 3; do CL+=("item replace block 153 -60 3 container.$i with minecraft:cobblestone 64"); done
at() { echo "execute positioned $1 -60 $2 run $3"; }
P() { at "$1" "$2" "rrp probe ${*:3}"; }

PHASE_DELAY=0.5 run_phase "$LOG" "difficulty peaceful" "forceload add -16 -16 170 16" "forceload add 284 -16 316 16" "${CL[@]}" \
  "rrp village scan" "rrp warehouse set" "$(at 150 0 'rrp warehouse set')" "rrp townhall build" "$(at 150 0 'rrp townhall build')" "rrp village scan" "rrp village act" "rrp tick 1200" \
  "$(P 300 0 role sovereign)" "$(P 300 0 purse 100)" "$(P 300 0 found)" "$(P 300 0 role show)" \
  "$(P 300 0 tax set food 20)" "$(P 300 0 tax show)" "$(P 300 0 law ban on)" "$(P 300 0 law embargo village-2)" "$(P 300 0 law show)" \
  "$(P 300 0 plan zone market 300 0 310 10)" "$(P 300 0 plan show)" "$(P 300 0 map)" "$(P 300 0 register)" \
  "$(P 300 0 register propose trade village-3 village-1)" "$(P 300 0 register propose alliance village-3 village-1)" \
  "$(P 0 0 role merchant)" "$(P 0 0 purse 60)" "$(P 0 0 market prices)" "$(P 0 0 market buy food 40)" "$(P 0 0 role show)" \
  "$(P 150 0 market prices)" "$(P 150 0 market sell food 40)" "$(P 150 0 role show)" \
  "rrp territory incident village-1 village-2" "rrp territory incident village-1 village-2" "rrp territory incident village-1 village-2" "rrp territory incident village-2 village-1" \
  "rrp diplomacy evaluate" "rrp tick 1200" "rrp tick 1200" "rrp tick 1200" "rrp tick 1200" "rrp war" \
  "$(P 0 0 role mercenary)" "$(P 0 0 contract list)" "$(P 0 0 contract accept 1)" "$(P 0 0 contract status)" \
  "rrp tick 1200" "rrp tick 1200" "$(P 0 0 contract status)" "rrp village flush"
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG"; then echo "PLAYERS: mod error in log"; grep -E "\[.*/ERROR\]" "$LOG" | head -5; exit 1; fi
for pat in "\[probe\].*village-3" "\[probe\] ran: probe tax set food 20" "\[probe\] ran: probe plan zone" "\[probe\] ran: probe map" "\[probe\] ran: probe register propose trade" \
           "\[probe\] ran: probe market buy food 40" "\[probe\] ran: probe market sell food 40" "\[probe\] ran: probe contract accept 1"; do
  grep -q "$pat" "$LOG" || { echo "PLAYERS: missing in log: $pat"; exit 1; }
done
echo "PLAYERS: sovereign, merchant and mercenary roles ran on the real server"
python3 "$ROOT/tools/topdown.py" run/rrp-players-world "$ROOT/.athanor/visuels/m62-players.png" > /dev/null 2>&1 || echo "PLAYERS: top-down export skipped"
sed -i 's/^level-name=.*/level-name=rrp-test-world/' run/server.properties
echo "PLAYERS: log in $LOG"
