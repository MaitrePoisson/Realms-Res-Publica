#!/usr/bin/env bash
# Télécharge Paper 1.20.4 (API PaperMC v3) dans .athanor/server/ et prépare eula/server.properties.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRV="$ROOT/.athanor/server"
mkdir -p "$SRV"
if [ ! -s "$SRV/paper.jar" ]; then
  URL=$(curl -sSf https://fill.papermc.io/v3/projects/paper/versions/1.20.4/builds | python3 -c "import sys,json; b=json.load(sys.stdin)[0]; print(b['downloads']['server:default']['url'])")
  curl -sSfL -o "$SRV/paper.jar.tmp" "$URL" && mv "$SRV/paper.jar.tmp" "$SRV/paper.jar"
fi
echo "eula=true" > "$SRV/eula.txt"
cat > "$SRV/server.properties" <<'PROP'
online-mode=false
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:dirt","height"\:2},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains"}
generate-structures=false
view-distance=4
simulation-distance=4
spawn-protection=0
server-port=25599
motd=RRP test
enable-command-block=true
allow-nether=false
spawn-monsters=false
PROP
echo "Paper prêt: $SRV/paper.jar"
