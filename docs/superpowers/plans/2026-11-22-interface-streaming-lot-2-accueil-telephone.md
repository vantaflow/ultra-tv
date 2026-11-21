# Interface façon streaming — lot 2 : accueil téléphone et tablette — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** L'accueil tactile (téléphone et tablette, portrait et paysage, au doigt ou au clavier / à la manette) prend le langage de la maquette `Mobile.dc.html` — en-tête « Pour <profil> », filtres rapides, grande image à la une avec « Lecture » et « Ma liste », rangées En direct, Reprendre, affiches et Top 10 lisible jusqu'au rang 10 — sans perdre une fonction, une préférence ni une image par seconde, et en dessinant enfin ses états d'erreur.

**Architecture:** On remodèle `ui/mobile/MobileHome.kt` en place et on sort ses rangées dans un fichier voisin. Les données ne changent pas : `HomeViewModel` → `HomeRows` (immuable, construite hors fil principal) → `mobileSectionOrder`. Ce qui est partagé avec la TV est extrait, jamais dupliqué : choix de l'encart du haut (`homeTopSlot`), règle de rotation (`heroRotatesOn`), mémoire de rangée (`RowFocusMemory`), grand chiffre du Top 10, voiles (`imageScrims`), boutons sur image. Un petit utilitaire de journal d'erreurs d'écran (`UiLog`) est posé ici et resservira à tous les lots suivants.

**Tech Stack:** Kotlin, Jetpack Compose (foundation, tv-material3), Hilt, Room, Coil 3 ; tests Robolectric + compose-ui-test.

**Spec:** `docs/superpowers/specs/2026-11-21-interface-streaming-design.md` (écran 6 du canevas, section « Périmètre élargi le 2026-11-21 »). Maquette : `Mobile.dc.html` (390 × 844, valeurs en dp). Langage commun : `Main.dc.html`, `AccueilRangees.dc.html`.

Chemins abrégés : `K = android-native/app/src/main/kotlin/com/ultratv/tv/nativeapp`, `T = android-native/app/src/test/kotlin/com/ultratv/tv/nativeapp`, `R = android-native/app/src/main/res`. Commandes lancées depuis `android-native/`. Test ciblé : `./gradlew --offline -q :app:testDebugUnitTest --tests '<motif>'`.

## Points à trancher par le propriétaire

Le plan applique la proposition ; chaque point se défait par un seul commit s'il est refusé.

1. **« Reprendre » en affiches portrait (maquette) au lieu des vignettes larges actuelles.** Proposé : affiche 112 × 168 avec progression, titre et temps restant SOUS l'affiche (la maquette n'en montre pas, on ne les retire pas). « Chaînes regardées récemment » garde la vignette large (un logo de chaîne n'a pas sa place dans un cadre d'affiche). Tâche 6.
2. **En-tête « Pour <profil> » (maquette) à la place du logo « ULTRA TV ».** Proposé : nom du profil ; l'initiale colorée reste le bouton de changement de profil. Tâche 4.
3. **Compteurs des raccourcis** (« 1 240 chaînes ») : la maquette montre des pastilles sans compteur. Proposé : libellé seul à l'écran, compteur conservé dans la description lue par TalkBack. Tâche 4.
4. **Primaire « Lecture » de l'image à la une** : sur un film, lecture directe (comme « Regarder » sur TV) ; sur une série, ouverture de la fiche. Toucher l'image hors des boutons ouvre la fiche (comportement actuel). Tâche 5.
5. **« Nouveaux épisodes » sur téléphone** : la rangée existe sur TV, est cochable dans Réglages, mais n'apparaît jamais au toucher (`MOBILE_SECTIONS`). Proposé : NE PAS l'ajouter dans ce lot (aucune préférence réinterprétée) ; à décider pour un lot ultérieur.

## Global Constraints

Conditions d'acceptation de CHAQUE tâche (exigences du propriétaire, recopiées) :

- « Tous les écrans, toute l'application, sans la moindre erreur, pour tous les appareils » : box et téléviseurs Android TV / Fire TV à la télécommande, téléphones et tablettes au toucher (portrait et paysage, clavier ou manette), du plus modeste (2 Go, 32 bits, Android 7.1) au plus récent.
- Aucune anomalie, aucun plantage : clés uniques dans les listes paresseuses, pas d'index hors bornes sur liste vide ou à un élément, pas de `FocusRequester` non attaché, états vides et d'erreur dessinés.
- Navigation logique et stable (télécommande, clavier, manette) : aucun piège, aucun focus perdu, aucune touche avalée, pas de `LaunchedEffect` qui déplace le focus à retardement ; chaque rangée retient sa carte ; retour d'une fiche ou du lecteur sur l'élément quitté ; ◀ depuis le premier élément vers le menu latéral (TV) ; Retour : d'abord fermer ce qui est ouvert, puis ouvrir le menu latéral sur un écran principal (`K/ui/components/RailBack.kt`, TV seulement — au toucher la barre basse et `MobileNav.kt:188` restent inchangées) ; RTL (arabe) cohérent.
- Les 10 thèmes conservés (DARK, LIGHT, AUTO, OLED, MIDNIGHT, FOREST, PLUM, SAND, CONTRAST, TEAL) : jetons `Ux.*`, aucune couleur en dur ; anneau de focus du thème partout, blanc seulement sur une image ; contrastes vérifiés par test sur tous les thèmes. Exception de la spec : ce qui est posé sur une image (voile `#0A0C10`, texte clair, boutons blancs, `OnImageButton`) est identique dans tous les thèmes.
- Performance « au top » : rien d'ajouté par image ou par recomposition ; un déplacement de focus ne recompose que l'élément quitté, l'élément atteint et ce qui en dépend (verrouillé par le compteur de recompositions de la tâche 6) ; images demandées à la taille affichée (`decodeSize`) ; listes paresseuses avec `key` et `contentType` ; agrandissement par `graphicsLayer` ; voiles par `drawWithCache` ; aucun flou ; image à la une fixe sur appareil modeste seulement selon la règle TACTILE existante (`heroRotatesOn(touch = true, …)` = le niveau d'animation décide, correctif d9986d5a).
- Textes : en, fr, es, ar écrits à la main (`R/values{,-fr,-es,-ar}/strings.xml` + lignes `T/../resources/i18n-golden/{en,fr,es,ar}.json`), les autres langues par Weblate (ne jamais modifier les autres `values-xx`) ; en arabe tous les nombres en chiffres latins (passer par `DisplayLocale` / `DisplayFormats` / `I18n.string` / `tx`, jamais `String.format` ou `"%d".format` sur la locale par défaut) ; aucun texte en dur ; aucun texte tronqué dans une zone de hauteur fixe (`NoTextWrap`, vérifié à la taille de texte 1,3).
- Gestion d'erreur et journaux (spec, puce « Gestion d'erreur et journaux ») : états de chargement, vide et d'erreur dessinés avec un message compréhensible et une action (réessayer, revenir) ; aucun `runCatching { }` / `catch` dont l'échec disparaît sans trace dans les fichiers touchés ; toute erreur attrapée passe par `UiLog` (tâche 2), qui écrit écran, action, catégorie (`NetErrors.classify`) et cause (`UtvLog.why`) ; aucune adresse de flux, aucun identifiant, aucun mot de passe en clair (adresses passées par `UtvLog.maskUrl`, texte par `LogSanitizer` via `UtvLog`) ; `CancellationException` toujours relancée, jamais journalisée.
- Zéro régression : la logique (ViewModel, dépôts, DAO) ne change pas sauf ajout nécessaire à l'affichage ; préférences `home_order` / `home_hidden` jamais réinterprétées (`HomeLayout.parse(tv = false)` inchangé) ; correctifs passés conservés (liste dans la tâche 1).
- Jamais de mention d'une appli concurrente (code, commentaires, commits, CHANGELOG) ; jamais de ligne `Co-Authored-By` ni `Claude-Session` dans les commits.
- `I18nGoldenTest` est vert sous JDK 17 et 27 depuis cb7fb9b0 : tout échec est un vrai échec.
- Le lot se termine par : suite complète verte (aucun échec accepté), captures (sombre, clair, arabe ; téléphone portrait et paysage, tablette), ligne(s) de CHANGELOG, liste de contrôle sur appareil réel (tâche 8).

## Review Focus

1. Petit téléphone (320 × 568 dp, police système « Grande ») : l'image à la une, ses deux boutons et le titre tiennent dans la carte, rien n'est rogné ni recouvert. → test tâche 5 (`aLaUne_petitTelephonePolice13_toutDansLaCarte`).
2. Rotation portrait ↔ paysage, ou passage en fenêtre partagée, au milieu d'une rangée : même rangée à l'écran, même carte en tête, aucun plantage, aucune image redemandée en boucle. → test tâche 7 (`rotation_gardeDefilementEtCarte`).
3. Tablette avec clavier ou manette : le focus est visible sur CHAQUE élément atteint, y compris les boutons blancs posés sur l'image (aujourd'hui `OnImageButton` n'a aucun anneau au toucher). → tests tâches 3 et 7.
4. Rang 10 du Top 10 au toucher et en arabe : « 10 » ne se lit jamais « 1 » (l'affiche ne cache pas plus de 40 % du chiffre). → test tâche 6 (`top10_rangs1a10_lisibles_ltrEtRtl`).
5. Source en erreur ou non prise en charge sur téléphone : aujourd'hui l'accueil tactile n'affiche que « aucune source » ou « chargement » ; il doit dire la cause et proposer Réessayer / Réglages, comme la TV. → test tâche 2 (`sourceEnErreur_messageEtActions`).

---

### Task 1: Filet de non-régression de l'accueil tactile

Aucun changement visuel. On fige ce qui marche aujourd'hui et qu'aucun test ne garde (il n'existe aucun test de rendu de `MobileHomeScreen`).

**Files:**
- Create: `T/ui/mobile/MobileHomeHost.kt` (hôte de test réutilisé par tout le lot)
- Create: `T/ui/mobile/MobileHomeRegressionTest.kt`

**Interfaces:**
- Consumes: `MobileHomeScreen(state, syncPercent, onSearch, onProfile, onGoLive, onGoSettings, onGoGuide, onGoFavorites, onShortcut, onPlay, onPlayHistory, onOpenHero, onOpenMovie, onOpenSeries)` et `MobileHomeState(providersLoaded, hasProviders, hero, rows, profileInitial, profileColor)` tels qu'aujourd'hui ; `HomeTvFixtures.rows(layout)` / `longRows(layout)` (`T/ui/home/HomeTvFixtures.kt`) ; `installFakeImageLoader()` / `resetImageLoader()` (`T/ui/home/FakeImageLoader.kt`).
- Produces: `internal fun ComposeContentTestRule.setMobileHome(width: Int = 390, height: Int = 844, lang: AppLang = AppLang.French, light: Boolean = false, fontScale: Float = 1f, keyboard: Boolean = false, content: @Composable () -> Unit)` — fournit `LocalTouch provides true`, `LocalUiWidthDp`, `LocalUiHeightDp`, `LocalDs`, `LocalStrings`, `LocalMobileStrings`, la direction (RTL si `lang.rtl`), `Density(…, fontScale)`, et `requestInputMode(Keyboard)` si `keyboard`. Tailles de référence du lot : téléphone `390×844`, petit téléphone `320×568`, paysage `844×390`, tablette portrait `800×1280`, tablette paysage `1280×800` (classes Robolectric `@Config(sdk = [34], qualifiers = "w<W>dp-h<H>dp-xxhdpi")`, `application = android.app.Application::class` comme `HomeRenderTest`).

Comportements à préserver (chacun avec son test dans `MobileHomeRegressionTest`, sauf mention) :

| Comportement (et son correctif d'origine) | Test qui le garde |
|---|---|
| Ordre des rangées = ordre TV filtré par `MOBILE_SECTIONS` | `HomeLogicTest.mobileSectionOrder_retireLesRangeesPropresALaTv_ordreConserve` (existant) + `ordreAffiche_suitMobileSectionOrder` |
| Toutes les rangées cochées dans Réglages s'affichent au toucher | `HomeLogicTest.mobile_rangeesCocheesDansLesReglages_toutesAffichees` (existant) |
| Rotation du bandeau au toucher : seul le niveau d'animation décide (d9986d5a) | `HeroRotationTest.au tactile le comportement de la 1_3_72 est conserve…` (existant) |
| Colonne paresseuse : seules les rangées visibles sont composées | `colonneParesseuse_rangeesHorsEcranNonComposees` |
| Chaînes en double dans les données : une seule carte, aucune clé dupliquée (`distinctBy { it.channel.id }`) | `chainesEnDouble_uneSeuleCarte_aucunPlantage` |
| Rangée à un seul élément, rangées toutes vides, aucune image : aucun plantage | `donneesMinimales_aucunPlantage` |
| Aucune source : encart + « Synchroniser depuis le cloud » + « Ajouter une source » | `aucuneSource_encartEtDeuxActions` |
| Chargement : encart avec pourcentage lu en différé | `chargement_pourcentageAffiche` |
| Toucher l'image à la une ouvre la fiche (film → `onOpenMovie`, série → `onOpenSeries`) | `toucherLImage_ouvreLaFiche` |
| Toucher une affiche ouvre la bonne fiche ; une carte Reprendre appelle `onPlayHistory` ; une chaîne appelle `onPlay(url, titre)` | `actions_desCartes` |
| « Tout voir » de la rangée chaînes : Direct, ou Favoris quand `showingFavorites` | `toutVoir_menAuBonEcran` |
| Tirer pour rafraîchir présent (`TouchRefresh`) | `tirerPourRafraichir_present` |
| Arabe : la première carte de chaque rangée est du côté droit | `arabe_premiereCarteADroite` |

- [ ] **Step 1: Écrire `MobileHomeHost.kt`** selon la signature ci-dessus.
- [ ] **Step 2: Écrire les tests** du tableau sur l'écran ACTUEL (ils doivent passer avant toute modification ; un test qui échoue ici révèle un défaut préexistant : le noter dans le rapport, le corriger dans la tâche qui touche ce code, pas ici). Les tests ciblent des `testTag` : en ajouter, sans autre changement, dans `MobileHome.kt` : `"m-home"` (colonne), `"m-row-<HomeSectionId>"` (conteneur de rangée), `"m-card-<clé>"` (carte), `"m-hero"`, `"m-notice"`.
- [ ] **Step 3: Lancer** `--tests '*MobileHomeRegressionTest*' --tests '*HomeLogicTest*' --tests '*HeroRotationTest*'` → tout passe.
- [ ] **Step 4: Commit** — `Accueil tactile : tests de non-régression avant la refonte`.

### Task 2: Journal d'erreurs d'écran et états de l'accueil tactile

**Files:**
- Create: `K/ui/common/UiLog.kt`
- Modify: `K/ui/design/Images.kt` (`SlotImage` : lecture de `LocalImageFailures`)
- Modify: `K/ui/home/HomeScreen.kt` (branche tactile : calcul de l'encart et passage de l'échec), `K/ui/mobile/MobileHome.kt` (`MobileHomeState`, `MobileNotice`)
- Modify: `K/ui/home/HomeViewModel.kt` (`toggleHeroList` : échec journalisé)
- Test: Create `T/ui/common/UiLogTest.kt` ; `T/ui/mobile/MobileHomeRegressionTest.kt`

**Interfaces:**
- Produces (réutilisé par les lots 3 à 12 ; si un lot l'a déjà créé, le réutiliser tel quel) :
  - `object UiLog { fun failure(screen: String, action: String, t: Throwable, url: String? = null) ; @VisibleForTesting internal var sink: ((tag: String, message: String) -> Unit)? }` — tag `"ui.$screen"`, message `"$action · ${NetErrors.classify(t)} · ${UtvLog.why(t)}"` suivi de `" · " + UtvLog.maskUrl(url)` si `url` ; envoi par `UtvLog.w` (logcat masqué + `RemoteLog.warn`, la version de l'appli est ajoutée par `RemoteLog`) ; `sink` reçoit la même ligne MASQUÉE (tests).
  - `suspend inline fun <T> uiAttempt(screen: String, action: String, url: String? = null, block: () -> T): Result<T>` — remplace `runCatching` dans le code d'écran : relance `CancellationException` sans rien journaliser, sinon `UiLog.failure` puis `Result.failure`.
  - `class ImageFailureTally(val screen: String) { fun note(url: String?) ; fun flush() }` et `val LocalImageFailures = staticCompositionLocalOf<ImageFailureTally?> { null }` ; `@Composable fun rememberImageFailureTally(screen: String): ImageFailureTally` — `flush()` au `onDispose` de l'écran : UNE ligne `UtvLog.w("ui.$screen", "images introuvables : <n> (hôtes : <hôtes masqués, 3 au plus>)")` si `n > 0`, rien sinon. `SlotImage` appelle `LocalImageFailures.current?.note(url)` dans `onError` (lu une fois par image, `null` partout ailleurs : aucun coût ajouté hors des écrans refaits).
  - `MobileHomeState(slot: HomeTopSlot, failure: MobileFailureUi?, hero: HeroItem?, rows: HomeRows, profileName: String, profileInitial: String, profileColor: Int, sportOn: Boolean)` avec `@Immutable data class MobileFailureUi(val title: String, val body: String, val providerId: Long)` ; `MobileHomeScreen(..., onRetry: (Long) -> Unit = {}, ...)`.
- Consumes: `homeTopSlot(...)` et `HomeTopSlot` (`K/ui/home/HomeLogic.kt:241-256`), `DataStateViewModel.failure` / `retry(providerId)` (`K/ui/common/DataStates.kt`), `S.sync.messageFor(kind)`, `D.sourceErrorBody(provider)`, `D.unsupportedSource`, `D.unsupportedSourceHint`, `D.openSourceSettings`, `D.retry` (textes existants, déjà traduits).

Erreurs possibles de l'écran, ce que voit l'utilisateur, ce qui est journalisé :

| Cas | À l'écran | Journal |
|---|---|---|
| Aucune source | encart « aucune source » + cloud + ajouter (inchangé) | rien (pas une erreur) |
| Sources toutes non prises en charge (Stalker hérité) | encart `unsupportedSource` + « Réglages des sources » (nouveau au toucher) | rien (déjà connu de la synchro) |
| Source en erreur (réseau, DNS, délai, identifiants refusés, fournisseur bloqué, réponse illisible) | encart : `S.sync.messageFor(kind)`, `D.sourceErrorBody(provider)`, « Réessayer » (`onRetry(providerId)`) et « Réglages des sources » (nouveau au toucher) | déjà journalisé par la synchro ; l'écran n'ajoute pas de doublon |
| Synchro en cours sans titre | encart de chargement avec pourcentage (inchangé) | rien |
| Rangée sans données | rangée absente (inchangé, `presentSections`) | rien |
| Image introuvable (affiche, fond, logo) | initiales ou aplat du cadre (inchangé) | une ligne de synthèse par visite (`ImageFailureTally`) |
| « Ma liste » qui échoue (base indisponible) | l'icône garde l'état réel (observé) | `UiLog.failure("home", "ma-liste", t)` |
| Lecture impossible | gérée par le lecteur (lots 4 à 6), hors de cet écran | — |

- [ ] **Step 1: Tests qui échouent.** `UiLogTest` (JUnit pur + `sink`) :

```kotlin
@Test fun ligne_ecranActionCategorieCause_sansSecret() {
    val lines = mutableListOf<Pair<String, String>>(); UiLog.sink = { t, m -> lines += t to m }
    UiLog.failure("home", "ma-liste", java.net.UnknownHostException("x.invalid"), url = "http://h.tv:8080/movie/jean/s3cret/12.mkv?token=abc")
    val (tag, msg) = lines.single()
    assertEquals("ui.home", tag)
    assertTrue(msg.startsWith("ma-liste · HOST_NOT_FOUND · UnknownHostException"))
    assertFalse(msg.contains("jean")); assertFalse(msg.contains("s3cret")); assertFalse(msg.contains("abc"))
}
@Test fun annulation_relancee_etJamaisJournalisee() = runTest {
    val lines = mutableListOf<String>(); UiLog.sink = { _, m -> lines += m }
    assertThrows(CancellationException::class.java) { runBlocking { uiAttempt("home", "x") { throw CancellationException() } } }
    assertTrue(lines.isEmpty())
}
@Test fun images_uneSeuleLigneParVisite_hotesMasques() { /* note() ×5 sur 2 hôtes avec identifiants Xtream → flush() : 1 ligne, « 5 », aucun identifiant ; flush() sans échec : 0 ligne */ }
```

  `MobileHomeRegressionTest` : `sourceEnErreur_messageEtActions` (le message de `messageFor(UNAUTHORIZED)` est affiché, « Réessayer » appelle `onRetry(7L)`, « Réglages des sources » appelle `onGoSettings`) ; `sourcesNonPrisesEnCharge_encartEtReglages` ; `encart_policeA13_rienDeRogne` (bornes des textes et boutons dans `"m-notice"`).
- [ ] **Step 2: Constater l'échec** — `--tests '*UiLogTest*' --tests '*MobileHomeRegressionTest*'`.
- [ ] **Step 3: Implémenter** `UiLog.kt` ; la branche tactile de `HomeScreen` calcule `slot` avec la MÊME expression que la TV (`homeTopSlot(h != null, loaded, providers.map { it.kind }, SUPPORTED_KINDS, f != null)`) et construit `MobileFailureUi` ; `MobileNotice` dessine les cas `NO_SOURCE`, `UNSUPPORTED`, `FAILURE`, `LOADING` (rien pour `NONE`, comme la TV). `toggleHeroList` passe par `uiAttempt`. Garder les tests de la tâche 1 verts (adapter seulement la construction de `MobileHomeState` dans l'hôte).
- [ ] **Step 4: Lancer** `--tests '*UiLogTest*' --tests '*MobileHome*' --tests '*LogSanitizerTest*' --tests '*Home*'` → passe.
- [ ] **Step 5: Commit** — `Accueil tactile : causes d'erreur affichées avec Réessayer, journal d'erreurs d'écran commun`.

### Task 3: Pièces partagées TV / toucher : grand chiffre du Top 10 et focus visible au clavier

**Files:**
- Modify: `K/ui/design/Streaming.kt` (nouveau `Top10RankNumber`, anneau tactile des boutons sur image)
- Modify: `K/ui/design/Design.kt` (`FocusSurface` : paramètre facultatif)
- Modify: `K/ui/home/HomeScreen.kt` (`Top10Rank` remplacé par l'appel au composant partagé)
- Test: `T/ui/design/StreamingTest.kt`, `T/ui/home/HomeTvStructureTest.kt` (existant, doit rester vert)

**Interfaces:**
- Produces:
  - `@Composable fun Top10RankNumber(rank: Int, fontDp: Dp, strokeDp: Dp, modifier: Modifier = Modifier)` — chiffres latins (`"$rank"`, jamais formatés par la locale), Sora ExtraBold, `drawStyle = Stroke(strokeDp.toPx())`, couleur `Ux.Text3`, taille convertie de `fontDp` (NON soumise à la taille de texte ni à la police système), `maxLines = 1`, `softWrap = false`, `clearAndSetSemantics { }`, `TextStyle` mémorisé. TV : `Top10RankNumber(rank, 150.dp, 4.design)` — identique pixel pour pixel à l'actuel `Top10Rank`.
  - `FocusSurface(..., ringColor: Color? = null, ...)` — couleur de l'anneau de focus dans les DEUX modes : anneau de `ringWidth` sur TV, anneau de 2 dp au toucher quand l'élément a le focus (clavier, manette) ; `null` = `Ux.Accent` (inchangé pour tous les appelants). Réservé à ce qui est posé sur une image (règle de la spec) ; le lot 3 s'en sert pour les épisodes posés sur le fond de la fiche série.
  - `OnImageButton` et `OnImageIconButton` passent `ringColor = Color.White` (sur TV leur `ringWidth` reste 0 et `onImageRing` inchangé) : au toucher avec clavier, l'anneau blanc est visible sur le bouton blanc grâce à un liseré sombre `#0A0C10` de 2 dp (même principe que `onImageRing` sur TV).

- [ ] **Step 1: Tests qui échouent** (`StreamingTest`, Robolectric `w390dp-h844dp-xxhdpi`, `LocalTouch provides true`, mode clavier) :
  - `boutonSurImage_tactileAuClavier_anneauBlancVisible` : `OnImageButton(primary = true)` sur fond `#0A0C10`, focus demandé ; dans la capture, le pixel à 1 dp à l'extérieur du bord gauche du bouton est blanc (`> 0xF0` sur les trois canaux) et le pixel à 3 dp est sombre ; sans focus, le pixel à 1 dp est sombre.
  - `focusSurface_couleurDAnneauParDefaut_inchangee` : sans `ringColor`, le pixel d'anneau vaut `Ux.Accent` (thème sombre), au toucher et sur TV ; avec `ringColor = Color.White`, il est blanc dans les deux modes.
  - `top10RankNumber_tailleFixe_independanteDeLaPolice` : largeur du nœud identique avec `fontScale = 1f` et `1.3f`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.** L'anneau passe par `Modifier.border` déjà présent dans la branche tactile de `FocusSurface` : aucune couche ni état ajoutés.
- [ ] **Step 4: Lancer** `--tests '*StreamingTest*' --tests '*HomeTvStructureTest*' --tests '*HomeRenderTest*' --tests '*MobileNavTest*'` → passe (les tests Top 10 TV, dont la lisibilité du rang 10 de 3ed63933, restent verts sans modification).
- [ ] **Step 5: Commit** — `Design : grand chiffre du Top 10 partagé, focus visible au clavier sur les boutons posés sur une image`.

### Task 4: En-tête « Pour <profil> » et filtres rapides

**Files:**
- Modify: `K/ui/mobile/MobileHome.kt` (`MobileTopBar`, `MobileShortcuts` → `MobileQuickFilters`), `K/ui/home/HomeScreen.kt` (nom du profil, `onGoSport`)
- Modify: `R/values{,-fr,-es,-ar}/strings.xml`, `K/i18n/DesignStringsB1.kt`, `T/../resources/i18n-golden/{en,fr,es,ar}.json`
- Test: Create `T/ui/mobile/MobileHomeStructureTest.kt`

**Interfaces:**
- Consumes: `ProfileViewModel.current` (`ProfileEntity.name`, `.initial`, `.color`) ; `LocalSportEnabled` ; `ShortcutUi` / `ShortcutId` ; `formatCount(n, D.locale)` et `D.homeChannelsCount(...)` etc. (pour TalkBack).
- Produces: `fun DesignStrings.mobileForProfile(name: String): String` (clé `mh_for_profile` : en « For %1$s », fr « Pour %1$s », es « Para %1$s », ar « لـ%1$s ») ; `testTag` `"m-topbar"`, `"m-search"`, `"m-profile"`, `"m-filters"`, `"m-filter-<LIVE|MOVIES|SERIES|SPORT|FAVORITES|GUIDE>"`.

Valeurs (maquette, dp) : barre 64 de haut, marges 12 / 16 ; titre Sora ExtraBold 22 sp, une ligne, ellipse, `weight(1f)` ; loupe ronde 44 (cible 48) fond `Ux.Surface`, icône 22 ; profil carré arrondi 44, rayon 12, couleur du profil, initiale Sora ExtraBold 18. Filtres : `LazyRow`, marge début 16, écart 8, pastilles hauteur 44 (cible 48), padding 16, contour 1,5 `Ux.LineKey`, fond `Ux.Surface`, texte Manrope Bold 15 `Ux.Text`, une ligne. Ordre : Direct, Films, Séries, Sport (si activé), Favoris, Guide. Chaque pastille est une `FocusSurface` (focus visible au clavier). Aucune pastille n'est posée sur l'image (on garde les jetons du thème).

Place des filtres : ils représentent le bloc `SHORTCUTS`. Masqué par l'utilisateur → absents. Premier bloc visible de l'ordre (cas par défaut) → juste sous l'en-tête, AVANT l'image à la une (maquette). Déplacé ailleurs par l'utilisateur → à sa place parmi les rangées. Aucune préférence n'est relue autrement.

- [ ] **Step 1: Tests qui échouent** : `entete_nomDuProfil_etDeuxBoutons` (texte « Pour Principal », `m-search` → `onSearch`, `m-profile` → `onProfile`) ; `entete_nomTresLong_uneLigne_boutonsVisibles` ; `filtres_ordreEtSport` (sans sport : 5 pastilles dans l'ordre ; avec : `m-filter-SPORT` en 4e, clic → `onGoSport`) ; `filtres_parDefaut_auDessusDeLImage` (bornes : bas de `m-filters` ≤ haut de `m-hero`) ; `filtres_deplacesParLUtilisateur_restentALeurPlace` (layout avec SHORTCUTS après RESUME → `m-filters` sous `m-row-RESUME`) ; `filtres_masques_absents` ; `filtres_compteurPourTalkBack` (description de `m-filter-LIVE` contient « 1 240 ») ; `arabe_compteursEnChiffresLatins` (aucun caractère `٠..٩` dans les descriptions).
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter** ; ajouter le texte dans les 4 langues, l'extension dans `DesignStringsB1.kt`, puis lancer `I18nGoldenTest` : copier dans les 4 golden la ligne qu'il signale manquante pour `mobileForProfile`.
- [ ] **Step 4: Lancer** `--tests '*MobileHome*' --tests '*I18n*' --tests '*StringsXmlCompletenessTest*'` → passe.
- [ ] **Step 5: Commit** — `Accueil tactile : en-tête au nom du profil et filtres rapides`.

### Task 5: Image à la une tactile

**Files:**
- Create: `K/ui/mobile/MobileHomeRows.kt` (y déplacer `MobileHero` et les cartes ; `MobileHome.kt` garde l'écran et les composants communs des fiches, que le lot 3 reprendra)
- Modify: `K/ui/home/HomeScreen.kt` (branche tactile : `watchHero`, `heroInList`, `toggleHeroList` passés), `K/ui/mobile/MobileHome.kt`
- Test: `T/ui/mobile/MobileHomeStructureTest.kt`, Create `T/ui/mobile/MobileHeroGeometryTest.kt`

**Interfaces:**
- Consumes: `OnImageButton(text, onClick, modifier, primary, iconPath, iconFill, heightPx)`, `OnImageIconButton` (tâche 3 : anneau tactile) ; `Modifier.imageScrims(start, bottom, rtl, pageColor, bottomSpan)` ; `BackdropImage`, `PosterImage(..., decodeSize, background, initialsColor)` ; `heroInfoLine`, `runtimeLabel`, `heroRatingBadges` (`HomeLogic.kt`) ; `D.heroMyList`, `S.play`, `D.removeFavorite` / `D.addFavorite` (état TalkBack) ; `vm.heroInList`, `vm.toggleHeroList`, `vm.playHero` (existants, ceux de la TV).
- Produces:
  - `internal data class MobileHeroGeometry(val widthDp: Float, val heightDp: Float, val portraitArt: Boolean, val centered: Boolean)` et `internal fun mobileHeroGeometry(contentWidthDp: Float, windowHeightDp: Float): MobileHeroGeometry` (pure).
  - `MobileHomeScreen(..., heroInList: State<Set<String>>, onWatchHero: (HeroItem) -> Unit, onToggleHeroList: (HeroItem) -> Unit, ...)`.
  - `testTag` `"m-hero"`, `"m-hero-play"`, `"m-hero-list"`.

Règle de `mobileHeroGeometry` (la largeur est celle du CONTENU, mesurée par un `BoxWithConstraints` dans l'élément de l'image seulement : avec le rail latéral de la tablette, la fenêtre est plus large que le contenu) :
- portrait compact (`contentWidthDp < 600` et `windowHeightDp >= contentWidthDp`) : largeur `contentWidthDp − 32`, hauteur `clamp(windowHeightDp × 0.5, 280, 420)`, `portraitArt = true` (affiche si elle existe, sinon fond recadré), `centered = true` ;
- sinon (paysage, tablette) : largeur `contentWidthDp − 2 × marge` (marge 16 si `< 600`, 24 sinon), hauteur `clamp(min(largeur × 9 / 16, windowHeightDp × 0.6), 200, 520)`, `portraitArt = false` (fond paysage ; sans fond, affiche 2:3 dans une zone fixe à droite, jamais étirée), `centered = false`.

Valeurs (maquette, dp) : carte arrondie 20, marge 16 ; voile bas `imageScrims(start = !centered, bottom = true, rtl, pageColor = Color(0xFF0A0C10), bottomSpan = 0.57f)` (240 / 420 de la maquette, toujours sombre : le texte est clair dans tous les thèmes) ; bloc texte à 16 des bords, écart 10 ; centré en portrait, aligné au début sinon ; titre Sora ExtraBold 38 / interligne 40, deux lignes au plus, ellipse (logo Fanart s'il est chargé, comme TV) ; ligne d'infos Manrope SemiBold 14 `#E3E6EB` ; deux boutons hauteur 48 côte à côte, poids égaux, écart 10 : « Lecture » (`OnImageButton(primary = true, iconPath = Icons.Play, heightPx = 96)`, soit 48 dp) et « Ma liste » (`OnImageButton` secondaire avec icône `Icons.Plus` / `Icons.Check` selon `heroInList`, `stateDescription`). Décodage : `decodeSize = DpSize(geometry.widthDp.dp, geometry.heightDp.dp)`. Tout le bloc texte est dans `NoTextWrap`.

Comportements gardés : toucher l'image hors des boutons ouvre la fiche (`onOpenHero`) ; rotation et fondu comme aujourd'hui (le titre affiché change ; aucun focus ne bouge) ; sans titre à la une, l'accueil commence par l'en-tête, les filtres et les rangées.

- [ ] **Step 1: Tests qui échouent.** `MobileHeroGeometryTest` (pur) :

```kotlin
@Test fun telephonePortrait_valeursDeLaMaquette() = assertEquals(MobileHeroGeometry(358f, 420f, true, true), mobileHeroGeometry(390f, 844f))
@Test fun petitTelephone() = assertEquals(MobileHeroGeometry(288f, 284f, true, true), mobileHeroGeometry(320f, 568f))
@Test fun telephonePaysage() = assertEquals(MobileHeroGeometry(812f, 234f, false, false), mobileHeroGeometry(844f, 390f))
@Test fun tablettePaysage() = assertEquals(MobileHeroGeometry(1152f, 480f, false, false), mobileHeroGeometry(1200f, 800f))
@Test fun tablettePortrait() = assertEquals(MobileHeroGeometry(672f, 378f, false, false), mobileHeroGeometry(720f, 1280f))
@Test fun largeurNulle_jamaisNegatif() { val g = mobileHeroGeometry(0f, 0f); assertTrue(g.widthDp >= 0f && g.heightDp >= 200f) }
```

  `MobileHomeStructureTest` : `aLaUne_lectureEtMaListe` (« Lecture » sur un film → `onWatchHero(h)` ; « Ma liste » → `onToggleHeroList(h)` ; l'icône bascule quand `heroInList` change, sans recomposer la rangée suivante) ; `aLaUne_toucherHorsBoutons_ouvreLaFiche` ; `aLaUne_petitTelephonePolice13_toutDansLaCarte` (320 × 568, `fontScale = 1.3f`, titre de 120 caractères : bornes de `m-hero-play` et `m-hero-list` dans `m-hero`) ; `aLaUne_paysage_texteAuDebut_arabeADroite` ; `aLaUne_sansFondNiAffiche_aucunPlantage` ; `aLaUne_imageIntrouvable_uneLigneDeJournalALaSortie`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer** `--tests '*MobileHero*' --tests '*MobileHome*' --tests '*HeroRotationTest*'` → passe.
- [ ] **Step 5: Commit** — `Accueil tactile : grande image à la une avec Lecture et Ma liste`.

### Task 6: Rangées tactiles (En direct, Reprendre, affiches, Top 10) et compteur de recompositions

**Files:**
- Modify: `K/ui/mobile/MobileHomeRows.kt`, `K/ui/mobile/MobileHome.kt` (`SectionHeader`)
- Create: `K/ui/mobile/MobileHomeProbe.kt`
- Test: `T/ui/mobile/MobileHomeStructureTest.kt`, Create `T/ui/mobile/MobileHomeRecompositionTest.kt`

**Interfaces:**
- Consumes: `Top10RankNumber` (tâche 3) ; `LiveBadge()`, `StreamingProgressLine(fraction, modifier, track)`, `channelTrackColor()` (`HomeScreen.kt:977`, à rendre `internal` s'il ne l'est pas) ; `PosterCell(item, modifier, kind, imageWidth, focusedScale, onClick)` ; `rememberRowFocusMemory()`, `rowMemory(memory, key)`, `RowFocusMemory.tracker`, `LocalHomeRowId` (`K/ui/home/RowFocusMemory.kt` — sans `HomeEdgeGate` ni `HomeFocusRestore`, qui restent propres à la TV : la mémoire fonctionne alors seule) ; `formatCount`, `D.hourMinLeft`, `D.minLeft` passés par `tx` / `DisplayLocale` (pas `String.format` : défaut arabe relevé en `MobileHome.kt:302-303`).
- Produces:
  - `internal class MobileHomeProbe { val cards = HashMap<String, Int>(); var hero = 0; var notice = 0 }`, `internal val LocalMobileHomeProbe = staticCompositionLocalOf<MobileHomeProbe?> { null }`, `internal inline fun ProbeCard(key: String)` — même schéma que `K/ui/guide/GuideProbe.kt` (`null` en production : une lecture de local, aucun effet).
  - `testTag` `"m-live-<id>"`, `"m-resume-<clé>"`, `"m-poster-<clé>"`, `"top10-rank-<n>"`, `"m-see-all-channels"`.

Valeurs (dp ; « large » = largeur de contenu ≥ 600) :
- Titre de rangée : Sora Bold 19, une ligne, marge début 16, écart titre / cartes 10, écart entre rangées 20. « Tout voir » (rangée chaînes) : `FocusSurface` texte Manrope SemiBold 13, cible 48.
- En direct : carte 196 × 110 (large 240 × 135), rayon 12, écart 10, fond `Ux.Card` ; `LiveBadge` à 10 / 10 ; logo de chaîne 28 × 28 en haut à la fin (`LogoBox`, `decodeSize` 28 × 28) ; en bas à 10 / 12 : programme en cours Sora Bold 15 (à défaut le nom de la chaîne), une ligne ; nom de la chaîne Manrope SemiBold 12 `Ux.Text3`, sans numéro (le numéro de la source est souvent propre à une catégorie : décision déjà prise sur TV) ; `StreamingProgressLine` de 4 collée au bas, piste `channelTrackColor()`, seulement si `progress != null`. Description TalkBack : chaîne — programme (comportement actuel).
- Reprendre (point à trancher n° 1) : affiche 112 × 168 (large 140 × 210), rayon 10, `ProgressLine` 4 au bas de l'affiche ; sous l'affiche, titre Manrope Bold 13 une ligne, temps restant Manrope 12 `Ux.Text3`. « Chaînes regardées récemment » : carte large actuelle 200 × 112 inchangée.
- Affiches : 110 de large (large 150), comme aujourd'hui.
- Top 10 : affiche 104 × 156 (large 130 × 195) alignée à la fin de la cellule ; chiffre `Top10RankNumber(rank, 112.dp, 2.dp)` (large `140.dp, 2.5.dp`) au début ; cellule 156 (rangs 1 à 9) et 214 (rang 10), large × 1,25. Ce sont des valeurs de départ : si le test de lisibilité échoue, ÉLARGIR la cellule (jamais réduire le chiffre) et reporter la valeur retenue dans le commit.
- Toutes les cartes sont des `FocusSurface` (fini les `clickable` nus du `MobileHome` actuel : focus visible au clavier) ; chaque `LazyRow` porte `rememberRowFocusMemory()` + `memory.tracker`, chaque carte `rowMemory(memory, clé)`, avec `LocalHomeRowId provides id.name`.

- [ ] **Step 1: Tests qui échouent.** Structure : `direct_carteDeLaMaquette` (196 × 110 ; sans programme : nom de la chaîne, pas de progression) ; `direct_police13_textesDansLaCarte` ; `reprendre_afficheTitreEtReste_arabeEnChiffresLatins` ; `top10_rangs1a10_lisibles_ltrEtRtl` (même règle que le test TV de 3ed63933 : partie visible du chiffre ≥ 60 % de sa largeur, pour les rangs 1 à 10, en LTR et en RTL, sur téléphone et tablette ; jamais focalisable ; ignoré de TalkBack) ; `top10_moinsDe4Titres_rangeeAbsente` ; `rangees_clesUniques_listeAUnElement`. Recompositions (`MobileHomeRecompositionTest`, montage avec `LocalMobileHomeProbe provides probe`) :

```kotlin
@Test fun pourcentageDeSynchro_neRecomposeNiCartesNiImage() { /* état LOADING puis syncPercent 0 → 50 : probe.cards vide, probe.hero == 0, probe.notice >= 1 */ }
@Test fun nouvellesRangeesEgales_aucuneCarteRecomposee() { /* rows = rows.copy() (mêmes listes) : probe.cards vide */ }
@Test fun clavier_droiteDansUneRangee_deuxCartesAuPlus() { /* focus sur m-poster-p1, ▶ : probe.cards.keys ⊆ {p1, p2} */ }
@Test fun rotationDuTitreALaUne_aucuneCarteDeRangee() { /* hero change : probe.cards vide */ }
```

- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter** (aucun état remonté à la rangée ; libellés formatés une fois par carte dans `remember(clé, D)`).
- [ ] **Step 4: Lancer** `--tests '*MobileHome*' --tests '*HomeTvStructureTest*' --tests '*RowFocusMemoryTest*'` → passe.
- [ ] **Step 5: Commit** — `Accueil tactile : rangées En direct, Reprendre, affiches et Top 10 lisible`.

### Task 7: Clavier et manette sur tablette, rotation, retour d'une fiche

**Files:**
- Test: Create `T/ui/mobile/MobileHomeNavigationTest.kt`
- Modify: `K/ui/mobile/MobileHome.kt`, `K/ui/mobile/MobileHomeRows.kt` seulement si un test révèle un défaut

**Interfaces:**
- Consumes: les `testTag` des tâches 4 à 6 ; `setMobileHome(..., keyboard = true)` ; `StateRestorationTester` (compose-ui-test) pour la mort d'activité et le retour d'une fiche.

Décisions : au toucher, AUCUN focus initial n'est demandé (un téléphone ne doit pas s'ouvrir avec un anneau) ; la première touche directionnelle ou Tab entre dans le contenu par l'en-tête. ▲ ▼ changent de bloc dans l'ordre visuel, ◀ ▶ parcourent une rangée et s'arrêtent à ses bouts, Tab suit l'ordre visuel. Retour reste celui de la navigation tactile (`MobileNav.kt:188`), inchangé. La position de défilement de la colonne et de chaque rangée est sauvegardée (états `rememberLazyListState` saveable de la colonne et des éléments).

- [ ] **Step 1: Écrire les tests de parcours** (tablette paysage 1280 × 800, clavier ; refaire 2 et 3 en arabe ; 5 sur téléphone et tablette) :
  1. `clavier_premiereTouche_entreParLEntete` : rien de focalisé à l'ouverture ; ▼ → `m-search` ou la première pastille, visible.
  2. `clavier_descenteComplete_ordreVisuel_rienDeDecoratif` : ▼ répété : en-tête, filtres, `m-hero-play`, puis la première carte de chaque rangée dans l'ordre affiché ; à chaque pas un seul élément focalisé, entièrement visible, jamais un `top10-rank-<n>` ni un titre ; ▼ sur la dernière rangée ne change rien.
  3. `clavier_rangee_droiteGauche_bouts` : ▶ carte par carte jusqu'à la dernière, où ▶ ne change rien ; ◀ revient ; en arabe directions inversées.
  4. `clavier_memoireDeRangee` : 3e carte de Reprendre, ▼ puis ▲ → 3e carte.
  5. `rotation_gardeDefilementEtCarte` : défiler jusqu'à la rangée Top 10, ▶ ×4 ; `StateRestorationTester.emulateSavedInstanceStateRestore()` en changeant la taille (portrait → paysage) : la rangée Top 10 est à l'écran et sa 5e carte visible ; aucune exception.
  6. `retourDUneFiche_memeDefilement` : idem avec la même taille (sortie / retour de composition) : même position verticale, même carte en tête de rangée.
  7. `donneesQuiChangent_focusNeBougePas` : focus clavier sur une affiche, `rows` remplacées par une nouvelle valeur contenant la même affiche, puis le titre à la une change : même nœud focalisé.
  8. `toucherPuisClavier_aucunAnneauFantome` : un toucher n'affiche aucun anneau (mode tactile) ; une touche ensuite en affiche un seul.
- [ ] **Step 2: Lancer** `--tests '*MobileHomeNavigationTest*'`. Un échec révèle un vrai défaut.
- [ ] **Step 3: Corriger** chaque défaut par le plus petit changement (`focusProperties`, `focusGroup`, clé stable, état saveable), sans `LaunchedEffect` qui déplace le focus. Un point impossible à tester sous Robolectric va dans le rapport avec ce qui le garantit dans le code.
- [ ] **Step 4: Lancer** `--tests '*MobileHome*' --tests '*MobileNavTest*' --tests '*TopLevelNavTest*'` → passe.
- [ ] **Step 5: Commit** — `Accueil tactile : parcours au clavier et à la manette, rotation et retour vérifiés`.

### Task 8: Dix thèmes, captures, CHANGELOG, suite complète, contrôle sur appareil

**Files:**
- Create: `T/ui/mobile/MobileHomeRenderTest.kt`, `T/ui/mobile/MobileHomeThemeContrastTest.kt`
- Modify: `CHANGELOG.md` (« Non publié »), `README.md`, `README.fr.md` (si la description de l'accueil téléphone y figure), `docs/screenshots/{fr,en,ar}/home-phone*.png`
- Vérifier : `android-native/app/src/main/baseline-prof.txt`

- [ ] **Step 1: Contrastes** (`MobileHomeThemeContrastTest`, même méthode que `T/ui/home/HomeThemeContrastTest.kt`, thèmes appliqués comme sur un téléphone : `isLightTheme(theme, systemDark = false, isTv = false)` ET `systemDark = true`) : pour chaque `AppTheme.entries` — titre de rangée sur `bg` ≥ 7 ; texte de pastille sur `card` ≥ 4,5 ; contour `lineKey` sur `bg` ≥ 3 ; texte atténué des cartes sur `card` ≥ 4,5 ; accent (anneau, progression) sur `card` ≥ 3 ; `onAccent` sur `accent` ≥ 3.
- [ ] **Step 2: Rendu de tous les thèmes** (`MobileHomeRenderTest`) : accueil complet (image à la une, filtres, En direct, Reprendre, Top 10, affiches) dans CHAQUE thème, téléphone portrait, écrit `home-phone-theme-<thème>.png` ; échoue si un thème plante. Puis captures de documentation : `home-phone-fr.png`, `-light`, `-ar` (portrait 390 × 844), `home-phone-land-fr.png` (844 × 390), `home-tablet-fr.png` (1280 × 800, avec rail), `home-phone-fr-keyboard.png` (focus clavier sur « Lecture »), `home-phone-fr-error.png` (source en erreur). Ouvrir CHAQUE capture : texte lisible, rien de rogné, focus évident, accent du thème, aucune couleur qui jure, chiffres latins en arabe. Corriger avant de continuer.
- [ ] **Step 3: Profil de démarrage** : si `baseline-prof.txt` nomme des méthodes exactes de `ui/mobile` renommées ou supprimées, les mettre à jour ; s'il ne contient que des règles par paquet, ne rien changer.
- [ ] **Step 4: Suite complète** — `./gradlew --offline -q :app:testDebugUnitTest` → 0 échec (aucun accepté) ; la relancer une seconde fois pour écarter un test instable.
- [ ] **Step 5: CHANGELOG et README** (fr et en) : nouvel accueil téléphone / tablette, causes d'erreur affichées, focus visible au clavier ; copier les captures dans `docs/screenshots`. Commit — `Accueil tactile : captures, journal des modifications`.
- [ ] **Step 6: Liste de contrôle sur appareil réel** (à joindre à la PR, cochée par le propriétaire) :
  1. Téléphone Android 7 à 9 de 2 Go (32 bits) : ouverture à froid, défilement de bout en bout, aucune saccade nouvelle ; image à la une qui tourne si « Animations : Normales ».
  2. Téléphone récent, portrait puis paysage puis fenêtre partagée : mise en page correcte, position conservée à chaque rotation.
  3. Tablette en portrait et paysage avec le rail ; puis clavier Bluetooth et manette : parcours complet, focus visible partout (boutons blancs de l'image compris), aucun piège.
  4. Police système « Très grande » + taille de texte « Très grand » : rien de rogné (image à la une, cartes En direct, Top 10 « 10 »).
  5. Arabe : tout en miroir, chiffres latins partout (compteurs TalkBack, temps restant), puis rotation (défaut connu `I1` de retours-2-review : vérifier qu'aucun nombre ne repasse en chiffres arabo-indiens sur cet écran).
  6. Source coupée (mode avion, puis mauvais mot de passe) : message de cause, Réessayer et Réglages fonctionnent ; la ligne `ui.home…` / synchro apparaît dans la télémétrie SANS identifiant ni adresse complète.
  7. Les 10 thèmes : capture rapide de chacun.
  8. TalkBack : en-tête, pastilles avec compteur, « Lecture », « Ma liste » (état lu), chiffres du Top 10 non lus.
  9. Retour d'une fiche et du lecteur : même position, même carte.
- [ ] **Step 7: PR** vers `main` : ce qui change à l'écran, les points tranchés, les comportements préservés (tableau de la tâche 1), les mesures `perf` avant / après sur un téléphone, les captures. Pas de fusion sans CI verte ; aucune version publiée sans accord du propriétaire.
