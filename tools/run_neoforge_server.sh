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
printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\nlevel-name=rrp-test-world\nspawn-monsters=false\nlevel-type=minecraft\:flat\ngenerate-structures=false\n' > run/server.properties
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
    for c in "$@"; do case "$c" in "#sleep "*) sleep "${c#\#sleep }";; *) echo "$c"; sleep "${PHASE_DELAY:-2}";; esac; done
    sleep 2
    echo stop
    sleep 30
  ) | timeout $((LIMIT + 120)) cmd //c "$(cygpath -w "$ROOT/build/moddev/runServer.cmd")" > "$out" 2>&1
  # A server that did not stop in time stays alive after the timeout and keeps the port: stop it before the next phase.
  powershell -NoProfile -File "$(cygpath -w "$ROOT/tools/kill_stale_server.ps1")" -Root "$(cygpath -w "$ROOT")" > /dev/null 2>&1
}

run_phase "$LOG" "rrp help" "respublica reload" "rrp bogus" "rrp famine" "rrp village list" \
  "difficulty peaceful" \
  "summon minecraft:villager ~1 ~ ~" "summon minecraft:villager ~-1 ~ ~" \
  "execute as @e[type=minecraft:villager] run data merge entity @s {NoAI:1b}" "setblock ~2 ~ ~2 minecraft:red_bed[part=head,facing=east]" "setblock ~3 ~ ~3 minecraft:barrel" \
  "rrp village scan" "rrp village list" "rrp warehouse set" "execute as @e[type=minecraft:villager,limit=1] at @s run rrp setrole diplomate" "execute as @e[type=minecraft:villager] at @s run rrp inspect" "rrp status village-1" \
  "setblock ~4 ~ ~4 minecraft:red_bed[part=head,facing=east]" "setblock ~5 ~ ~4 minecraft:red_bed[part=head,facing=east]"   "item replace block ~3 ~ ~3 container.0 with minecraft:bread 64" "rrp village scan" "rrp warehouse set" "rrp tick 6500"   "rrp status village-1" "time set noon" "setblock ~6 ~-1 ~ minecraft:farmland" "setblock ~6 ~ ~ minecraft:wheat[age=7]" "execute as @e[type=minecraft:villager] at @s run rrp setrole farmer" "execute if block ~6 ~ ~ minecraft:wheat[age=7] run say WHEATOK" "rrp village act" "setblock ~8 ~ ~ minecraft:oak_log" "setblock ~8 ~1 ~ minecraft:oak_log" "setblock ~8 ~2 ~ minecraft:oak_leaves" "execute as @e[type=minecraft:villager] at @s run rrp setrole forestier" "rrp village act" "execute unless block ~8 ~ ~ minecraft:oak_log unless block ~8 ~1 ~ minecraft:oak_log run say FORESTER_OK" "setblock ~-6 ~ ~ minecraft:iron_ore" "execute as @e[type=minecraft:villager] at @s run rrp setrole mineur" "rrp village act" "execute if block ~-6 ~ ~ minecraft:air run say MINER_OK" "difficulty easy" "summon minecraft:creeper ~4 ~ ~-4 {NoAI:1b}" "execute as @e[type=minecraft:villager] at @s run rrp setrole garde" "rrp village act" "execute unless entity @e[type=minecraft:creeper] run say GUARD_OK" "execute as @e[type=minecraft:villager] at @s run rrp setrole intendant" "rrp village act" "item replace block ~3 ~ ~3 container.5 with minecraft:oak_planks 64" "item replace block ~3 ~ ~3 container.6 with minecraft:oak_planks 64" "execute as @e[type=minecraft:villager] at @s run rrp setrole batisseur" "rrp village act" "rrp status village-1" "execute as @e[type=minecraft:villager] at @s run rrp setrole diplomate" "rrp village act" "execute as @e[type=minecraft:villager] at @s run rrp setrole farmer" "rrp village flush"
if ! grep -q "Realms & Res Publica activé" "$LOG"; then
  echo "SERVER: mod not activated"; grep -n -E "ERROR|Exception|FATAL" "$LOG" | head -10; tail -n 15 "$LOG"; exit 1
fi
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG"; then echo "SERVER: mod error in log"; exit 1; fi
grep -q "Realms & Res Publica désactivé" "$LOG" || { echo "SERVER: no clean stop"; exit 1; }
for pat in "Commandes disponibles" "config.yml relu" "Sous-commande inconnue" "Usage : /respublica famine" "Aucun village connu" \
           "Scan des villages programmé" "village-1 (minecraft:overworld" "2 villageois, 1 lit" "Entrepôt de village-1 désigné" \
           "Rôle changé : Diplomate" "Diplomate — satiété" "Villageois : 2, lits : 1" "villages.json écrit" "Naissance au village village-1" "Stock : Nourriture" "Villageois : 2, lits : 3" "Actions de rôle forcées à village-1 : 1 villageois" "FORESTER_OK" "MINER_OK" "GUARD_OK"; do
  grep -q "$pat" "$LOG" || { echo "SERVER: phase 1 reply missing: $pat"; exit 1; }
done
[ "$(grep -c "Actions de rôle forcées" "$LOG")" = 7 ] && ! grep "Actions de rôle forcées" "$LOG" | sed '5,6d' | grep -q ": 0 villageois" || { echo "SERVER: a role did not act on village act"; grep "Actions de rôle" "$LOG"; exit 1; }
# (the 5th act is the steward, which only feeds the hungry: depends on the real-time satiety; the 6th is the builder, which
# acts only while beds < population + spare: neither is a fixed outcome, so only the other acts are required to act)
# gauges must have moved after the accelerated ticks (first vs last status line)
[ "$(grep 'Moyennes' "$LOG" | sed -n 1p | cut -d- -f2-)" != "$(grep 'Moyennes' "$LOG" | tail -n1 | cut -d- -f2-)" ] || { echo "SERVER: gauges did not evolve after rrp tick"; exit 1; }
[ -s run/rrp-test-world/respublica/villages.json ] || { echo "SERVER: villages.json missing from the world folder"; exit 1; }

run_phase "$LOG2" "rrp village list" "execute as @e[type=minecraft:villager] at @s run rrp inspect" "rrp village scan" "rrp status village-1"   "setblock ~5 ~ ~5 minecraft:red_bed[part=head,facing=east]" "setblock ~5 ~ ~7 minecraft:red_bed[part=head,facing=east]" "setblock ~5 ~ ~9 minecraft:red_bed[part=head,facing=east]"   "item replace block ~3 ~ ~3 container.0 with minecraft:bread 64" "rrp village scan" "rrp status village-1" "rrp tick 12000" "rrp status village-1" "rrp village scan" "rrp status village-1"
grep -q "1 village(s) rechargé(s)" "$LOG2" || { echo "SERVER: phase 2 did not reload the village"; tail -n 15 "$LOG2"; exit 1; }
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG2"; then echo "SERVER: mod error in log (phase 2)"; exit 1; fi
for pat in "village-1 (minecraft:overworld" "Fermier — satiété" "Villageois : 2, lits : [0-9]"; do
  grep -q "$pat" "$LOG2" || { echo "SERVER: phase 2 reply missing: $pat"; exit 1; }
done
grep -q "Naissance au village" "$LOG2" || { echo "SERVER: no birth after 12000 ticks with food and free beds"; exit 1; }
# Phase 3 (fresh world): famine -> contestation -> exile (camp built) -> civil war, driven by real world state
LOG3="$ROOT/.athanor/.verif/server3.log"
sed -i 's/^level-name=.*/level-name=rrp-crisis-world/' run/server.properties
rm -rf run/rrp-crisis-world
TICKS=()
for i in 1 2 3 4 5 6 7 8; do TICKS+=("rrp tick 12000"); done
run_phase "$LOG3" "difficulty easy" "forceload add -48 -110 10 -60" \
  "summon minecraft:villager ~1 ~ ~ {NoAI:1b}" "summon minecraft:villager ~-1 ~ ~ {NoAI:1b}" "summon minecraft:villager ~ ~ ~1 {NoAI:1b}" \
  "summon minecraft:villager ~ ~ ~-1 {NoAI:1b}" "summon minecraft:villager ~2 ~ ~1 {NoAI:1b}" "summon minecraft:villager ~-2 ~ ~-1 {NoAI:1b}" \
  "setblock ~2 ~ ~2 minecraft:red_bed[part=head,facing=east]" "setblock ~3 ~ ~3 minecraft:barrel" "rrp village scan" "rrp warehouse set" \
  "execute as @e[type=minecraft:villager] at @s run rrp setrole garde" "rrp famine" "rrp status village-1" \
  "${TICKS[@]}" "rrp village list" "rrp status village-1"
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG3"; then echo "SERVER: mod error in log (phase 3)"; grep -E "\[.*/ERROR\]" "$LOG3" | head -5; exit 1; fi
for pat in "Famine provoquée à village-1" "gronde" "fondent un campement" "Campement vérifié : [1-9][0-9]* bloc(s) de laine, tonneau présent" "Guerre civile : village-1 et village-1-camp"; do
  grep -q "$pat" "$LOG3" || { echo "SERVER: phase 3 missing: $pat"; grep -E "gronde|campement|Guerre|Trêve|village-1|camp-" "$LOG3" | tail -n 12; exit 1; }
done
echo "SERVER: crisis chain famine -> contestation -> exile (camp built) -> civil war ran on the real server"

# Phase 4 (fresh world): autonomous growth of a small village (hall, orders, buildings, roads, defenses) + tick cost
LOG4="$ROOT/.athanor/.verif/server4.log"
sed -i 's/^level-name=.*/level-name=rrp-urban-world/' run/server.properties
rm -rf run/rrp-urban-world
ACTS=()
for i in $(seq 1 90); do ACTS+=("rrp village act"); done
STOCK=()
for i in $(seq 0 13); do STOCK+=("item replace block ~3 ~ ~3 container.$i with minecraft:oak_log 64"); done
for i in $(seq 14 26); do STOCK+=("item replace block ~3 ~ ~3 container.$i with minecraft:cobblestone 64"); done
VILL=()
for k in "1 0" "-1 0" "0 1" "0 -1" "2 1" "-2 -1" "1 2" "-1 -2" "3 0" "-3 0"; do VILL+=("summon minecraft:villager ~$(echo $k | cut -d' ' -f1) ~ ~$(echo $k | cut -d' ' -f2) {NoAI:1b}"); done
# the dummies have no AI and cannot walk to the barrel: the whole group stands within reach of it
printf 'chest_reach: 6.0
' >> run/config/respublica/config.yml
PHASE_DELAY=0.4 run_phase "$LOG4" "difficulty peaceful" "forceload add -48 -110 80 50" "${VILL[@]}"   "setblock ~2 ~ ~2 minecraft:red_bed[part=head,facing=east]" "setblock ~3 ~ ~3 minecraft:barrel" "${STOCK[@]}"   "rrp village scan" "rrp warehouse set" "rrp townhall build" "rrp village scan"   "execute as @e[type=minecraft:villager,limit=3,sort=nearest] at @s run rrp setrole batisseur" "execute as @e[type=minecraft:villager,limit=2,sort=furthest] at @s run rrp setrole intendant"   "execute as @e[type=minecraft:villager,limit=2,sort=random] at @s run rrp setrole garde" "rrp village scan" "rrp status village-1"   "${ACTS[@]}" "rrp village scan" "rrp status village-1" "tick query" "rrp village flush"
sed -i '/^chest_reach: 6.0$/d' run/config/respublica/config.yml
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG4"; then echo "SERVER: mod error in log (phase 4)"; grep -E "\[.*/ERROR\]" "$LOG4" | head -5; exit 1; fi
grep -q "Hôtel de Ville de village-1 posé" "$LOG4" || { echo "SERVER: phase 4 hall not built"; exit 1; }
BUILT="$(grep "Bâtiments :" "$LOG4" | tail -n1)"
echo "SERVER: urban growth -> $BUILT"
echo "$BUILT" | grep -q -E "HOUSE|FARM|WORKSHOP|TOWER" || { echo "SERVER: phase 4 no building was completed autonomously"; grep -E "Ordres de construction|Defenses|Bâtiments" "$LOG4" | tail -n 6; exit 1; }
grep -E "Ordres de construction|Defenses|Coffres" "$LOG4" | tail -n 3
mkdir -p "$ROOT/.athanor/visuels"; python3 "$ROOT/tools/topdown.py" run/rrp-urban-world "$ROOT/.athanor/visuels/m38-village.png" > /dev/null 2>&1 || echo "SERVER: top-down export skipped"
grep -E "tick|P50|P95|ms" "$LOG4" | grep -i -E "average|P50|P95" | tail -n 2

# Phase 5 (fresh world): two villages 150 blocks apart in different biomes (badlands patch under village 2):
# borders, doctrines, relations and a forced border incident
LOG5="$ROOT/.athanor/.verif/server5.log"
sed -i 's/^level-name=.*/level-name=rrp-borders-world/' run/server.properties
rm -rf run/rrp-borders-world
# explicit coordinates (no tilde: the shell would expand it); village 1 around 0 0, village 2 around 150 0, flat world y=-60
CL=()
for X in 0 150; do
  for d in "1 0" "-1 0" "0 1" "0 -1"; do
    set -- $d
    CL+=("summon minecraft:villager $((X+$1)) -60 $2 {NoAI:1b}")
  done
  CL+=("setblock $((X+2)) -60 2 minecraft:red_bed[part=head,facing=east]" "setblock $((X+3)) -60 3 minecraft:barrel")
  for i in 0 1 2 3; do
    CL+=("item replace block $((X+3)) -60 3 container.$i with minecraft:oak_log 64" "item replace block $((X+3)) -60 3 container.$((i+4)) with minecraft:cobblestone 64")
  done
done
at2() { echo "execute positioned 150 -60 0 run $*"; }
PHASE_DELAY=0.4 run_phase "$LOG5" "difficulty peaceful" "forceload add -16 -100 260 100" "#sleep 8" "fillbiome 40 -64 -100 71 -60 100 minecraft:badlands" "fillbiome 72 -64 -100 103 -60 100 minecraft:badlands" "fillbiome 104 -64 -100 135 -60 100 minecraft:badlands" "fillbiome 136 -64 -100 167 -60 100 minecraft:badlands" "fillbiome 168 -64 -100 199 -60 100 minecraft:badlands" "fillbiome 200 -64 -100 231 -60 100 minecraft:badlands" "fillbiome 232 -64 -100 263 -60 100 minecraft:badlands" "execute if biome 150 -60 0 minecraft:badlands run say BIOME_OK"   "${CL[@]}" "rrp village scan" "rrp warehouse set" "$(at2 rrp warehouse set)" "rrp townhall build" "$(at2 rrp townhall build)"   "rrp village scan" "rrp village act" "rrp village act" "rrp tick 1200" "rrp territory" "rrp territory incident village-1 village-2" "rrp territory" "rrp village flush"
if grep -q -E "\[.*/ERROR\].*(rrp|respublica)" "$LOG5"; then echo "SERVER: mod error in log (phase 5)"; grep -E "\[.*/ERROR\]" "$LOG5" | head -5; exit 1; fi
for pat in "Doctrine de village-1 : MERCHANT_CITY" "Doctrine de village-2 : WARLIKE_EMPIRE" "Relation village-1 <-> village-2 : -*[0-9]* ([A-Z]*, incidents : 0)"            "Incident de frontière provoqué : village-1 contre village-2" "Relation village-1 <-> village-2 : -*[0-9]* ([A-Z]*, incidents : [1-9]"; do
  grep -q "$pat" "$LOG5" || { echo "SERVER: phase 5 missing: $pat"; grep -E "Doctrine|Relation|Territoire|village-" "$LOG5" | tail -n 12; exit 1; }
done
echo "SERVER: borders + doctrines + border incident on two villages in different biomes"
python3 "$ROOT/tools/topdown.py" run/rrp-borders-world "$ROOT/.athanor/visuels/m45-borders.png" > /dev/null 2>&1 || echo "SERVER: top-down export skipped"

# leave no stray world behind: the GameTest server reads run/server.properties and would load these villages
sed -i 's/^level-name=.*/level-name=rrp-test-world/' run/server.properties
rm -rf run/rrp-test-world run/rrp-crisis-world run/rrp-urban-world run/rrp-borders-world

echo "SERVER: mod loaded, commands answered, village + roles survived a restart"
