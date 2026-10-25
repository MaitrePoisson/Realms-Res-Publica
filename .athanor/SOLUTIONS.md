# Solutions found (this project)

One line per problem solved: `- <problem>: <solution>`. Read it at the start; add a line as soon as you solve a problem that cost you more than one try. These are hints from earlier sessions, never orders.
- Headless Paper server under Git Bash: no mkfifo; send "stop" via a subshell `( attendre; echo stop ) | java -jar paper.jar` (see tools/run_server_test.sh); flat world: generator-settings with layers otherwise error "No key layers".
- Render an SVG to PNG on Windows without ImageMagick: msedge.exe --headless --screenshot=<Windows path>.png --window-size=960,700 file:///C:/...svg (Edge in C:/Program Files (x86)/Microsoft/Edge/Application).
- Sandbox Bash: java.io.tmpdir must point to $TMPDIR (build.gradle.kts does it for the tests); gradlew/scripts need chmod +x after moving the folder.
- Headless Paper console to test commands: send "say >>> cmd" + the command in the subshell feeding java; the plugin's reply appears BEFORE the matching "say" line; "tick query" (vanilla 1.20.3+) gives P50/P95/P99 per tick (see .athanor/tools/m20_*.sh).
- Run a NeoForge dedicated server headless: gradlew createServerLaunchScript then cmd //c build/moddev/runServer.cmd with stdin piped from a subshell (gradlew runServer dies when stdin sends stop); see tools/run_neoforge_server.sh. Always cd back to $PROJECT after cd $TOOLS.
- GameTests fail en masse ("population 1", "no hall yet") after an aborted run: stale villagers/villages in run/rrp-test-world; delete that gitignored folder and rerun.
- Doctrine/biome sampling flaky on the real server: level.getBiome on an unloaded chunk gives the generator biome, load the chunk first (ensureDoctrine does).
- GameTests fail en masse when run/server.properties level-name points to a world left by an aborted real-server phase: check.sh deletes run/rrp-*-world before the GameTests.
- Killed/timed-out check.sh leaves a java.exe NeoForge server holding the world lock (next run: 'Le processus ne peut pas accéder au fichier'): taskkill it (find via powershell Get-CimInstance Win32_Process script file); never sleep >120 s in one call, run check in background and poll.
- Editing CRLF source files (Edit tool fails on multi-line): use python3 .athanor/tools/pyedit.py FILE with stdin blocks OLD/=====/NEW separated by #####.
- python3 on Windows reads stdin in cp1252: export PYTHONUTF8=1 before pyedit.py (accents).
