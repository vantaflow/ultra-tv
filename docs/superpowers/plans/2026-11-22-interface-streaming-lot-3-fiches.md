# Interface façon streaming — lot 3 : fiches film et série (TV, téléphone, tablette) — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Les fiches film et série s'ouvrent sur une image plein écran voilée, titre ou logo, infos, reprise et actions nommées (maquettes `FicheFilm`, `FicheSerie`), avec la même mise en page déclinée au toucher ; elles disent ce qui ne va pas (titre introuvable, épisodes non chargés, contenu verrouillé) au lieu de rester sur « Chargement… », et rendent le focus à l'élément quitté — l'épisode compris — au retour du lecteur.

**Architecture:** On garde les écrans existants et leurs ViewModels : `MovieDetailScreen` / `MovieDetailTv` (`ui/movies`, `ui/detail/DetailV2.kt`), `SeriesDetailScreen` / `SeriesDetailTvContent` (`ui/series`, `ui/detail/SeriesDetailTv.kt`). Les rendus restent SANS ViewModel (favori, téléchargement, Trakt arrivent par des « slots ») pour être testés avec des données fixes. Nouveautés : un état de chargement explicite (`DetailLoad`) et un état de verrou (`DetailLock`), purs, dans `DetailLogic.kt` ; une couche de fond commune (`DetailBackdropLayer`) ; un panneau d'états commun TV / toucher (`DetailStatePanel`) ; la restauration du focus de l'accueil (`HomeFocusRestore`, `rowMemory`) réutilisée telle quelle pour les fiches.

**Tech Stack:** Kotlin, Jetpack Compose (foundation, tv-material3), Hilt, Room, Coil 3, Media3 (bande-annonce, inchangée) ; tests Robolectric + compose-ui-test.

**Spec:** `docs/superpowers/specs/2026-11-21-interface-streaming-design.md` (écrans 3 et 4, section « Périmètre élargi le 2026-11-21 »). Maquettes : `FicheFilm.dc.html`, `FicheSerie.dc.html` (1920 × 1080, unités `design`) ; pour le toucher, langage de `Mobile.dc.html` (dp). Dépend du lot 2 pour `UiLog`, `uiAttempt`, `ImageFailureTally`, `FocusSurface(ringColor)` et l'anneau tactile des boutons sur image : si le lot 3 passe avant, sa tâche 2 crée ces pièces avec EXACTEMENT les signatures du plan du lot 2 (`docs/superpowers/plans/2026-11-22-interface-streaming-lot-2-accueil-telephone.md`, tâches 2 et 3).

Chemins abrégés : `K = android-native/app/src/main/kotlin/com/ultratv/tv/nativeapp`, `T = android-native/app/src/test/kotlin/com/ultratv/tv/nativeapp`, `R = android-native/app/src/main/res`. Commandes lancées depuis `android-native/`. Test ciblé : `./gradlew --offline -q :app:testDebugUnitTest --tests '<motif>'`.

## Points à trancher par le propriétaire

Le plan applique la proposition ; chaque point est isolé dans une tâche et se défait seul.

1. **Fiche TV entièrement posée sur l'image** (maquettes) : textes clairs, boutons blancs, voile sombre dans les 10 thèmes, comme le lecteur. Les thèmes y gardent leur accent (progression, saison choisie, anneau des affiches et des avatars). Alternative : la partie basse (similaires, distribution) sur la couleur de page du thème. Tâches 4 à 6.
2. **Distribution du film** : la maquette ne montre qu'une ligne « Avec : … ». La ligne est ajoutée ET la rangée d'avatars (accès aux pages des personnes) est gardée, sous « Titres similaires ». Tâche 5.
3. **Contrôle parental sur les fiches** : aujourd'hui un film ou une série d'une catégorie verrouillée s'ouvre sans code depuis l'accueil, la recherche, Ma liste ou Trakt (le verrou n'agit que sur les chaînes). Proposé : fiche remplacée par « Contenu verrouillé » + « Déverrouiller » (fenêtre de code existante), déverrouillage valable pour la visite ; profil Enfants et catégorie adulte masquée → « Non disponible pour ce profil », sans code. Tâche 3 (indépendante : la retirer suffit si le point est refusé).
4. **Bouton principal au toucher** : aujourd'hui le gros bouton « Lecture » d'un film commencé REPART DU DÉBUT et efface la position (`restart` → `history.remove`), « Reprendre à … » étant le second bouton. Proposé, comme sur TV : principal = « Reprendre » (position gardée), second = « Depuis le début ». Tâche 7.
5. **Fiche série TV : saisons en liste verticale à gauche** (maquette) au lieu des onglets au-dessus des épisodes ; « Télécharger la saison » passe dans la rangée des actions ; ◀ depuis un épisode va sur la saison choisie (aujourd'hui sur « Reprendre »). Tâche 6.
6. **Titres similaires d'une série** : aucune source n'existe (seul `similarMovies` existe) et la maquette n'en montre pas. Proposé : pas de rangée pour les séries dans ce lot (rien ne disparaît).

## Global Constraints

Conditions d'acceptation de CHAQUE tâche (exigences du propriétaire, recopiées) :

- « Tous les écrans, toute l'application, sans la moindre erreur, pour tous les appareils » : box et téléviseurs Android TV / Fire TV à la télécommande, téléphones et tablettes au toucher (portrait et paysage, clavier ou manette), du plus modeste (2 Go, 32 bits, Android 7.1) au plus récent.
- Aucune anomalie, aucun plantage : clés uniques dans les listes paresseuses, pas d'index hors bornes sur liste vide ou à un élément (`resumeTarget` déjà gardé : `SeriesDetailRulesTest.resumeTarget_sansEpisode_retourneNull`), pas de `FocusRequester` non attaché (toute demande passe par `runCatching { requestFocus() }` journalisé ou par une cible dont l'attache est connue), états vides et d'erreur dessinés.
- Navigation à la télécommande logique et stable : aucun piège, aucun focus perdu, aucune touche avalée, pas de `LaunchedEffect` qui déplace le focus à retardement (règle de c7da3d10 : une demande de focus initiale s'arrête dès qu'une touche a été pressée depuis l'ouverture, `InputClock.lastKeyMs > startMs`) ; chaque rangée retient sa carte ; retour du lecteur ou d'une page de personne sur l'élément quitté ; ◀ depuis le premier élément d'une ligne : recherche habituelle (menu latéral s'il est affiché) ; Retour : d'abord fermer ce qui est ouvert (bande-annonce, notes Trakt, fenêtre de code), puis quitter la fiche (`K/ui/components/RailBack.kt` laisse passer les sous-niveaux : inchangé) ; RTL (arabe) cohérent.
- Les 10 thèmes conservés (DARK, LIGHT, AUTO, OLED, MIDNIGHT, FOREST, PLUM, SAND, CONTRAST, TEAL) : jetons `Ux.*`, aucune couleur en dur hors de l'exception « posé sur une image » (voile `#0A0C10`, encres claires `ImageInk`, boutons blancs `OnImageButton`, anneau blanc) ; anneau de focus du thème partout ailleurs ; contrastes vérifiés par test sur tous les thèmes.
- Performance « au top » : rien d'ajouté par image ou par recomposition ; un déplacement de focus ne recompose que l'élément quitté, l'élément atteint et ce qui en dépend (compteur de recompositions sur la liste d'épisodes et la rangée des similaires, tâche 6) ; UNE image de fond décodée à taille explicite (TV 1280 × 720, toucher à la taille affichée), aucun flou (`BlurredFill` et son `blur(48)` disparaissent des fiches), voiles par `drawWithCache` ; listes paresseuses avec `key` et `contentType` ; agrandissement par `graphicsLayer`.
- Textes : en, fr, es, ar écrits à la main (`R/values{,-fr,-es,-ar}/strings.xml` + golden `T/../resources/i18n-golden/{en,fr,es,ar}.json`), les autres langues par Weblate (ne jamais modifier les autres `values-xx`) ; en arabe tous les nombres en chiffres latins : durées et « SxxÉyy » par `DisplayLocale` / `tx` (défauts relevés : `MovieDetailScreen.kt:379` `"%02d".format`, `SeriesViewModel.kt:82` titre persisté → `Locale.ROOT`) ; aucun texte en dur ; aucun texte tronqué dans une zone de hauteur fixe (`NoTextWrap`, vérifié à la taille de texte 1,3).
- Gestion d'erreur et journaux (spec, puce « Gestion d'erreur et journaux ») : chargement, vide, introuvable, erreur et verrou dessinés avec un message compréhensible et une action (Réessayer, Retour, Déverrouiller) ; aucun `runCatching { }` / `catch` dont l'échec disparaît sans trace dans les fichiers touchés (les deux ViewModels en ont une douzaine) ; toute erreur attrapée passe par `uiAttempt` / `UiLog.failure(écran, action, cause)` ; jamais d'identifiant, de mot de passe ni d'adresse de flux complète dans un journal (`UtvLog.maskUrl`, `LogSanitizer`) ; `CancellationException` relancée.
- Zéro régression : la logique (ViewModels, dépôts, DAO) ne change pas sauf ajout nécessaire à l'affichage (états `load`, `lock`, `episodesError`, `retry`, lecture des catégories verrouillées) ; préférences des utilisateurs jamais réinterprétées ; correctifs passés conservés (liste dans la tâche 1).
- Jamais de mention d'une appli concurrente (code, commentaires, commits, CHANGELOG) ; jamais de ligne `Co-Authored-By` ni `Claude-Session` dans les commits.
- `I18nGoldenTest` vert sous JDK 17 et 27 : tout échec est un vrai échec.
- Le lot se termine par : suite complète verte (aucun échec accepté), captures (sombre, clair, arabe ; TV, téléphone portrait et paysage, tablette), ligne(s) de CHANGELOG, liste de contrôle sur appareil réel (tâche 9).

## Review Focus

1. Titre supprimé de la source entre l'accueil et la fiche (synchro passée entre-temps) : aujourd'hui « Chargement… » pour toujours ; attendu : « Ce titre n'est plus disponible » + Retour, focus sur Retour. → test tâche 2 (`film_introuvable_messageEtRetour`).
2. Série dont les épisodes ne se chargent pas (fournisseur injoignable, réponse illisible) : aujourd'hui « Aucun épisode » (l'échec de `loadEpisodes` est avalé) ; attendu : la cause et « Réessayer », une ligne de journal sans identifiant. → test tâche 2 (`serie_episodesEnErreur_causeEtReessayer_journalMasque`).
3. Retour du lecteur après avoir regardé l'épisode 4 de la saison 3 alors que la reprise visait la saison 2 : la saison 3 est affichée et le focus est sur l'épisode 4, sans passage par un autre élément. → test tâche 6 (`retourDuLecteur_saisonEtEpisodeQuittes`).
4. Fiche sans aucune image (ni fond, ni affiche, ni logo), en thème clair : le texte reste lisible (voile sombre sur aplat sombre), aucun cadre blanc. → test tâche 4 (`sansImage_themeClair_texteLisible`).
5. Série à une seule saison, saison à un épisode, série de 30 saisons : aucune impasse, toutes les saisons atteignables, rien hors bornes. → tests tâche 6 (`uneSaisonUnEpisode`, `trenteSaisons_toutesAtteignables`).

---

### Task 1: Filet de non-régression des fiches

Aucun changement visuel. On fige ce qui marche et que les tâches suivantes doivent garder.

**Files:**
- Create: `T/ui/detail/DetailHost.kt` (hôte de test du lot : TV 1920 × 1080 comme `DetailRenderTest`, toucher aux tailles du lot 2 : `390×844`, `320×568`, `844×390`, `800×1280`, `1280×800`)
- Create: `T/ui/detail/MovieDetailTvFocusTest.kt`, `T/ui/detail/DetailTouchRegressionTest.kt`
- Test (existants, inchangés) : `T/ui/detail/DetailLogicTest.kt`, `DetailRenderTest.kt`, `SeriesDetailFocusTest.kt`, `T/ui/series/SeriesDetailRulesTest.kt`, `T/ui/movies/MovieDurationTest.kt`

**Interfaces:**
- Consumes: `MovieDetailTv(m, onPrimary, onFromStart, onTrailer, onBack, onOpenPerson, playRequester, onPlayFocusChanged, favorite, actionsExtra, afterActions, below)`, `MovieDetailTvModel(...)`, `SeriesDetailTvContent(data, onSeason, onPlayTarget, onPlayEpisode, onOpenPerson, onBack, onLongPressEpisode, downloadLabel, onDownloadSeason, favorite, trakt)`, `SeriesDetailData(...)` tels qu'aujourd'hui.
- Produces: `internal fun ComposeContentTestRule.setDetail(tv: Boolean, width: Int, height: Int, lang: AppLang = AppLang.French, light: Boolean = false, fontScale: Float = 1f, keyboard: Boolean = !tv, content: @Composable () -> Unit)` ; fixtures `DetailFixtures.movie(...)`, `.series(seasons: Int, episodesPerSeason: Int)`, `.history(...)`.

Comportements à préserver :

| Comportement (correctif d'origine) | Test qui le garde |
|---|---|
| Ligne d'infos, choix de saison, saut, progression, « vu » à moins d'une minute | `DetailLogicTest` (existant) |
| Épisode à reprendre : inachevé, sinon suivant du dernier terminé, sinon suivant Trakt, sinon premier ; liste vide → null (plantage `first()` corrigé) | `SeriesDetailRulesTest` (existant) |
| Titre d'épisode nettoyé (« Daily Dose - S01E01 - … ») | `SeriesDetailRulesTest.cleanEpisodeTitle_retireSerieEtMarqueur` |
| Durées « 01:54:00 » / « 112 » → « 1 h 54 » ; `formatClock` | `MovieDurationTest` (existant) |
| Logo introuvable : le titre texte reste | `DetailRenderTest.logoIntrouvable_titreTexteConserve` (existant) |
| Fond « paysage » qui est en fait une affiche : affiche nette à droite, jamais agrandie | `fondPortrait_afficheDansSaZone` (nouveau, `DetailBackdrop`) |
| Série TV : → depuis Reprendre vers l'épisode à reprendre ; ligne de boutons → dans la ligne puis épisodes ; distribution → épisode ; 10 saisons atteignables | `SeriesDetailFocusTest` (existant ; la tâche 6 dit lesquels changent et par quoi) |
| Film TV : focus initial sur le principal (« Reprendre à … » si commencé, sinon « Regarder ») ; ▲ depuis les similaires → distribution, pas « Lecture » | `filmTv_focusInitialSurLePrincipal`, `filmTv_hautDepuisLesSimilaires_vaSurLaDistribution` |
| Film TV : principal = reprise (`vm.play`), « Depuis le début » = `restart` | `filmTv_principalReprend_depuisLeDebutRepart` |
| Bande-annonce ouverte : Retour la ferme, la fiche reste | `bandeAnnonce_retourFermeSeulement` |
| Avertissement « une seule connexion » avant une lecture en ligne pendant un téléchargement | `filmTv_lectureAppelleLAvertissement` (via le lambda `onPlay` enveloppé de `MovieDetailScreen`, testé sur la fonction extraite à la tâche 2) |
| Toucher : saisons en pastilles, épisode → lecture, bouton de téléchargement par épisode, « Télécharger la saison » | `serieTactile_saisonsEpisodesTelechargements` |
| Toucher : synopsis replié à 4 lignes puis « Plus » / « Moins » | `tactile_synopsisRepliable` |
| Toucher : bouton Retour flottant sous la barre d'état | `tactile_retourSousLaBarreDEtat` |
| Grand écran tactile (≥ 840) : deux volets | `tactile_grandEcran_deuxVolets` |

- [ ] **Step 1: Écrire l'hôte, les fixtures et les tests** du tableau sur le code ACTUEL (ajouter seulement les `testTag` nécessaires, sans autre changement : `"dt-primary"`, `"dt-from-start"`, `"dt-trailer"`, `"dt-list"`, `"dt-download"`, `"dt-back"`, `"dt-similar-<id>"`, `"dt-cast-<nom>"`, `"dt-season-<n>"`, `"dt-episode-<s>-<e>"`, `"dt-backdrop"`).
- [ ] **Step 2: Lancer** `--tests '*Detail*' --tests '*SeriesDetail*' --tests '*MovieDuration*'` → tout passe. Un test rouge ici révèle un défaut préexistant : le consigner, le corriger dans la tâche qui touche ce code.
- [ ] **Step 3: Commit** — `Fiches : tests de non-régression avant la refonte`.

### Task 2: États de chargement, introuvable et erreur, journaux

**Files:**
- Modify: `K/ui/detail/DetailLogic.kt` (état pur), `K/ui/movies/MovieDetailScreen.kt` (`MovieDetailViewModel`, branche « film nul »), `K/ui/series/SeriesViewModel.kt`, `K/ui/series/SeriesDetailScreen.kt`
- Create: `K/ui/detail/DetailStates.kt`
- Modify: `R/values{,-fr,-es,-ar}/strings.xml`, `K/i18n/DesignStringsB1.kt`, golden `{en,fr,es,ar}.json`
- Test: `T/ui/detail/DetailLogicTest.kt`, Create `T/ui/detail/DetailStatesTest.kt`, Create `T/ui/detail/DetailViewModelErrorsTest.kt`

**Interfaces:**
- Consumes: `uiAttempt`, `UiLog`, `ImageFailureTally`, `rememberImageFailureTally` (lot 2, tâche 2) ; `NetErrors.classify(t): SyncErrorKind` ; `S.sync.messageFor(kind)` ; `D.retry` ; `M.a11yBack` (« Retour ») ; `Skeleton(modifier, circle)`.
- Produces:
  - `sealed interface DetailLoad { data object Loading : DetailLoad; data object Ready : DetailLoad; data object NotFound : DetailLoad; data class Failed(val kind: SyncErrorKind) : DetailLoad }` (pur, `DetailLogic.kt`).
  - `MovieDetailViewModel.load: StateFlow<DetailLoad>`, `MovieDetailViewModel.retry()` ; `SeriesDetailViewModel.load: StateFlow<DetailLoad>`, `SeriesDetailViewModel.episodesError: StateFlow<SyncErrorKind?>`, `SeriesDetailViewModel.retryEpisodes()`.
  - `@Composable fun DetailStatePanel(state: DetailLoad, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)` — TV et toucher ; `Loading` = silhouette (barre de titre, ligne d'infos, deux boutons en `Skeleton`, aucune animation) ; `NotFound` = titre `D.detailNotFound` + Retour ; `Failed(kind)` = `S.sync.messageFor(kind)` + Réessayer + Retour ; focus initial (TV) sur la première action, une fois, arrêté par toute touche. `testTag` `"dt-state"`, `"dt-state-retry"`, `"dt-state-back"`.
  - `val DesignStrings.detailNotFound` (`dt_not_found` : en « This title is no longer available », fr « Ce titre n'est plus disponible », es « Este título ya no está disponible », ar « هذا العنوان لم يعد متاحًا ») ; `val DesignStrings.episodesLoadError` (`dt_episodes_error` : en « Episodes could not be loaded », fr « Impossible de charger les épisodes », es « No se pudieron cargar los episodios », ar « تعذّر تحميل الحلقات »).

Erreurs possibles des fiches, ce que voit l'utilisateur, ce qui est journalisé (écran `movie` ou `series`) :

| Cas | À l'écran | Journal |
|---|---|---|
| Titre absent du catalogue (`movieById` / `seriesById` nul après chargement) | `NotFound` : message + Retour | `UtvLog.w("ui.<écran>", "introuvable id=<identifiant interne Room>")` (jamais l'adresse ni l'identifiant de la source) |
| Exception à la lecture locale (Room) | `Failed(OTHER)` + Réessayer + Retour | `UiLog.failure(écran, "chargement", t)` |
| Détails de la source (`vodInfo`) en échec : réseau, délai, identifiants, fournisseur bloqué, réponse illisible | fiche affichée avec les données du catalogue (dégradé silencieux pour l'utilisateur, comme aujourd'hui) | `UiLog.failure(écran, "details-source", t)` |
| Enrichissement TMDB / notes / Fanart / TVmaze en échec | fiche sans ces compléments (inchangé) | une ligne par source et par visite (`"tmdb"`, `"notes"`, `"logo"`, `"tvmaze"`) |
| Épisodes non chargés (`loadEpisodes` en échec) et aucun épisode en base | colonne épisodes : `D.episodesLoadError`, `S.sync.messageFor(kind)`, Réessayer (`retryEpisodes`) | `UiLog.failure("series", "episodes", t)` |
| Épisodes en échec mais déjà en base | épisodes affichés (inchangé) | même ligne de journal |
| Image de fond / affiche / vignette introuvable | aplat ou affiche de repli (inchangé) | synthèse par visite (`ImageFailureTally`) |
| Bande-annonce introuvable | bouton absent (inchangé) | rien (pas une erreur) |
| Lecture impossible | gérée par le lecteur (`PlayErrorText`, lots 4 à 6) | — |
| Contenu verrouillé | tâche 3 | — |

- [ ] **Step 1: Tests qui échouent.** `DetailViewModelErrorsTest` (faux dépôts, `UiLog.sink`) : `film_introuvable_etatNotFound` ; `film_exceptionRoom_failedEtJournal` ; `film_vodInfoEnEchec_ficheReady_uneLigneDetailsSource` ; `serie_episodesEnErreur_causeEtReessayer_journalMasque` (exception portant `http://h:8080/series/jean/s3cret/1.mkv` : `episodesError == UNREACHABLE`, ligne `ui.series` sans « jean » ni « s3cret » ; `retryEpisodes()` relance `loadEpisodes` et remet `episodesError` à null au succès) ; `annulation_jamaisJournalisee` ; `titreDEpisodePersiste_chiffresLatins_sousLocaleArabe` (`Locale.setDefault(ar-EG)`, `playEpisode` produit « S02E05 »). `DetailStatesTest` : `film_introuvable_messageEtRetour` (TV : focus sur Retour, OK → `onBack`) ; `erreur_reessayer_puisRetour_ordreDuFocus` ; `chargement_aucunTexteNiFocusPerdu` ; `etats_tactile_police13_rienDeRogne` ; `etats_arabe_miroir`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.** Chaque `runCatching { … }.getOrNull()` / `getOrDefault` des deux ViewModels devient `uiAttempt(écran, action) { … }.getOrNull()` (même valeur de repli, une trace en plus) ; `SeriesViewModel.playEpisode` formate le marqueur avec `Locale.ROOT`. Les écrans remplacent leur `Text(S.detailLoading)` par `DetailStatePanel(load, …)` tant que l'état n'est pas `Ready`. Extraire de `MovieDetailScreen` la fonction qui enveloppe `onPlay` dans l'avertissement de téléchargement (`internal fun withDownloadWarning(...)`) pour la tester.
- [ ] **Step 4: Lancer** `--tests '*Detail*' --tests '*SeriesDetail*' --tests '*UiLog*' --tests '*I18n*'` → passe.
- [ ] **Step 5: Commit** — `Fiches : titre introuvable, épisodes non chargés et erreurs affichés avec leur cause, journal sans identifiant`.

### Task 3: Contenu verrouillé par le contrôle parental (point à trancher n° 3)

**Files:**
- Modify: `K/ui/detail/DetailLogic.kt`, `K/data/repo/CatalogRepository.kt` (une lecture), `K/ui/movies/MovieDetailScreen.kt`, `K/ui/series/SeriesViewModel.kt`, `K/ui/series/SeriesDetailScreen.kt`, `K/ui/detail/DetailStates.kt`
- Modify: `R/values{,-fr,-es,-ar}/strings.xml`, `K/i18n/DesignStringsB1.kt`, golden
- Test: `T/ui/detail/DetailLogicTest.kt`, `T/ui/detail/DetailStatesTest.kt`

**Interfaces:**
- Consumes: `CategoryEntity.locked` (`K/data/db/Entities.kt:120`), `categoryDao.forProviderKind(pid, kind)` ; `ProfileRepository.hiddenCategoryKeys` (clés `"<kind>:<providerId>:<remoteId>"`, `KidsRules.adultCategoryKeys`) ; `MovieEntity.categoryId`, `SeriesEntity.categoryId` ; `PinPromptDialog(title, onUnlocked, onCancel)` (`K/ui/parental/PinPromptDialog.kt`, qui laisse passer sans code s'il n'y a aucun code défini) ; textes existants `S.parentalLockedTitle`, `S.parentalUnlock`.
- Produces:
  - `enum class DetailLock { NONE, PIN, HIDDEN }` et `fun detailLockOf(kind: String, providerId: Long, categoryId: String?, lockedCategoryIds: Set<String>, profileHiddenKeys: Set<String>, unlocked: Boolean): DetailLock` (pur) — `HIDDEN` si `"$kind:$providerId:$categoryId"` est dans `profileHiddenKeys` ; sinon `PIN` si `categoryId in lockedCategoryIds && !unlocked` ; sinon `NONE` ; `categoryId` nul → `NONE`.
  - `suspend fun CatalogRepository.lockedCategoryIds(pid: Long, kind: String): Set<String>`.
  - `MovieDetailViewModel.lock: StateFlow<DetailLock>` / `unlock()` ; idem `SeriesDetailViewModel`. Déverrouillage gardé en mémoire du ViewModel (la visite), jamais persisté.
  - `@Composable fun LockedDetailPanel(lock: DetailLock, title: String, onUnlock: () -> Unit, onBack: () -> Unit)` ; `val DesignStrings.detailHiddenForProfile` (`dt_hidden_profile` : en « Not available for this profile », fr « Non disponible pour ce profil », es « No disponible para este perfil », ar « غير متاح لهذا الملف الشخصي »).

Règles : tant que `lock != NONE`, aucun fond, aucune affiche, aucun synopsis ni bande-annonce n'est chargé ou affiché ; le panneau montre le titre texte, `S.parentalLockedTitle`, « Déverrouiller » (PIN) ou seulement Retour (HIDDEN). Code accepté → `unlock()` → fiche normale, focus sur le principal. Code annulé → le panneau reste, focus sur « Déverrouiller ». Aucune journalisation (ce n'est pas une erreur).

- [ ] **Step 1: Tests qui échouent** : `DetailLogicTest.verrou_reglesPures` (catégorie nulle, verrouillée, verrouillée + déverrouillée, masquée par le profil prioritaire sur le verrou) ; `DetailStatesTest.verrouille_aucuneImage_codePuisFiche` (aucun nœud `"dt-backdrop"` tant que verrouillé ; `onUnlock` appelé ; après déverrouillage `"dt-primary"` focalisé) ; `masqueProfil_retourSeulement` ; `verrouille_tactile_et_arabe`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter** (lecture des catégories hors fil principal, une fois par chargement).
- [ ] **Step 4: Lancer** `--tests '*Detail*' --tests '*Parental*' --tests '*Kids*'` → passe.
- [ ] **Step 5: Commit** — `Fiches : contenu d'une catégorie verrouillée protégé par le code parental`.

### Task 4: Fond plein écran commun et actions posées sur l'image

**Files:**
- Modify: `K/ui/detail/DetailParts.kt` (`DetailBackdrop` → `DetailBackdropLayer`), `K/ui/design/Streaming.kt` (`ImageInk`), `K/ui/movies/MovieDetailScreen.kt` (suppression de `DetailVisual` et `BlurredFill` une fois sans appelant), `K/ui/detail/DetailV2.kt` (`V2CastRow` : encres en paramètres)
- Modify: `K/ui/downloads/DownloadActions.kt` (`MovieDownloadPill`), `K/ui/trakt/TraktActionsUi.kt` (`TraktMovieActions`, `TraktSeriesButton`) : paramètre facultatif `onImage`
- Test: Create `T/ui/detail/DetailBackdropTest.kt`

**Interfaces:**
- Consumes: `Modifier.imageScrims(start, bottom, rtl, pageColor, bottomSpan)` ; `OnImageButton`, `OnImageIconButton` ; `FocusSurface(ringColor)` (lot 2) ; `SlotImage(onFail)` via `ImageFailureTally`.
- Produces:
  - `object ImageInk { val Primary = Color(0xFFF4F5F7); val Secondary = Color(0xFFE3E6EB); val Tertiary = Color(0xFFC9CED6); val Quiet = Color(0xFFA9AFBA); val Outline = Color(0xFF8A919D) }` (exception « sur image » de la spec, valeurs des maquettes).
  - `enum class DetailScrim { MOVIE, SERIES }` et `@Composable fun DetailBackdropLayer(backdrop: String?, poster: String?, title: String, scrim: DetailScrim, decodeSize: DpSize?, modifier: Modifier = Modifier)` — fond `#0A0C10` ; UNE image décodée à `decodeSize` (TV : `null` → 1280 × 720 actuels) ; détection du faux fond portrait conservée (affiche 2:3 nette dans une zone fixe à la fin) ; `MOVIE` : `imageScrims(start = true, bottom = true, rtl, pageColor = Color(0xFF0A0C10), bottomSpan = 0.43f)` (460 / 1080 de la maquette) ; `SERIES` : voile uniforme `#0A0C10` à 72 % (maquette), dessiné une fois ; `testTag` `"dt-backdrop"`.
  - `MovieDownloadPill(movie, vm, onImage: Boolean = false)`, `TraktMovieActions(movie, vm, onImage: Boolean = false)`, `TraktSeriesButton(series, episodes, season, touch, vm, onImage: Boolean = false)` — `onImage = true` dessine des `OnImageButton` / `OnImageIconButton` au lieu des `PillButton` ; `false` = inchangé.
  - `V2CastRow(..., ink: Color = Ux.Text, ink2: Color = Ux.Text3, focusedBg: Color = Ux.Button)`.

- [ ] **Step 1: Tests qui échouent** : `fondPaysage_uneSeuleImage_tailleDemandee` (le chargeur factice reçoit UNE requête de 1280 × 720 sur TV, de la taille affichée au toucher) ; `fondPortrait_afficheDansSaZone` ; `sansImage_themeClair_texteLisible` (thème LIGHT, aucune image : pixel sous le titre plus sombre que `#303030`, texte `ImageInk.Primary`) ; `voileSerie_uniforme72` ; `actionsSurImage_ongletsTrakt_et_telechargement` (avec `onImage`, boutons blancs ; sans, `PillButton` comme avant) ; `aucunFlou` (aucun nœud n'utilise `blur`, vérifié par l'absence de `BlurredFill` : `grep` dans l'étape 4).
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.** Aucune allocation par recomposition (requête Coil mémorisée par `(url, taille)`, voile dans `drawWithCache`).
- [ ] **Step 4: Lancer** `--tests '*DetailBackdropTest*' --tests '*Detail*' --tests '*Download*' --tests '*Trakt*'` → passe ; `grep -rn "BlurredFill\|blur(" K/ui/movies K/ui/series K/ui/detail K/ui/mobile` → aucun résultat.
- [ ] **Step 5: Commit** — `Fiches : fond plein écran voilé commun, actions posées sur l'image, plus de flou`.

### Task 5: Fiche film TV (maquette FicheFilm)

**Files:**
- Modify: `K/ui/detail/DetailV2.kt` (`MovieDetailTv`, `MovieDetailTvModel`), `K/ui/movies/MovieDetailScreen.kt` (construction du modèle, similaires, distribution)
- Modify: `K/ui/home/RowFocusMemory.kt` (généralisation de `heroFocus`, sans changement de comportement)
- Modify: `R/values{,-fr,-es,-ar}/strings.xml`, `K/i18n/DesignStringsB1.kt`, golden
- Test: `T/ui/detail/MovieDetailTvFocusTest.kt`, `T/ui/detail/DetailRenderTest.kt`

**Interfaces:**
- Consumes: `DetailBackdropLayer(…, DetailScrim.MOVIE, null)`, `ImageInk`, `OnImageButton`, `OnImageIconButton`, `MovieDownloadPill(onImage = true)`, `TraktMovieActions(onImage = true)` (tâche 4) ; `DetailLoad`, `DetailLock` (tâches 2, 3) ; `HomeFocusRestore` + `HomeFocusRestore.Saver` + `LocalHomeFocusRestore`, `rememberRowFocusMemory()`, `rowMemory(memory, key)`, `RegisterRowKeys(memory, data, listState, keys)`, `LocalHomeRowId` ; `PosterCell(item, modifier, kind, imageWidth, focusedScale)` avec `HomePosterScale` ; `RowEdgeMargin(edgeMarginFor(HomePosterScale, 200.design, PosterRingReach))`.
- Produces:
  - `internal fun restorableFocus(row: String, key: String): Modifier` dans `RowFocusMemory.kt` ; `heroFocus(key)` devient `restorableFocus("HERO", key)` (suite `HomeTv*` verte sans modification).
  - `MovieDetailTvModel(..., metaRating: String?, badges: List<String>, castLine: String?, genresLine: String?)` (champs ajoutés ; `chips` / `ratings` restent tant qu'un appelant s'en sert, puis sont retirés dans cette tâche).
  - Textes : `val DesignStrings.withLabel` (`dt_with` : en « With: », fr « Avec : », es « Con: », ar « بطولة: ») ; `val DesignStrings.genresLabel` (`dt_genres` : en « Genres: », fr « Genres : », es « Géneros: », ar « الأنواع: »).
  - `testTag` de la tâche 1 conservés.

Valeurs (maquette, unités `design`) : retour rond 60 à 72 / 52, fond blanc 16 %, chevron 26 `ImageInk.Primary` (`OnImageIconButton`) ; bloc à 72 du bord, haut 170, largeur 900, écart 20 ; titre Sora ExtraBold 104 / 104, `letterSpacing` −2, deux lignes au plus (logo Fanart 900 × 220 au plus) ; ligne d'infos 24 SemiBold `ImageInk.Tertiary` : note Rotten Tomatoes en ExtraBold `ImageInk.Primary`, année · durée · badges qualité et langue (contour 2 `ImageInk.Outline`, rayon 6, texte 20) · « ★ 7,6 IMDb » (nombres par `DisplayLocale`) ; synopsis 26 / 38 `ImageInk.Secondary`, trois lignes ; « Avec : » + jusqu'à 4 noms, « Genres : » + genres, 22, libellé Bold `ImageInk.Tertiary`, noms `ImageInk.Quiet`, une ligne chacune ; reprise : barre 620 × 6, piste blanche 22 %, remplissage `Ux.Accent`, texte 20 `ImageInk.Tertiary` « Reprendre à 31:12 · 94 min restantes » (inchangé : `D.resumeAt`, `D.remainingMin`) ; boutons hauteur 76, écart 16 : principal `OnImageButton(primary = true, iconPath = Icons.Play)` « Reprendre » / « Regarder », « Depuis le début » (si commencé), « Bande-annonce » (si clé), « Ma liste » rond 76 (`OnImageIconButton`, `+` / `✓`, état lu), téléchargement rond 76, puis Trakt (`onImage`) sur la ligne suivante. « Titres similaires » à 842 du haut (la rangée dépasse du bas) : titre Sora Bold 30 `ImageInk.Primary`, affiches 200 × 300, écart 20, agrandissement 10 %, anneau du thème, mémoire de rangée. Puis « Distribution » (point n° 2) : `V2CastRow(ink = ImageInk.Primary, ink2 = ImageInk.Quiet, focusedBg = Color.White.copy(alpha = 0.16f))`.

Focus : première visite → « Reprendre » / « Regarder », une seule fois, arrêtée par une touche ; tout élément focalisé est mémorisé dans un `HomeFocusRestore` sauvegardé avec l'entrée de navigation (`"ACTIONS\u0000<clé>"`, `"SIMILAR\u0000<id>"`, `"CAST\u0000<nom>"`) : retour du lecteur, d'une page de personne ou d'une autre fiche → l'élément quitté (la rangée des similaires défile jusqu'à lui par `RegisterRowKeys`), sans focus de passage. ▼ depuis les actions → similaires ; ▼ depuis les similaires → distribution ; ▲ inverse ; la fiche défile pour garder l'élément focalisé entièrement visible ; ▲ depuis les actions → retour rond.

- [ ] **Step 1: Tests qui échouent** (`MovieDetailTvFocusTest`, français puis arabe pour 3) :
  1. `filmTv_focusInitialSurLePrincipal_uneFois` ; `filmTv_toucheAvantLArriveeDesDetails_focusNeBougePas`.
  2. `filmTv_descente_actionsSimilairesDistribution_puisRemontee`.
  3. `filmTv_lignes_droiteGauche_bouts_etArabe`.
  4. `filmTv_retourDuLecteur_surLaSeptiemeAfficheSimilaire` (focus sur `dt-similar-7`, sortie / retour de composition avec état sauvegardé : `dt-similar-7` focalisé, visible, aucun autre élément focalisé entre-temps).
  5. `filmTv_retourDUnePersonne_surLAvatar`.
  6. `filmTv_donneesQuiArrivent_focusNeBougePas` (TMDB fusionne titre, logo, notes après le focus : même nœud focalisé).
  7. `filmTv_titreTresLong_boutonsVisibles_police13`.
  8. `filmTv_sansSimilairesNiDistribution_basNeFaitRien`.
  `DetailRenderTest` : captures `movie-detail-tv-fr.png`, `-light`, `-ar`, `-en` mises à jour.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer** `--tests '*MovieDetail*' --tests '*DetailRender*' --tests '*HomeTv*'` → passe ; regarder les captures (texte lisible sur l'image dans les quatre, voile à droite en arabe, rien de rogné).
- [ ] **Step 5: Commit** — `Fiche film TV : image plein écran, reprise et actions nommées, similaires et distribution`.

### Task 6: Fiche série TV (maquette FicheSerie), mémoire de l'épisode et compteur de recompositions

**Files:**
- Modify: `K/ui/detail/SeriesDetailTv.kt` (`SeriesDetailTvContent`, `SeasonTabs` → `SeasonList`, `EpisodeCard`), `K/ui/series/SeriesDetailScreen.kt` (saison choisie sauvegardée)
- Create: `K/ui/detail/DetailProbe.kt`
- Test: `T/ui/detail/SeriesDetailFocusTest.kt`, Create `T/ui/detail/SeriesDetailRecompositionTest.kt`, `T/ui/detail/DetailRenderTest.kt`

**Interfaces:**
- Consumes: tâches 2 à 5 (`DetailBackdropLayer(…, DetailScrim.SERIES, null)`, `ImageInk`, `restorableFocus`, `HomeFocusRestore`, `FocusSurface(ringColor = Color.White)`) ; `entryIndexOf(shown, target)`, `activeSeasonOf`, `seasonsOf` (`DetailLogic.kt`) ; `D.seasonLabel(n)`, `D.episodesCount(n)`, `D.resumeEpisode`, `D.playEpisodeLabel`, `D.longPressDownload`, `D.remainingMin`, `D.watchedLabel`.
- Produces:
  - `@Immutable` sur `SeriesDetailData` (toutes ses listes sont immuables par construction) ; `SeriesDetailTvContent(..., restore: HomeFocusRestore, ...)`.
  - `internal class DetailProbe { val episodes = HashMap<String, Int>(); val similar = HashMap<Long, Int>(); var header = 0 }`, `LocalDetailProbe`, `ProbeEpisode(key)`, `ProbeSimilar(id)`, `ProbeHeader()` — schéma de `K/ui/guide/GuideProbe.kt`.
  - `SeriesDetailScreen` : `var season by rememberSaveable(series.id) { mutableStateOf<Int?>(null) }` (aujourd'hui `remember` : la saison choisie se perdait au retour du lecteur).
  - Texte : `val DesignStrings.seasonsHeader` (`dt_seasons_header` : en « Seasons », fr « Saisons », es « Temporadas », ar « المواسم » ; mis en capitales à l'affichage sauf en arabe).

Valeurs (maquette) : fond + voile uniforme 72 % ; retour rond 60 à 72 / 52. Colonne gauche à 72, haut 150, largeur 620, écart 18, paresseuse (elle défile si 30 saisons) : titre Sora ExtraBold 72 / 76, `letterSpacing` −1, deux lignes (logo 620 × 170) ; infos 22 SemiBold `ImageInk.Tertiary` (`buildInfoLine` inchangé + IMDb, RT, langue) ; ligne TVmaze (`episodeLine`) 20 Bold `ImageInk.Primary` ; synopsis 21 / 30, deux lignes ; « Reprendre S2 É4 » / « Regarder S1 É1 » `OnImageButton(primary)` 76, pad 36, texte 26 ; ligne de boutons 64 : « Ma liste », « Bande-annonce », « Télécharger la saison », Trakt (`onImage`) ; « SAISONS » 20 Bold, espacement 2, `ImageInk.Quiet` ; une ligne par saison, hauteur 64 (`designT`), rayon 16, texte 26 « Saison 2 · 8 épisodes » : choisie = fond blanc 14 %, barre `Ux.Accent` 6 × 28, ExtraBold `ImageInk.Primary`, `selected` ; autres = transparent, SemiBold `ImageInk.Tertiary` ; au focus fond blanc, texte `#0A0C10` ; distribution (avatars 80) en dernier élément de la colonne. Colonne droite à 780, haut 150, largeur 1068, écart 16 : indication d'appui long 17 `ImageInk.Quiet` puis épisodes : ligne padding 16, rayon 20, fond blanc 6 % (focus : blanc 16 % + anneau blanc 5, `ringColor = Color.White`, `focusedScale = 1f`) ; vignette 284 × 160 rayon 12 (`decodeSize` 284 × 160), numéro Sora ExtraBold 44 blanc 90 % à 14 / 10, progression 6 au bas ; titre Sora Bold 28 + durée 20 `ImageInk.Tertiary` ; résumé 22 / 30 `ImageInk.Tertiary` deux lignes ; état 20 Bold `ImageInk.Primary` (« Vu », « Reste 32 min »). Saison choisie par focus après 140 ms (comportement actuel) ou par OK.

Focus :
- Première visite : focus sur l'ÉPISODE À REPRENDRE dans la liste (maquette, demande du propriétaire), la liste ayant été amenée à lui par `requestScrollToItem` (aucune demande de focus dans l'effet). Tant que les épisodes ne sont pas là, focus sur « Reprendre » ; s'ils arrivent sans qu'aucune touche n'ait été pressée, le focus passe à l'épisode (règle de c7da3d10) ; après une touche, il ne bouge plus.
- Retour du lecteur : saison et épisode QUITTÉS (`"EPISODES\u0000<s>:<e>:<remoteId>"` dans le `HomeFocusRestore` sauvegardé), même si la reprise vise désormais l'épisode suivant ; épisode disparu → l'épisode d'entrée de la saison, sans trou de focus (`RegisterRowKeys` déclare les clés de la liste).
- → depuis la colonne gauche (bout de ligne ou distribution) → épisode d'entrée ; ◀ depuis un épisode → la saison choisie (point n° 5) ; ▲ ▼ dans la liste de saisons et d'épisodes ; ▲ depuis la première saison → ligne de boutons ; ◀ depuis une saison → recherche habituelle.

Tests existants de `SeriesDetailFocusTest` : gardés tels quels `droiteDepuisReprendreVaALEpisodeAReprendre`, `ligneBoutonsGardeDroiteDansLaLignePuisEntreDansLesEpisodes`, `distributionEtDerniereEntreeVersLEpisode`, `dixSaisonsToutesAtteignables` (adapté à la liste verticale : ▼ au lieu de ▶) ; remplacés : `gaucheDepuisUnEpisodeRevientSurReprendre` → `gaucheDepuisUnEpisode_vaSurLaSaisonChoisie` ; `ordreVerticalEpisodeBarreOnglet` et `basDepuisLaBarreVaAuPremierEpisode` → `ordreVertical_colonneGauche_puisSaisons` ; `saison4AtteignableEtGaucheDepuisLaPremiereVersLaColonneGauche` → `saisons_basHaut_etHautDepuisLaPremiere_versLesBoutons` ; `sansReprendreEntreSurLePremierEpisodeEtGaucheSurLePremierBouton` → `sansHistorique_focusInitialSurLePremierEpisode`.

- [ ] **Step 1: Tests qui échouent** : les remplacements ci-dessus, puis `focusInitial_surLEpisodeAReprendre_visible` ; `episodesEnRetard_sansTouche_focusVaSurLEpisode` ; `episodesEnRetard_apresUneTouche_focusNeBougePas` ; `retourDuLecteur_saisonEtEpisodeQuittes` (Review Focus n° 3) ; `retourDuLecteur_episodeDisparu_entreeSansTrou` ; `uneSaisonUnEpisode` ; `trenteSaisons_toutesAtteignables` ; `aucunEpisode_basEtDroiteNeFontRien` ; `arabe_directionsInversees`. Recompositions (`SeriesDetailRecompositionTest`) :

```kotlin
@Test fun basDansLesEpisodes_deuxCartesAuPlus_enTeteIntact() { /* focus dt-episode-2-3, ▼ : probe.episodes.keys ⊆ {2-3, 2-4} ; probe.header == 0 */ }
@Test fun progressionRecue_seulementLEpisodeConcerne() { /* data.progress change pour 2-3 seul : probe.episodes.keys == {2-3} */ }
@Test fun survolDesSaisons_neRecomposePasLaColonneGauche() { /* ▼ sur la liste des saisons : probe.header == 0 */ }
```

  Même compteur pour les similaires de la fiche film : `similaires_droite_deuxAffichesAuPlus` (dans `SeriesDetailRecompositionTest` ou un fichier `MovieDetailRecompositionTest`).
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.** Une seule demande de focus initiale ; plus de boucle `repeat(10) { requestFocus }` sans trace : `focusEntry()` garde son attente bornée mais journalise un échec final par `UiLog` (action `"focus-episode"`).
- [ ] **Step 4: Lancer** `--tests '*SeriesDetail*' --tests '*DetailRender*' --tests '*DetailLogic*'` → passe ; regarder `series-detail-tv-*.png`.
- [ ] **Step 5: Commit** — `Fiche série TV : image voilée, saisons à gauche, focus sur l'épisode à reprendre et retour sur l'épisode quitté`.

### Task 7: Fiche film au toucher (téléphone et tablette)

**Files:**
- Create: `K/ui/mobile/MobileDetail.kt` (y déplacer `MobileDetailFrame`, `MobileBackButton`, `MobileActionTile`, `MobilePrimaryButton`, `MobileSecondaryButton`, `MetaRow`, `ExpandableText` depuis `MobileHome.kt`, signatures inchangées pour leurs autres appelants : `TraktActionsUi.kt`, `DownloadActions.kt`)
- Modify: `K/ui/movies/MovieDetailScreen.kt` (branche tactile)
- Test: Create `T/ui/detail/MovieDetailTouchTest.kt`, Create `T/ui/detail/DetailTouchLayoutTest.kt`

**Interfaces:**
- Consumes: `DetailBackdropLayer`, `ImageInk`, `OnImageButton` (anneau blanc au clavier, lot 2), `DetailStatePanel`, `LockedDetailPanel`, `TraktMovieActionsMobile`, `MovieDownloadTile`, `PosterCell`, `AvatarImage`, `D.withLabel`, `D.genresLabel`, `resume` (`R.string.common_resume`), `MS.fromStart`, `D.heroMyList`.
- Produces:
  - `enum class DetailTouchLayout { PORTRAIT, LANDSCAPE, TWO_PANE }` et `fun detailTouchLayout(contentWidthDp: Float, windowHeightDp: Float): DetailTouchLayout` (pur) : `TWO_PANE` si largeur ≥ 840 (`EXPANDED_MIN_DP`) ; sinon `PORTRAIT` si largeur < 600 et hauteur ≥ largeur ; sinon `LANDSCAPE`.
  - `@Composable fun MobileDetailScaffold(layout: DetailTouchLayout, backdrop: String?, poster: String?, title: String, onBack: () -> Unit, overImage: @Composable ColumnScope.() -> Unit, body: LazyListScope.() -> Unit, side: (LazyListScope.() -> Unit)? = null)` — remplace `MobileDetailFrame` pour les deux fiches ; contenu paresseux (`LazyColumn`, clés stables).

Mise en page retenue (non maquettée ; déduite de `FicheFilm` et de `Mobile.dc.html`, valeurs en dp) :
- `PORTRAIT` : image en tête, pleine largeur, hauteur `clamp(fenêtre × 0.6, 320, 480)`, `DetailBackdropLayer(…, MOVIE)` décodée à cette taille (affiche si aucun fond) ; voile bas sombre `bottomSpan = 0.6` ; bouton Retour 48 en haut au début, sous la barre d'état. Sur l'image, en bas, centré, à 16 des bords, écart 10 (comme l'image à la une de `Mobile.dc.html`) : titre Sora ExtraBold 30 / 33, trois lignes ; ligne d'infos 14 SemiBold `ImageInk.Secondary` (note RT · année · durée · qualité · langue · ★ IMDb) ; deux boutons 48 de poids égal : principal (point n° 4 : « Reprendre » si commencé, sinon « Lecture ») et « Ma liste ». Sous l'image, sur la page (jetons du thème), marges 16, écart 14 : progression 4 + « Reprendre à … · … min restantes » 13 `Ux.Text3` ; tuiles 64 : « Depuis le début » (si commencé), « Bande-annonce » (si clé), téléchargement ; tuiles Trakt ; synopsis repliable ; « Avec : … » et « Genres : … » 14 ; « Distribution » (avatars 52) ; « Titres similaires » (affiches 110 × 165, NOUVEAU au toucher : les données existent déjà dans le ViewModel).
- `LANDSCAPE` (téléphone couché, tablette en portrait) : image FIXE plein écran derrière, voiles début + bas sombres ; colonne paresseuse au début, largeur `min(560, 62 % de la largeur)`, marge 24, tout posé sur l'image (encres `ImageInk`, boutons blancs ; les tuiles gardent leur propre surface) ; titre aligné au début, 28 (36 si largeur ≥ 600) ; boutons principal + « Ma liste » côte à côte, largeur naturelle, 160 au moins ; même ordre de blocs que `PORTRAIT`.
- `TWO_PANE` (≥ 840) : comme `LANDSCAPE`, colonne de 640 au plus ; similaires et distribution dans le volet de droite (`side`).

Clavier / manette : aucun focus initial au toucher ; Tab et flèches suivent l'ordre ci-dessus ; anneau du thème sur la page, blanc sur l'image ; mémoire de rangée sur les similaires. Retour du lecteur : même position de défilement (état saveable), même élément si le clavier était utilisé (`HomeFocusRestore` comme sur TV).

- [ ] **Step 1: Tests qui échouent.** `DetailTouchLayoutTest` : `(390, 844) → PORTRAIT`, `(320, 568) → PORTRAIT`, `(844, 390) → LANDSCAPE`, `(800, 1280) → LANDSCAPE`, `(1280, 800) → TWO_PANE`, `(0, 0)` sans exception. `MovieDetailTouchTest` : `principal_reprendGardeLaPosition_depuisLeDebutRepart` (point n° 4 : principal → `vm.play`, « Depuis le début » → `restart`) ; `pasCommence_principalLecture_sansDepuisLeDebut` ; `portrait_police13_titreEtBoutonsDansLImage` (320 × 568, titre 120 caractères) ; `paysage_colonneAuDebut_arabeADroite` ; `similaires_presents_auToucher` ; `clavier_parcoursEtAnneauBlancSurLImage` ; `rotation_gardeLaPosition` (`StateRestorationTester`) ; `etats_introuvable_erreur_verrou_auToucher`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter** (images à la taille affichée ; aucune lecture d'état en haut de l'écran pour les notes et le logo : lues par leurs seules feuilles).
- [ ] **Step 4: Lancer** `--tests '*MovieDetailTouch*' --tests '*DetailTouch*' --tests '*Trakt*' --tests '*Download*'` → passe.
- [ ] **Step 5: Commit** — `Fiche film au toucher : image en tête, reprise d'abord, similaires, mise en page paysage et tablette`.

### Task 8: Fiche série au toucher (téléphone et tablette)

**Files:**
- Modify: `K/ui/series/SeriesDetailScreen.kt` (branche tactile, `EpisodeRow`)
- Test: Create `T/ui/detail/SeriesDetailTouchTest.kt`

**Interfaces:**
- Consumes: `MobileDetailScaffold`, `detailTouchLayout` (tâche 7) ; `DetailBackdropLayer(…, SERIES, …)` ; `EpisodeDownloadButton`, `TraktSeriesButton(touch = true)` ; `D.episodesLoadError`, `retryEpisodes` (tâche 2) ; saison saveable (tâche 6).
- Produces: `testTag` `"dt-season-<n>"`, `"dt-episode-<s>-<e>"`, `"dt-episode-dl-<s>-<e>"`, `"dt-episodes-error"`.

Mise en page : même cadre que la fiche film (`PORTRAIT` : image en tête, titre, infos « 2022 · 3 saisons · Drame · ★ 8,1 », principal « Reprendre S2 É4 » et « Ma liste » sur l'image ; `LANDSCAPE` / `TWO_PANE` : image fixe, colonne au début), puis sur la page : tuiles Bande-annonce et Trakt, synopsis, ligne TVmaze, « Saisons » (pastilles 40, `LazyRow`, saison choisie en couleurs de focus — actuel), « Télécharger la saison », indication d'appui long, épisodes (vignette 120 × 68 avec progression 4, « É4 · Titre » 15 Bold, durée · état 12, résumé deux lignes 13, bouton de téléchargement 44 au bout). `TWO_PANE` : saisons et épisodes dans le volet de droite. Épisodes non chargés : `D.episodesLoadError` + cause + Réessayer à la place de la liste.

Comportements : première visite → la liste est amenée à l'épisode à reprendre (une fois par visite, drapeau `rememberSaveable`, `requestScrollToItem`, aucune demande de focus au toucher) ; retour du lecteur → même saison (saveable) et même position ; changement de saison → la liste montre l'épisode d'entrée de la nouvelle saison ; clavier : ▲ ▼ dans les épisodes, → vers le bouton de téléchargement de la ligne, ← retour sur la ligne.

- [ ] **Step 1: Tests qui échouent** : `premiereVisite_listeSurLEpisodeAReprendre` ; `retourDuLecteur_memeSaisonMemePosition` (saison 3 choisie alors que la reprise vise la 2 : après restauration, la saison 3 est affichée) ; `changementDeSaison_episodeDEntree` ; `episodesEnErreur_reessayer` ; `uneSaisonUnEpisode_aucunPlantage` ; `trenteSaisons_pastillesAtteignablesAuClavier` ; `police13_lignesDEpisode_rienDeRogne` ; `arabe_miroir_chiffresLatins` ; `deuxVolets_saisonsEtEpisodesADroite`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer** `--tests '*SeriesDetail*' --tests '*DetailTouch*'` → passe.
- [ ] **Step 5: Commit** — `Fiche série au toucher : même langage que la TV, saison et position gardées au retour du lecteur`.

### Task 9: Dix thèmes, captures, CHANGELOG, suite complète, contrôle sur appareil

**Files:**
- Modify: `T/ui/detail/DetailRenderTest.kt` ; Create `T/ui/detail/DetailThemeContrastTest.kt`
- Modify: `CHANGELOG.md` (« Non publié »), `README.md`, `README.fr.md` (si les fiches y sont décrites), `docs/screenshots/{fr,en,ar}/*detail*.png`
- Vérifier : `android-native/app/src/main/baseline-prof.txt`

- [ ] **Step 1: Contrastes** (`DetailThemeContrastTest`, méthode de `T/ui/home/HomeThemeContrastTest.kt`, chaque `AppTheme.entries`, TV et téléphone, système clair et sombre) : `ImageInk.Primary` et `ImageInk.Tertiary` sur `#0A0C10` ≥ 7 et ≥ 4,5 (une fois, indépendant du thème) ; accent (progression, barre de saison, anneau des affiches et avatars) sur `#0A0C10` ≥ 3 ; texte et texte atténué des tuiles sur `card` ≥ 7 et ≥ 4,5 ; `onAccent` sur `accent` ≥ 3.
- [ ] **Step 2: Rendu de tous les thèmes** : fiche film et fiche série, TV, dans CHAQUE thème, focus sur le principal puis sur un épisode / une affiche similaire, `detail-<film|serie>-tv-theme-<thème>-<état>.png` ; fiche film et série téléphone portrait par thème. Captures de documentation : TV fr sombre, clair, ar, en ; téléphone portrait fr / ar ; téléphone paysage ; tablette ; états (introuvable, erreur d'épisodes, verrouillé). Ouvrir CHAQUE capture : texte lisible, rien de rogné, focus évident, voile du bon côté en arabe, chiffres latins. Corriger avant de continuer.
- [ ] **Step 3: Profil de démarrage** : mettre à jour les lignes exactes qui nomment `DetailVisual`, `BlurredFill`, `SeasonTabs`, `MobileDetailFrame` ou d'autres méthodes renommées / supprimées ; ne rien changer s'il n'y a que des règles par paquet.
- [ ] **Step 4: Suite complète** — `./gradlew --offline -q :app:testDebugUnitTest` → 0 échec (aucun accepté) ; seconde passe pour écarter un test instable.
- [ ] **Step 5: CHANGELOG et README** (fr et en) : fiches refaites, épisode retrouvé au retour du lecteur, états d'erreur et de verrou, « Reprendre » d'abord au toucher ; copier les captures. Commit — `Fiches : captures, journal des modifications`.
- [ ] **Step 6: Liste de contrôle sur appareil réel** (jointe à la PR, cochée par le propriétaire) :
  1. Box modeste (Mecool KM7 Plus, Android 11, 2 Go, 32 bits) : ouverture d'une fiche film et série depuis l'accueil, mémoire (`dumpsys meminfo`) avec le fond 1280 × 720, aucune saccade nouvelle (`perf` avant / après), aucun flou.
  2. Fiche série : focus initial sur l'épisode à reprendre ; lecture d'un épisode d'une autre saison, Retour → même saison, même épisode ; 30 saisons (source de test) toutes atteignables ; ◀ ▶ ▲ ▼ depuis chaque bord.
  3. Fiche film : Reprendre / Depuis le début / Bande-annonce (Retour la ferme) / Ma liste / téléchargement / Trakt (notes : Retour ferme la fenêtre seule) ; retour d'une page de personne sur l'avatar.
  4. Titre supprimé de la source (resynchroniser après suppression) : « Ce titre n'est plus disponible ». Source coupée : épisodes en erreur, Réessayer ; la ligne `ui.series` apparaît dans la télémétrie SANS identifiant ni adresse complète.
  5. Catégorie VOD verrouillée : fiche verrouillée, code juste puis faux ; profil Enfants : « Non disponible pour ce profil ».
  6. Téléphone portrait / paysage / fenêtre partagée, tablette avec clavier et manette : parcours, anneaux visibles (blanc sur l'image), rotation sans perte.
  7. Police « Très grande » + texte « Très grand » ; arabe (miroir, chiffres latins, puis rotation : aucun chiffre arabo-indien sur ces écrans).
  8. Les 10 thèmes ; TalkBack (titre, boutons, « Ma liste » avec son état, saison choisie lue « sélectionnée », épisodes avec leur état).
- [ ] **Step 7: PR** vers `main` : ce qui change, les points tranchés, les comportements préservés (tableau de la tâche 1), les tests remplacés de `SeriesDetailFocusTest` et pourquoi, les mesures avant / après, les captures. Pas de fusion sans CI verte ; aucune version publiée sans accord du propriétaire.
