# ROADMAP — GeoPoliticsMod (nom provisoire)

Mod géopolitique pour **Minecraft 1.21.1 / NeoForge (21.1.x) / Java 21** : nations joueurs et PNJ, régimes et lois, territoires, économie monétaire, diplomatie, ONU, guerres et espionnage.

- ModID provisoire : `geopoliticsmod` (constante `GeoPoliticsMod.MODID` + `mod_id` dans `gradle.properties`).
- Package racine : `com.geopoliticsmod`.
- Chaque phase se termine par un `./gradlew build` qui passe et une liste de tests en jeu. On passe à la phase suivante après validation.

---

## 1. Principes d'architecture

### Séparer les données, la logique et la présentation
| Couche | Où | Règle |
|---|---|---|
| **Données** | records/classes immuables + `Codec` | sérialisables, sans référence à `Level` ni `Player` (on stocke des `UUID`) |
| **Logique** | `*Manager` / `*Service` côté serveur | seule couche qui modifie les données ; marque le `SavedData` concerné comme modifié (`setDirty`) |
| **Réseau** | `network` | le serveur fait foi ; le client ne reçoit que ce dont il a besoin (paquets de synchro + deltas) |
| **Présentation** | `client` | lit un cache client, n'envoie que des *intentions* (« je veux voter la loi X »), que le serveur valide |

### Persistance
- **`SavedData` par domaine**, stocké dans l'Overworld : `nations`, `territory`, `economy`, `diplomacy`, `wars`, `npc`. Une modification d'un domaine ne réécrit pas tout le reste.
- **`DataAttachments`** pour l'état par joueur (nation, rôle, dernier chunk/pays vu pour la détection de frontière, statut d'espion) et par chunk si besoin.
- **`DataComponents`** pour les items (billets/pièces : nation émettrice, valeur faciale, numéro de série).
- Identité : toute entité métier a un `UUID` stable ; les noms sont des propriétés modifiables.

### Performance (TPS)
- **Aucune logique lourde dans `ServerTickEvent`.** Un `GeoScheduler` unique compte les ticks et lance chaque système à son intervalle (configurable dans `GeoConfig`) : économie toutes les 5 min, IA des PNJ toutes les 2 min, Power toutes les minutes… Les tâches sont **décalées** pour ne pas tomber sur le même tick.
- **Calculs lourds hors du thread serveur** (taux de change, décisions IA, scores) : on prend un **instantané immuable** des données sur le thread serveur, on calcule sur un `Executor` dédié, puis on applique le résultat sur le thread serveur (`server.execute(...)`). On ne touche jamais au monde (`Level`) depuis un autre thread.
- **Index spatial** : `Long2ObjectOpenHashMap<NationId>` indexé par `ChunkPos.toLong()` → recherche du propriétaire d'un chunk en O(1). Index inverse nation → `LongOpenHashSet` de chunks.
- **Frontières** : on ne vérifie que lorsque le joueur **change de chunk** (comparaison avec la valeur gardée en attachment), et au plus toutes les `borderCheckInterval` ticks.
- **Nations PNJ** : la nation est une **simulation abstraite** (données + décisions périodiques) qui tourne même sans chunk chargé. Les **entités** PNJ n'apparaissent qu'autour des joueurs, sur un territoire PNJ chargé, avec un plafond par joueur/zone ; elles disparaissent (despawn) quand plus personne n'est à proximité.
- Réseau : on synchronise des **deltas** (claim ajouté/retiré) plutôt que des cartes complètes.

### Extensibilité
- Lois, résolutions de l'ONU, buts de guerre, noms de nations PNJ et pools de skins sont **data-driven** (JSON en datapack/resource pack), rechargés avec `/reload`.
- Package `api` : interface `PowerContributor` et événements NeoForge (`NationCreatedEvent`, `WarDeclaredEvent`…) pour que d'autres mods (ou des scripts KubeJS) puissent se brancher.
- Compatibilités dans `compat.<mod>`, chargées seulement si `ModList.get().isLoaded(...)`, avec des dépendances `compileOnly` pour que le mod se lance sans elles.

---

## 2. Arborescence des packages

```
com.geopoliticsmod
├── GeoPoliticsMod            point d'entrée commun
├── GeoPoliticsModClient      point d'entrée client
├── api                       API publique (PowerContributor, événements, requêtes de territoire)
├── core
│   ├── config                GeoConfig (serveur), config client
│   ├── registry              ModItems, ModBlocks, ModBlockEntities, ModMenus, ModEntities,
│   │                         ModDataComponents, ModAttachments, ModCreativeTabs
│   ├── scheduler             GeoScheduler (ticks espacés), GeoExecutor (calculs hors thread)
│   └── data                  SavedData par domaine, codecs partagés
├── nation                    Nation, NationId, Member, NationRole, City, Flag, NationManager
├── government                StateForm, ConstitutionalSystem, Ideology, Regime,
│                             RegimeEvaluator (mutation), TitleResolver (Président, Roi…)
├── law                       LawDefinition (JSON), LawLoader, IdeologyWeights, NationLaws, LawVote
├── territory                 ClaimIndex (Long2Object), TerritoryManager, ClaimProtection,
│                             BorderTracker (titres d'entrée/sortie)
├── power                     PowerCalculator, SpecialBuilding (+ blocs), sources de Power
├── economy                   Currency, MoneyComponent, BanknoteItem, CoinItem, MintBlock,
│                             PrintingPressBlock, Treasury, MoneySupply, ExchangeRates, Inflation
├── diplomacy                 Relation, Treaty, TreatyClause, Alliance, Vassalage, Tribute, LibertyDesire
│   └── un                    UnitedNations, GeneralAssembly, SecurityCouncil, Resolution, ResolutionEffect
├── war                       War, WarGoal (casus belli), WarScore, PeaceDeal, Truce
├── espionage                 SpyMission, Intel, CounterIntelligence, Trial, Sentence
├── npc                       NpcNationGenerator, NameGenerator
│   ├── ai                    NpcNationBrain (décisions abstraites), personnalité/idéologie
│   └── entity                NpcCitizenEntity, NpcGuardEntity, NpcSpawner, SkinPool
├── network                   GeoNetwork (enregistrement), payloads S2C/C2S
├── client                    ClientNationCache, HUD de frontière, rendu des drapeaux
│   └── screen                écrans : nation, drapeau, lois, économie, diplomatie, ONU, jugement, admin
├── compat
│   └── xaero                 frontières et couleurs sur Xaero's Minimap / World Map
└── admin                     /geoadmin, AdminService, écran opérateur
```

Ressources :
```
src/main/resources/
├── assets/geopoliticsmod/    lang (fr_fr, en_us), textures (billets, pièces, blocs, skins PNJ), modèles
└── data/geopoliticsmod/
    ├── geopoliticsmod/laws/              une loi par fichier JSON
    ├── geopoliticsmod/resolutions/       résolutions prédéfinies de l'Assemblée générale
    ├── geopoliticsmod/war_goals/         buts de guerre
    ├── geopoliticsmod/npc_names/         pays réels / générateur procédural
    └── tags/                             tags d'items (ex. items acceptés comme tribut)
```

---

## 3. Phases

### Phase 0 — Socle ✅ (cette étape)
- Projet NeoForge 1.21.1 (ModDevGradle), Java 21, CI GitHub Actions.
- Points d'entrée commun et client, configuration serveur (intervalles de l'ordonnanceur).
- Arborescence des packages documentée (`package-info.java`).
- **Test :** `./gradlew build` passe ; `./gradlew runClient` ouvre le jeu avec le mod dans la liste.

### Phase 1 — Structures de données & nations de base ✅ (code livré, tests en jeu à faire)
- `GeoScheduler` (ticks espacés, décalés, alerte si une tâche dépasse 5 ms) + `GeoExecutor` (calcul sur instantané, application sur le thread serveur).
- `Nation` (UUID, nom, devise/motto, couleur, capitale, villes, membres + rangs, date de création, `controller` = JOUEUR | PNJ), `City`, `NationRole` (Citoyen < Haut gradé < Dirigeant).
- `NationSavedData` (codecs, champ `format` pour les migrations futures) avec index en mémoire joueur → nation et nom → nation.
  *Choix :* l'appartenance est stockée dans le SavedData et non en attachment joueur, car elle doit pouvoir changer quand le joueur est hors ligne (exclusion, dissolution).
- `NationManager` : règles métier (rangs, validations, notifications) ; invitations en mémoire, valables 5 min, purgées par l'ordonnanceur.
- Commandes : `/nation create|info [nom]|list|invite|join|leave|kick|promote <joueur> <leader|officer|citizen>|disband [confirm]|motto|city add|city remove|capital <ville>` ; `/geoadmin nation disband <nom>` (OP, journalisé).
- Synchro réseau : liste des nations (`NationListPayload`) et nation/rang du joueur (`PlayerNationPayload`) → `ClientNationCache`.
- Traductions FR et EN.

| Action | Rang minimum |
|---|---|
| inviter, exclure (un rang inférieur), fonder/supprimer une ville | Haut gradé |
| changer les rangs, transmettre la direction, devise, capitale, dissoudre | Dirigeant |

- **Tests en jeu :** créer une nation, inviter un 2e joueur (lien cliquable), redémarrer le serveur → tout est conservé ; un non-dirigeant ne peut pas dissoudre ; le dirigeant ne peut pas partir sans transmettre la direction (sauf s'il est seul : la nation est dissoute).

### Phase 2 — Territoires & frontières ✅ (code livré, tests en jeu à faire)
- `TerritorySavedData` : index chunk → nation (`Long2ObjectOpenHashMap` par dimension) + index inverse nation → chunks (`LongOpenHashSet`), sauvegardés en tableaux de `long`.
- `/nation claim [rayon 0-3]` (Haut gradé) : chunk courant ou carré centré, examiné du centre vers l'extérieur ; `/nation unclaim`.
  Limite = `baseClaims` (16) + `claimsPerMember` (8) × membres — le Power s'y ajoutera en phase 7. Contiguïté par un côté (configurable), le premier chunk d'une dimension est libre.
- Une ville doit être fondée sur le territoire de la nation ; un chunk qui contient une ville ne peut pas être libéré.
- Dissolution d'une nation → événement `NationDisbandedEvent` (package `api.event`) → ses chunks sont libérés. Au démarrage, les chunks de nations inexistantes sont nettoyés.
- `ClaimProtection` (côté serveur) pour les non-membres : casser, poser, utiliser un bloc ou un objet sur un bloc, seaux, cadres / porte-armures / véhicules à inventaire, explosions (configurable). Manger, combattre, marcher restent permis. Machines « faux joueurs » autorisées par défaut (Create, Mekanism), configurable.
- `BorderTracker` (ordonnanceur, toutes les 10 ticks) : titre à l'entrée (nom en couleur + devise) et à la sortie (« Terres libres »). Recherche seulement au changement de chunk ; un 2e passage en moins de 3 s s'affiche en barre d'action. Le régime s'ajoutera au sous-titre en phase 4.
- Synchro client : instantané complet à la connexion, puis deltas (`ClaimDeltaPayload`) → `ClientTerritoryCache` (prêt pour Xaero).
- Admin : `/geoadmin bypass`, `/geoadmin territory claim <nation> | unclaim | info` (journalisés).
- **Reste ouvert :** coût des claims (attend l'économie, phase 6) ; politique FTB Chunks / MineColonies ; contraptions Create et pistons/fluides venant de l'extérieur (pas d'événement NeoForge fiable : à traiter en compat).
- **Tests en jeu :** `/nation claim 3`, traversée de frontière (titre puis barre d'action), un 2e joueur sans nation ne peut ni casser ni ouvrir un coffre, une TNT n'abîme pas le territoire, `/nation disband confirm` libère tout ; `/neoforge tps` stable.

### Phase 3 — Intégration Xaero's Minimap & World Map ✅ (code livré, test en jeu à faire)
- Cible : versions du pack ZZCCMXTP — World Map **1.47.0**, Minimap **26.6.0** (NeoForge 1.21.1). Dépendances `compileOnly` + `localRuntime` depuis le Maven de Modrinth : jamais embarquées dans le jar ni dans le dépôt (mods « All Rights Reserved »).
- Xaero n'a pas d'API publique pour ajouter un calque : chaque session crée un `HighlighterRegistry` et le verrouille avec `end()`. Deux **mixins** (client) ajoutent nos surligneurs juste avant : un pour la World Map, un pour la Minimap (dans ces versions la Minimap ne reprend pas ceux de la World Map). Un `IMixinConfigPlugin` n'applique chaque mixin que si le mod visé est installé.
- `NationWorldMapHighlighter` / `NationMinimapHighlighter` : remplissage translucide à la couleur de la nation, trait de frontière plus opaque là où le voisin n'est pas de la même nation, infobulle avec le nom de la nation.
- Rafraîchissement : à chaque delta, `WorldMapRefresher` efface le cache des régions (512×512 blocs) touchées et voisines — même méthode que la compat Open Parties and Claims de Xaero.
- `ClientTerritoryCache` rendu sûr pour les threads de rendu de Xaero (copie publiée à chaque changement) + index par région.
- **Tests en jeu :** `./gradlew runClient` (Xaero est inclus dans le jeu de développement) → `/nation claim 2` → le territoire apparaît sur la minimap et sur la carte (touche M), infobulle au survol ; `/nation unclaim` ou `/nation disband confirm` → la carte se met à jour sans relancer.
- **Risque :** le mixin dépend de `HighlighterRegistry.end()` ; à revérifier à chaque mise à jour de Xaero (s'il disparaît, le jeu se lance quand même mais sans calque des nations : config mixin non obligatoire, l'échec est seulement signalé dans les logs).

### Phase 4 — Régimes, titres & lois
- Enums des 3 axes, `Regime` = combinaison + validation des combinaisons incohérentes.
- `TitleResolver` : titre du dirigeant et des hauts gradés selon le régime ; affichage dans la TabList (`PlayerEvent.TabListNameFormat`) et le chat (`PlayerEvent.NameFormat`), rôles majeurs uniquement.
- `LawLoader` (datapack, rechargeable) : lois avec catégorie, effets, **poids idéologiques** ; jeu de lois par défaut selon le régime choisi.
- Votes/modification des lois selon le régime (décret du dirigeant, vote des membres, parlement…).
- `RegimeEvaluator` : somme des poids des lois actives → mutation automatique du régime quand un seuil est franchi (avec annonce).
- Écran GUI des lois.
- **Tests :** une république qui vote 3 lois autoritaires devient une dictature ; le titre change dans la TabList ; `/reload` charge une loi ajoutée.

### Phase 5 — Interface nation & drapeaux
- Assistant de création en GUI (nom, devise, régime à 3 axes, couleur, drapeau).
- Éditeur de drapeau par calques (motifs de bannière vanilla + couleurs), stocké comme données de motifs, rendu sur bannière, en GUI et sur Xaero.
- Écran « Ma nation » : membres, villes, capitale, territoire, Power, lois.
- **Tests :** création complète sans commande ; le drapeau s'affiche pareil partout.

### Phase 6 — Économie & monnaie
- `Currency` par nation ; items billet/pièce avec `DataComponent` (nation émettrice, valeur, série).
- Blocs Hôtel de la Monnaie (pièces) et Planche à billets (billets) : seuls les membres autorisés de la nation émettrice peuvent produire ; la contrefaçon est une action illégale détectable (amende/sanction).
- Trésor national, masse monétaire, réserves (items/or déposés).
- `ExchangeRates` (calcul hors thread) : f(Power, réserves, masse monétaire, stabilité) ; bureau de change.
- Inflation : création excessive → dépréciation + malus (stabilité, Power, bonheur).
- **Tests :** impossible d'imprimer la monnaie d'un autre pays ; imprimer ×10 fait chuter le taux ; le change respecte les taux.

### Phase 7 — Power & bâtiments spéciaux
- `PowerCalculator` : population, territoire, économie, armée, bâtiments, bonus de lois ; contributeurs externes via `api.PowerContributor`.
- Bâtiments spéciaux (Entrepôt d'armement, etc.) : blocs de contrôle avec conditions (multibloc simple ou inventaire minimal) → Power passif ; cibles stratégiques (détruire = perte de Power).
- Compat optionnelle FTB Quests (récompense de quête → Power).
- **Tests :** construire un entrepôt augmente le Power au cycle suivant ; le détruire le réduit.

### Phase 8 — Diplomatie, alliances & vassalité
- Relations entre nations (score), traités libres (texte) ou par clauses prédéfinies, alliances (défense mutuelle).
- Vassalité : condition de Power, tribut périodique (monnaie/items), désir de liberté, demande de baisse de tribut, guerre d'indépendance.
- Écran diplomatie.
- **Tests :** vassaliser une nation plus faible, recevoir le tribut automatiquement, déclencher une révolte.

### Phase 9 — Guerres
- Déclaration (joueurs ou PNJ) avec buts de guerre choisis (annexion de claims, transfert d'items, tribut, vassalisation).
- Pendant la guerre : protections levées entre belligérants, score de guerre (morts, bâtiments détruits, contrôle de zones).
- Fin : victoire (application des buts + transfert de Power), capitulation, **paix blanche → trêve de 7 jours réels** (`System.currentTimeMillis()`, survit aux redémarrages).
- **Tests :** guerre complète entre 2 joueurs ; après paix blanche, nouvelle déclaration refusée jusqu'à la fin de la trêve.

### Phase 10 — Espionnage
- Rôle d'espion, missions d'infiltration (déguisement : pas de titre de frontière affiché aux défenseurs, etc.).
- Renseignements : localisation des bâtiments spéciaux, coffres forts, faiblesses.
- Contre-espionnage : chance de détection (lois, bâtiments, gardes), outils de détection.
- Capture → écran **Jugement** pour le dirigeant : exécution, prison, amende, expulsion…
- **Tests :** un espion obtient la position d'un entrepôt ; capturé, il est jugé et la sentence s'applique.

### Phase 11 — Nations PNJ
- Générateur de nations (pays réels ou noms procéduraux), placement dans le monde, territoire initial.
- `NpcNationBrain` (abstrait, hors thread) : personnalité/idéologie, économie, diplomatie, déclaration de guerre, votes à l'ONU.
- Entités : citoyens et gardes avec noms et skins aléatoires (pool de textures), apparition uniquement autour des joueurs, plafonnée.
- **Tests :** les PNJ évoluent pendant que personne n'est chez eux ; aucune entité PNJ hors zone chargée ; TPS stable.

### Phase 12 — ONU
- Membres = nations découvertes (joueurs + PNJ).
- Assemblée générale : résolutions prédéfinies (JSON), vote des joueurs + vote PNJ selon idéologie/relations.
- Conseil de sécurité : joueurs éligibles uniquement ; résolutions en texte libre + effets codés (embargo, sanctions, perte de Power) ; vote.
- **Tests :** une résolution d'embargo bloque les échanges monétaires entre les nations visées.

### Phase 13 — Administration
- Les commandes `/geoadmin` arrivent au fil des phases ; ici, l'écran opérateur complet : frontières, régimes, Power, trésors, dissolution, annulation de guerres/trêves.
- **Tests :** chaque action admin est journalisée et visible en jeu.

### Phase 14 — Intégration au modpack & finition
- Test avec le pack complet (≈250 mods) : temps de chargement, conflits de claims, TPS (profil avec spark).
- Équilibrage, traductions, documentation joueur.

---

## 4. Compatibilité avec le modpack — points à surveiller
| Mod du pack | Point de friction | Approche prévue |
|---|---|---|
| **FTB Chunks**, **MineColonies** | ils ont leurs propres claims/protections | nos claims sont « nationaux » : on ne bloque que ce que nos règles demandent ; option de config pour exiger qu'un chunk FTB soit aussi dans la nation (à trancher en phase 2) |
| **Xaero's Minimap / World Map** | pas d'API publique documentée | fait en phase 3 : mixins sur `HighlighterRegistry.end()`, appliqués seulement si Xaero est présent |
| **NeoForge du pack (21.1.256)** | un mod qui exige une version plus récente ne se charge pas | le projet compile contre **21.1.256** (`neo_version`) : ne le monter que si le pack monte |
| **Create (+ Aeronautics, Sable)** | contraptions/aéronefs qui traversent les frontières et cassent des blocs | gérer la protection des claims face aux contraptions en phase 2 |
| **MCA Reborn, Goblin Traders, Kobolds!** | autres PNJ dans le monde | nos entités ont leur propre type ; pas de modification des villageois |
| **FTB Quests, KubeJS** | sources de Power / scripts | via `api` et événements NeoForge |
| **Tensura, Iron's Spells, Apotheosis…** | combat et sorts en guerre | on utilise l'équipement réel ; on ne touche pas aux dégâts, seulement à l'autorisation PvP entre nations |

## 5. Décisions ouvertes
- Nom définitif du mod (renommage : `mod_id`, `GeoPoliticsMod.MODID`, package racine).
- Coût des claims (monnaie nationale, items, Power ?).
- Politique de coexistence avec FTB Chunks.
- Monde des nations PNJ : générées au démarrage, au fil de l'exploration, ou placées par un admin ?
