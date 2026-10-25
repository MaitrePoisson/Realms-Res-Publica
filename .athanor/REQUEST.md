# Realms Res Publica

Demandé le 2026-10-23 10h11.

# Société des villageois : rôles, besoins et démographie

## Objectif
Faire évoluer les villageois de simples marchands à émeraudes en de véritables habitants d'une société vivante : ils ont un métier précis, des besoins et une loyauté, et leur population croît ou s'effondre selon les ressources réelles du village.

## Plateforme et public
- Univers de type Minecraft (villageois, émeraudes, lits). Plateforme exacte à confirmer (suggestion : mod ou plugin pour Minecraft Java Edition).
- Destiné aux joueurs qui veulent voir des villages évoluer de façon organique.

## Fonctionnalités

### 1. Rôles sociaux
- Sept rôles précis : **Bâtisseurs, Forestiers, Mineurs, Gardes/Soldats, Fermiers, Intendants, Diplomates**.
- Chaque villageois a un rôle ; il ne se limite plus au commerce d'émeraudes.
- Chaque rôle a un comportement et des tâches propres (suggestion : le fermier cultive et récolte, le forestier coupe du bois, le mineur extrait, le garde protège, l'intendant gère l'entrepôt, le bâtisseur construit, le diplomate gère les relations entre factions).

### 2. Jauges individuelles
Chaque PNJ possède :
- une jauge de **moral** ;
- une jauge de **satiété** ;
- une jauge de **loyauté** envers sa faction.

(suggestion : jauges visibles par un indicateur ou une commande de debug ; valeurs sauvegardées avec le monde.)

### 3. Croissance démographique organique
- La natalité dépend de deux facteurs : les **réserves alimentaires réellement stockées** dans l'entrepôt communal et le **nombre de lits disponibles**.
- Pas de naissance sans nourriture et sans lit libre.

### 4. Crises sociales
En cas de **famine prolongée, surpopulation ou insécurité**, le village risque :
- la **contestation** (baisse de moral et de loyauté, désobéissance) ;
- l'**exil** : des mécontents partent fonder un **campement dissident** ;
- la **guerre civile locale** entre loyalistes et dissidents.

(suggestion : seuils et durées configurables, avec une escalade progressive contestation → exil → guerre civile.)

## Contraintes
- Tout doit découler de l'état réel du monde (stocks, lits, menaces), sans événements aléatoires artificiels.
- Les valeurs d'équilibrage doivent être réglables dans un fichier de configuration (suggestion).
- Performances acceptables avec plusieurs villages et dizaines de villageois.

## Critères de réussite
- Les sept rôles existent et produisent des comportements distincts.
- Chaque PNJ a moral, satiété et loyauté qui évoluent de façon observable.
- Un village bien approvisionné avec assez de lits voit sa population croître ; sans nourriture ou sans lit, elle stagne.
- Une famine, une surpopulation ou une insécurité prolongée déclenche effectivement contestation, exil avec campement dissident, puis éventuellement guerre civile.
- Le projet est testable (suggestion : commande ou mode de debug pour simuler une famine ou une surpopulation).

## Remarques en cours de route
(Vous pouvez écrire ici pendant que Claude travaille : il relit ce fichier au début de chaque cycle.)

- 2026-10-23 19h49 : Nouvelle demande : porter le projet du plugin Paper (rrp.bukkit) vers un mod NeoForge pour Minecraft 1.21.1 (NeoForge 21.1.x, Java 21). Le plugin Paper est abandonné : une fois le portage vérifié, supprimer rrp.bukkit, plugin.yml et les scripts Paper (tools/fetch_paper.sh). Le mod doit fonctionner en solo (serveur intégré) ET sur serveur dédié avec un seul jar : logique purement côté serveur, aucun écran ni rendu client, textes via chat et action bar.  Garder rrp.core et rrp.sim tels quels (avec leurs tests JUnit et le simulateur). Réécrire la couche rrp.bukkit en couche NeoForge : point d'entrée @Mod et neoforge.mods.toml (build avec ModDevGradle ou NeoGradle), ticks via événements, commandes Brigadier (mêmes sous-commandes que /respublica, alias /rrp), données persistantes des villageois via attachments NeoForge, scanner de village, entrepôt (tonneau), naissances, crises, camp dissident, guerre civile, comportements par rôle (fermier, forestier, mineur, garde, intendant, bâtisseur, diplomate), action bar, lang_fr.yml, persistance villages.json et config.yml.  Remplacer le selftest Paper par des GameTests NeoForge (ou un équivalent sur serveur NeoForge dédié), adapter .athanor/check.sh pour que « CHECK: PASSED » reste le critère, et livrer le jar du mod dans deliverable/ avec TEST_GUIDE.md et README.md mis à jour. Ajouter ce portage au PLAN.md sous forme de jalons : build NeoForge, commandes, villageois et persistance, comportements, crises et guerre, tests, livraison et nettoyage de l'ancien plugin.
- 2026-10-23 16h00 : Demande du propriétaire : il veut des visuels du jeu (vue « in game ») pour suivre l'avancement. Si un client Minecraft ou un rendu hors-jeu est possible dans cet environnement, produis des captures du village de test (huttes, villageois par rôle, campement dissident) dans `.athanor/visuels/` après chaque jalon visuel. Sinon, génère une vue de dessus en PNG ou HTML à partir du monde de test. Indique le chemin des fichiers dans LOG.md, ou note l'impossibilité et sa raison dans DECISIONS.md.
- 2026-10-23 15h34 : Demande du propriétaire : il ne voit pas l'avancement et voudrait des visuels. Si c'est faisable sans client Minecraft, ajoute après M15 un petit outil qui génère dans `.athanor/visuels/` une image PNG (vue de dessus) ou une page HTML du village du monde de test : positions des huttes, lits, tonneau, villageois par rôle, campement dissident. Régénère-le à la fin de chaque jalon et mentionne le chemin du fichier dans LOG.md. Si ce n'est pas faisable, note-le dans DECISIONS.md avec la raison.


## Next request (2026-10-23 17:52, 01-nouvelle-demande-porter-le-projet-du-plu)

# Nouvelle demande : porter le projet du plugin Pape…

Demandé le 2026-10-23 19h52.

Nouvelle demande : porter le projet du plugin Paper (rrp.bukkit) vers un mod NeoForge pour Minecraft 1.21.1 (NeoForge 21.1.x, Java 21). Le plugin Paper est abandonné : une fois le portage vérifié, supprimer rrp.bukkit, plugin.yml et les scripts Paper (tools/fetch_paper.sh). Le mod doit fonctionner en solo (serveur intégré) ET sur serveur dédié avec un seul jar : logique purement côté serveur, aucun écran ni rendu client, textes via chat et action bar.

Garder rrp.core et rrp.sim tels quels (avec leurs tests JUnit et le simulateur). Réécrire la couche rrp.bukkit en couche NeoForge : point d'entrée @Mod et neoforge.mods.toml (build avec ModDevGradle ou NeoGradle), ticks via événements, commandes Brigadier (mêmes sous-commandes que /respublica, alias /rrp), données persistantes des villageois via attachments NeoForge, scanner de village, entrepôt (tonneau), naissances, crises, camp dissident, guerre civile, comportements par rôle (fermier, forestier, mineur, garde, intendant, bâtisseur, diplomate), action bar, lang_fr.yml, persistance villages.json et config.yml.

Remplacer le selftest Paper par des GameTests NeoForge (ou un équivalent sur serveur NeoForge dédié), adapter .athanor/check.sh pour que « CHECK: PASSED » reste le critère, et livrer le jar du mod dans deliverable/ avec TEST_GUIDE.md et README.md mis à jour. Ajouter ce portage au PLAN.md sous forme de jalons : build NeoForge, commandes, villageois et persistance, comportements, crises et guerre, tests, livraison et nettoyage de l'ancien plugin.



## Next request (2026-10-23 21:38, 01-2-économie-locale-et-urbanisme-autonome-)

# 2. Économie Locale et Urbanisme Autonome Chaîne lo…

Demandé le 2026-10-23 23h10.

2. Économie Locale et Urbanisme Autonome
Chaîne logistique et entrepôts centraux :

Implémentation d'un bloc maître (« Hôtel de Ville » / « Town Hall ») qui délimite le centre névralgique du village.

Les collecteurs déposent bois, pierre, minerais et récoltes dans des coffres communautaires.

Expansion territoriale et construction procédurale :

Dès qu'un quota de ressources et d'habitants est atteint, l'intendant commande la construction d'un nouveau bâtiment via un pool de schématiques (formats .nbt adaptables selon le biome).

Les bâtisseurs nivellent le terrain, posent les blocs bloc par bloc (façon MineColonies, mais de manière 100 % autonome sans intervention obligatoire du joueur).

Apparition d'infrastructures d'expansion : routes en terre battue puis pavées entre les avant-postes, tours de guet aux frontières, palissades en bois puis remparts en pierre.



## Next request (2026-10-24 22:16, 01-3-système-territorial-et-factions-généra)

# 3. Système Territorial et Factions Génération de f…

Demandé le 2026-10-25 00h16.

3. Système Territorial et Factions
 Génération de frontières :
 Carte d’influence basée sur des marqueurs de territoire (bornes frontières, bannières de faction).
 Rayon d’action et d’exploitation défini par chunk. Exploiter un chunk revendiqué par une faction voisine est perçu comme une provocation ou un acte de guerre.
 États et idéologies :
 Selon les ressources dominantes du biome de départ, la civilisation adopte une doctrine émergente :
 Cité marchande (plaines/côtes : priorité aux routes, marchés et neutralité armée).
 Empire belliqueux (badlands/montagnes : extraction minière intensive, armées nombreuses, expansion agressive).
 Théocratie isolationniste (forêts denses/marais : méfiance des étrangers, structures monumentales, magie/alchimie).



## Next request (2026-10-25 01:02, 01-4-diplomatie-et-événements-géopolitiques)

# 4. Diplomatie et Événements Géopolitiques Matrice …

Demandé le 2026-10-25 00h17.

4. Diplomatie et Événements Géopolitiques
 Matrice relationnelle dynamique (IA inter-villages) :
 Calcul en temps réel d’un indice relationnel (-100 à +100) entre chaque cité connue.
 Envoi de caravanes marchandes avec gardes d’escorte pour commercer les surplus (ex. échange de fer contre du blé).
 En cas de litige frontalier ou de pénurie critique : déclaration formelle de guerre, rupture des traités commerciaux, ou signature d’alliances défensives.
 Guerres et sièges :
 Mobilisation des régiments de gardes équipés du matériel forgé localement (fer, cuir, arcs, arbalètes).
 Fabrication et usage d’engins de siège rudimentaires (béliers, catapultes détruisant les portes et brisant les remparts).
 Résolution du conflit : pillage des entrepôts, annexion du territoire avec tribut régulier, ou destruction de l’Hôtel de Ville.



## Next request (2026-10-25 08:33, 02-5-intégration-et-liberté-du-joueur-rôles)

# 5. Intégration et Liberté du Joueur Rôles multiple…

Demandé le 2026-10-25 00h17.

5. Intégration et Liberté du Joueur
 Rôles multiples et non-linéaires :
 Souverain : Possibilité de fonder sa propre faction en posant un Hôtel de Ville, d’édicter des lois, de fixer des taxes et de tracer les plans d’urbanisme.
 Mercenaire : Être engagé par une cité pour éliminer un camp de pillards ou participer à une invasion contre une solde substantielle.
 Marchand libre : Exploiter les différences de prix entre régions pour s’enrichir (arbitrage économique).
 Observateur neutre : Aucun ciblage forcé par le mod ; le monde évolue en arrière-plan sans bloquer l’expérience classique de survie.
 Interface & Outils :
 Une table cartographique avancée permettant de visualiser la carte politique du monde (frontières, routes commerciales, zones de conflit).
 Registre diplomatique pour négocier directement avec les émissaires.



## Next request (2026-10-25 13:50, 03-contraintes-techniques-performance-pour-)

# Contraintes Techniques & Performance (Pour le mote…

Demandé le 2026-10-25 00h18.

Contraintes Techniques & Performance (Pour le moteur)
 Simulation asynchrone / hors-chunk :
 Pour éviter la saturation des calculs CPU (TPS drops), les cités lointaines non chargées doivent être gérées via une simulation mathématique simplifiée (calcul probabiliste des récoltes, déplacements abstraits des armées sur la carte globale).
 Rendu et pathfinding physique complets uniquement lorsque les chunks sont actifs ou chargés par le joueur.
 Compatibilité :
 Compatibilité totale avec les biomes vanilla et les principaux mods de génération de terrain (comme Terralith).

