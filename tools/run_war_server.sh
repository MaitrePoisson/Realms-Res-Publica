#!/usr/bin/env bash
# M53: real NeoForge dedicated server, two villages 150 blocks apart: relation change, caravan, border incidents,
# ultimatum + war declaration, regiment mobilisation, siege, war resolution. Everything is observed in the server log.
# Usage: run_war_server.sh [timeout_seconds]  -> .athanor/.verif/server6.log, top-down export in .athanor/visuels/m53-war.png
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIMIT="${1:-300}"
LOG="$ROOT/.athanor/.verif/server6.log"
mkdir -p "$ROOT/run" "$ROOT/.athanor/.verif" "$ROOT/.athanor/visuels"
cd "$ROOT" || exit 1
./gradlew classes createServerLaunchScript --console=plain -q > "$ROOT/.athanor/.verif/prep.log" 2>&1 || { echo "WAR: preparation failed"; tail -n 15 "$ROOT/.athanor/.verif/prep.log"; exit 1; }
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt
printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-name=rrp-war-world\nspawn-monsters=false\nlevel-type=minecraft\:flat\ngenerate-structures=false\n' > run/server.properties
rm -rf run/rrp-war-world

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

# village 1 around 0 0 (food surplus + iron + wood for gear and engines), village 2 around 150 0 (stone only: food shortage)
CL=()
for X in 0 150; do
  for d in "1 0" "-1 0" "0 1" "0 -1" "2 1" "-2 -1" "1 2" "-1 -2"; do
    set -- $d
    CL+=("summon minecraft:villager $((X+$1)) -60 $2 {NoAI:1b}")
  done
  CL+=("setblock $((X+3)) -60 3 minecraft:barrel")
  for b in 2 4 6 8 10 12 14 16; do CL+=("setblock $((X+b)) -60 5 minecraft:red_bed[part=head,facing=east]"); done  # one bed per villager: no overpopulation crisis
done
for i in 0 1 2 3 4 5; do CL+=("item replace block 3 -60 3 container.$i with minecraft:bread 64"); done
for i in 6 7 8 9; do CL+=("item replace block 3 -60 3 container.$i with minecraft:iron_ingot 64"); done
for i in 10 11 12 13 14 15; do CL+=("item replace block 3 -60 3 container.$i with minecraft:oak_planks 64"); done
for i in 0 1 2 3; do CL+=("item replace block 153 -60 3 container.$i with minecraft:cobblestone 64"); done
for i in 4 5 6 7 8 9; do CL+=("item replace block 153 -60 3 container.$i with minecraft:oak_planks 64"); done
at2() { echo "execute positioned 150 -60 0 run $*"; }
TICKS=()
for i in $(seq 1 16); do TICKS+=("rrp tick 1200" "rrp war"); done
PHASE_DELAY=0.4 run_phase "$LOG" "difficulty peaceful" "forceload add -16 -100 260 100" "${CL[@]}" \
  "rrp village scan" "rrp warehouse set" "$(at2 rrp warehouse set)" "rrp townhall build" "$(at2 rrp townhall build)" "rrp village scan" \
  "rrp village act" "rrp village act" "rrp tick 1200" "rrp territory" \
  "rrp caravan village-1 village-2" "rrp tick 1200" "rrp territory" \
  "execute as @e[type=minecraft:villager,x=-10,y=-70,z=-10,dx=20,dy=20,dz=20] at @s run rrp setrole garde" \
  "execute as @e[type=minecraft:villager,x=140,y=-70,z=-10,dx=20,dy=20,dz=20] at @s run rrp setrole garde" \
  "execute as @e[type=minecraft:villager,x=-10,y=-70,z=-10,dx=20,dy=20,dz=20,limit=2,sort=furthest] at @s run rrp setrole batisseur"   "execute as @e[type=minecraft:villager,x=140,y=-70,z=-10,dx=20,dy=20,dz=20,limit=2,sort=furthest] at @s run rrp setrole batisseur"   "rrp territory incident village-1 village-2" "rrp territory incident village-1 village-2" "rrp territory incident village-1 village-2" "rrp territory incident village-2 village-1" \
  "item replace block 153 -60 3 container.10 with minecraft:bread 64" "item replace block 153 -60 3 container.11 with minecraft:bread 64" "rrp village scan" "rrp diplomacy evaluate" "rrp diplomacy" "rrp tick 1200" "rrp diplomacy" \
  "${TICKS[@]}" "rrp regiment" "rrp siege" "rrp diplomacy" "rrp territory" "rrp village flush"
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG"; then echo "WAR: mod error in log"; grep -E "\[.*/ERROR\]" "$LOG" | head -5; exit 1; fi
for pat in "Traité commercial signé entre village-1 et village-2" "Caravane : échange réussi entre" "Ultimatum : village-[12] déclarera la guerre"            "Guerre ! village-[12] déclare la guerre" "mobilise un régiment de" "assemblent un(e) bélier devant" "a vaincu village-[12] : [A-Z]* (butin" "démobilise son régiment"; do
  grep -q "$pat" "$LOG" || { echo "WAR: missing in log: $pat"; exit 1; }
done
echo "WAR: relation, caravan, ultimatum, war declaration, mobilisation, siege and resolution ran on the real server"
python3 "$ROOT/tools/topdown.py" run/rrp-war-world "$ROOT/.athanor/visuels/m53-war.png" > /dev/null 2>&1 || echo "WAR: top-down export skipped"
sed -i 's/^level-name=.*/level-name=rrp-test-world/' run/server.properties
echo "WAR: log in $LOG"
