#!/usr/bin/env bash
# Vérification du projet. Usage : check.sh [quick [motif]]
#   quick            : tests JUnit seulement (rapide, sans serveur)
#   quick <motif>    : tests (et simulateur si motif « sim ») dont le nom contient <motif> ; motif « gametest » = GameTests NeoForge seulement ; motif « server » = serveur NeoForge réel seulement
#   (sans argument)  : build + tous les tests + serveur NeoForge réel ; imprime CHECK: PASSED/FAILED
cd "$(dirname "$0")/.." || exit 1
mkdir -p .athanor/.verif
if [ -n "$TOOLS" ] && [ -d "$TOOLS/jdk21" ]; then
  export JAVA_HOME="$(ls -d "$TOOLS"/jdk21/jdk-* | head -n1)"; export PATH="$JAVA_HOME/bin:$PATH"
fi
MODE="${1:-full}"
PATTERN="${2:-}"
FAIL=0
RUN_GRADLE=1
RUN_SERVER=0
RUN_GAMETEST=0
[ "$MODE" != quick ] || [ "$PATTERN" = gametest ] && RUN_GAMETEST=1
if [ "$MODE" = quick ]; then
  ARGS=(test)
  if [ "$PATTERN" = gametest ]; then
    ARGS=(classes)
  elif [ "$PATTERN" = server ]; then
    RUN_SERVER=1; ARGS=(jar)
  elif [ -n "$PATTERN" ]; then
    ARGS+=(--tests "*${PATTERN}*")
  fi
else
  ARGS=(build); RUN_SERVER=1
fi
if [ $RUN_GRADLE -eq 1 ]; then
  timeout 840 ./gradlew "${ARGS[@]}" --console=plain > .athanor/.verif/gradle.log 2>&1 || FAIL=1
  tail -n 6 .athanor/.verif/gradle.log
fi
# Simulateur d'équilibrage : rapports CSV + SVG dans .athanor/screenshots (sauf quick <autre motif> et quick server)
if [ $FAIL -eq 0 ] && { [ "$MODE" != quick ] || [ -z "$PATTERN" ] || [ "$PATTERN" = sim ]; }; then
  timeout 120 bash tools/run_sim.sh .athanor/screenshots > .athanor/.verif/sim.log 2>&1 || FAIL=1
  cat .athanor/.verif/sim.log
  for n in prosperous famine overpopulation insecurity; do
    [ -s ".athanor/screenshots/sim-$n.svg" ] || { echo "rapport sim-$n.svg absent"; FAIL=1; }
  done
fi
if [ "$MODE" != quick ]; then
  unzip -l build/libs/RealmsResPublica.jar 2>/dev/null | grep -q neoforge.mods.toml || { echo "neoforge.mods.toml absent du jar"; FAIL=1; }
fi
if [ $FAIL -eq 0 ] && [ $RUN_GAMETEST -eq 1 ]; then
  rm -rf run/rrp-*-world  # the GameTests use the level-name left in run/server.properties by the real-server phases
  timeout 600 ./gradlew runGameTestServer --console=plain > .athanor/.verif/gametest.log 2>&1 || FAIL=1
  grep -E "GAME TESTS COMPLETE|required tests (failed|passed)|^\s+- " .athanor/.verif/gametest.log | sed 's/^.*GameTestServer\]: //' | head -n 12
fi
if [ $FAIL -eq 0 ] && [ $RUN_SERVER -eq 1 ]; then
  timeout 840 bash tools/run_neoforge_server.sh 300 > .athanor/.verif/server_run.log 2>&1 || FAIL=1
  tail -n 5 .athanor/.verif/server_run.log
  if grep -q -E "\[(ERROR|SEVERE)\].*RealmsResPublica|Could not load 'plugins/RealmsResPublica" .athanor/.verif/server.log; then
    echo "erreur du plugin dans server.log"; FAIL=1
  fi
fi
if [ $FAIL -eq 0 ] && { [ "$MODE" != quick ] || [ "$PATTERN" = war ]; }; then
  timeout 840 bash tools/run_war_server.sh 300 > .athanor/.verif/war_run.log 2>&1 || FAIL=1
  tail -n 3 .athanor/.verif/war_run.log
  grep -q "WAR: relation, caravan" .athanor/.verif/war_run.log || FAIL=1
fi
if [ $FAIL -eq 0 ] && [ "$MODE" != quick ] && [ -f build/libs/RealmsResPublica.jar ]; then mkdir -p deliverable; cp build/libs/RealmsResPublica.jar deliverable/RealmsResPublica.jar; cp src/main/resources/config.yml deliverable/config.yml; fi
if [ $FAIL -eq 0 ]; then echo "CHECK: PASSED"; else echo "CHECK: FAILED"; exit 1; fi
