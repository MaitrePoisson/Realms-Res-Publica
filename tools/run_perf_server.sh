#!/usr/bin/env bash
# M67: performance proof on a real NeoForge dedicated server. N villages (default 40, 8 villagers each, force-loaded so they
# are ACTIVE), the mod's own cost per tick (rrp lod perf) is measured in ACTIVE mode, then after forcing every village to
# ABSTRACT. Vanilla `tick query` percentiles are recorded as well. Results: .athanor/visuels/m67-perf.txt
# Usage: run_perf_server.sh [villages] [timeout_seconds]  -> log .athanor/.verif/server_perf.log
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
N="${1:-40}"
LIMIT="${2:-400}"
LOG="$ROOT/.athanor/.verif/server_perf.log"
mkdir -p "$ROOT/run" "$ROOT/.athanor/.verif" "$ROOT/.athanor/visuels"
cd "$ROOT" || exit 1
./gradlew classes createServerLaunchScript --console=plain -q > "$ROOT/.athanor/.verif/prep.log" 2>&1 || { echo "PERF: preparation failed"; tail -n 15 "$ROOT/.athanor/.verif/prep.log"; exit 1; }
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt
printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-name=rrp-perf-world\nspawn-monsters=false\nlevel-type=minecraft\:flat\ngenerate-structures=false\n' > run/server.properties
rm -rf run/rrp-perf-world

CL=()
for i in $(seq 0 $((N-1))); do
  X=$(( (i % 8) * 160 )); Z=$(( (i / 8) * 160 ))
  CL+=("forceload add $((X-16)) $((Z-16)) $((X+31)) $((Z+31))")
done
for i in $(seq 0 $((N-1))); do
  X=$(( (i % 8) * 160 )); Z=$(( (i / 8) * 160 ))
  for d in "1 0" "-1 0" "0 1" "0 -1" "2 1" "-2 -1" "1 2" "-1 -2"; do
    set -- $d
    CL+=("summon minecraft:villager $((X+$1)) -60 $((Z+$2)) {NoAI:1b}")
  done
  CL+=("setblock $((X+3)) -60 $((Z+3)) minecraft:barrel")
  for b in 2 4 6 8 10 12 14 16; do CL+=("setblock $((X+b)) -60 $((Z+5)) minecraft:red_bed[part=head,facing=east]"); done
  for k in 0 1 2 3; do CL+=("item replace block $((X+3)) -60 $((Z+3)) container.$k with minecraft:bread 64"); done
done
SC=(); WH=(); AB=()
for i in $(seq 0 $((N-1))); do
  X=$(( (i % 8) * 160 )); Z=$(( (i / 8) * 160 ))
  SC+=("execute positioned $X -60 $Z run rrp village scan")
  WH+=("execute positioned $X -60 $Z run rrp warehouse set")
  AB+=("rrp lod force village-$((i+1)) abstract")
done
{
  : > "$LOG"
  (
    for i in $(seq 1 "$LIMIT"); do grep -q -E "Done \(|Failed to start|Exception" "$LOG" 2>/dev/null && break; sleep 1; done
    sleep 3
    c() { echo "$1"; sleep "${2:-0.3}"; }
    c "difficulty peaceful"
    for x in "${CL[@]}"; do c "$x" 0.05; done
    for x in "${SC[@]}"; do c "$x" 0.3; done
    for x in "${WH[@]}"; do c "$x" 0.3; done
    c "rrp lod" 2
    sleep 20
    c "rrp lod perf reset" 1; sleep 45
    c "say >>> ACTIVE" 0.5; c "rrp lod perf" 0.5; c "tick query" 1
    for x in "${AB[@]}"; do c "$x" 0.3; done
    sleep 20
    c "rrp lod perf reset" 1; sleep 45
    c "say >>> ABSTRACT" 0.5; c "rrp lod perf" 0.5; c "tick query" 1
    c "rrp lod" 2
    c "rrp village flush" 2
    echo stop
    sleep 30
  ) | timeout $((LIMIT + 600)) cmd //c "$(cygpath -w "$ROOT/build/moddev/runServer.cmd")" > "$LOG" 2>&1
}
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG"; then echo "PERF: mod error in log"; grep -E "\[.*/ERROR\]" "$LOG" | head -5; exit 1; fi
OUT="$ROOT/.athanor/visuels/m67-perf.txt"
{
  echo "M67 performance proof - $N villages x 8 villagers, force-loaded, no player, view/simulation distance 4"
  grep -E ">>> (ACTIVE|ABSTRACT)|PERF villages|Percentiles|Average time per tick" "$LOG" | sed 's/^\[[^]]*\] \[[^]]*\] \[[^]]*\]: //'
} > "$OUT"
cat "$OUT"
sed -i 's/^level-name=.*/level-name=rrp-test-world/' run/server.properties
