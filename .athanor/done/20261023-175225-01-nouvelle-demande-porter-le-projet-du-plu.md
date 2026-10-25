# Nouvelle demande : porter le projet du plugin Pape…

Demandé le 2026-10-23 19h52.

Nouvelle demande : porter le projet du plugin Paper (rrp.bukkit) vers un mod NeoForge pour Minecraft 1.21.1 (NeoForge 21.1.x, Java 21). Le plugin Paper est abandonné : une fois le portage vérifié, supprimer rrp.bukkit, plugin.yml et les scripts Paper (tools/fetch_paper.sh). Le mod doit fonctionner en solo (serveur intégré) ET sur serveur dédié avec un seul jar : logique purement côté serveur, aucun écran ni rendu client, textes via chat et action bar.

Garder rrp.core et rrp.sim tels quels (avec leurs tests JUnit et le simulateur). Réécrire la couche rrp.bukkit en couche NeoForge : point d'entrée @Mod et neoforge.mods.toml (build avec ModDevGradle ou NeoGradle), ticks via événements, commandes Brigadier (mêmes sous-commandes que /respublica, alias /rrp), données persistantes des villageois via attachments NeoForge, scanner de village, entrepôt (tonneau), naissances, crises, camp dissident, guerre civile, comportements par rôle (fermier, forestier, mineur, garde, intendant, bâtisseur, diplomate), action bar, lang_fr.yml, persistance villages.json et config.yml.

Remplacer le selftest Paper par des GameTests NeoForge (ou un équivalent sur serveur NeoForge dédié), adapter .athanor/check.sh pour que « CHECK: PASSED » reste le critère, et livrer le jar du mod dans deliverable/ avec TEST_GUIDE.md et README.md mis à jour. Ajouter ce portage au PLAN.md sous forme de jalons : build NeoForge, commandes, villageois et persistance, comportements, crises et guerre, tests, livraison et nettoyage de l'ancien plugin.
