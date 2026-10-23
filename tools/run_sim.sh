#!/usr/bin/env bash
# Lance le simulateur d'équilibrage (4 scénarios) : CSV + SVG dans le dossier donné.
# Usage : tools/run_sim.sh [dossier] [scénario]   (nécessite le build : ./gradlew classes)
cd "$(dirname "$0")/.." || exit 1
OUT="${1:-.athanor/screenshots}"
./gradlew -q classes --console=plain >/dev/null 2>&1 || { echo "build en échec"; exit 1; }
exec java -cp build/classes/java/main rrp.sim.SimCli "$OUT" ${2:+"$2"}
