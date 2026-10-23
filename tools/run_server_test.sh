#!/usr/bin/env bash
# Lance un vrai serveur Paper avec le plugin, attend son activation, l'arrête proprement.
# Usage : run_server_test.sh [timeout_secondes]
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRV="$ROOT/.athanor/server"
LIMIT="${1:-240}"
JAR="$ROOT/build/libs/RealmsResPublica.jar"
[ -s "$JAR" ] || { echo "jar absent: $JAR"; exit 1; }
bash "$ROOT/tools/fetch_paper.sh" >/dev/null || { echo "téléchargement de Paper impossible"; exit 1; }
mkdir -p "$SRV/plugins" "$ROOT/.athanor/.verif"
cp "$JAR" "$SRV/plugins/RealmsResPublica.jar"
RESULT="$SRV/plugins/RealmsResPublica/selftest-result.json"
rm -f "$RESULT"
LOG="$ROOT/.athanor/.verif/server.log"
: > "$LOG"
cd "$SRV" || exit 1
# stdin du serveur : un sous-shell attend « Done », lance `respublica selftest`, attend « selftest terminé »
# (ou la limite), puis envoie « stop ». Pas de FIFO (indisponible sous Git Bash).
(
  for i in $(seq 1 "$LIMIT"); do
    grep -q -E "Done \(|Realms & Res Publica désactivé" "$LOG" 2>/dev/null && break
    sleep 1
  done
  sleep 1
  echo "respublica selftest"
  for i in $(seq 1 "$LIMIT"); do
    grep -q "selftest terminé" "$LOG" 2>/dev/null && break
    sleep 1
  done
  sleep 2
  echo stop
  sleep 25
) | timeout $((LIMIT + 40)) java -Xmx1G -Dfile.encoding=UTF-8 -jar paper.jar --nogui > "$LOG" 2>&1
sleep 1
if ! grep -q "Realms & Res Publica activé" "$LOG"; then
  echo "SERVER: plugin non activé"; tail -n 20 "$LOG"; exit 1
fi
echo "SERVER: plugin activé"
if [ ! -s "$RESULT" ]; then
  echo "SELFTEST: selftest-result.json absent"; tail -n 20 "$LOG"; exit 1
fi
cp "$RESULT" "$ROOT/.athanor/.verif/selftest-result.json"
SUMMARY=$(tr -d "[:space:]" < "$RESULT" | grep -o '"total":[0-9]*,"passed":[0-9]*,"failed":[0-9]*')
echo "SELFTEST: $SUMMARY"
if ! echo "$SUMMARY" | grep -q '"failed":0$' || echo "$SUMMARY" | grep -q '"total":0,'; then
  grep -E '"status": "FAIL"' "$RESULT"; exit 1
fi
exit 0
