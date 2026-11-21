# Interface façon streaming — lots 0 et 1 : composants communs et accueil TV — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** L'accueil TV affiche le titre « À la une » en image plein écran, puis des rangées (Direct en tête, Top 10 à grand chiffre, affiches au focus net), sans perdre une fonction ni une image par seconde.

**Architecture:** On remodèle `ui/home/HomeScreen.kt` en place : mêmes données (`HomeRows`, `HeroItem`, `heroFlow`), même `LazyColumn`, même logique de focus. Les seules pièces nouvelles sont de petits composants de dessin réutilisables par les lots suivants (`ui/design/Streaming.kt`) et un défaut de disposition dépendant de l'appareil dans `HomeLayout`. Le téléphone (`MobileHome`) n'est pas touché par ce plan.

**Tech Stack:** Kotlin, Jetpack Compose (foundation, tv-material3), Hilt, Robolectric + compose-ui-test pour les tests, Coil 3 pour les images.

**Spec:** `docs/superpowers/specs/2026-11-21-interface-streaming-design.md` (écrans 1 et 2 du canevas de maquettes).

Chemins abrégés : `K = android-native/app/src/main/kotlin/com/ultratv/tv/nativeapp`, `T = android-native/app/src/test/kotlin/com/ultratv/tv/nativeapp`. Commandes lancées depuis `android-native/`.

## Global Constraints

- Identité inchangée : Sora pour les titres, Manrope pour le reste. Tous les thèmes existants sont conservés (Sombre, Clair, Automatique, OLED, Minuit, Forêt, Prune, Sable, Contraste, Sarcelle) : couleurs par les jetons `Ux.*`, jamais en dur ; accent, surfaces et anneau de focus sont ceux du thème. Seule exception, ce qui est posé sur une image : voile sombre `#0A0C10`, texte clair et boutons blancs, identiques dans tous les thèmes.
- Aucune fonction ne disparaît. Le bloc `SHORTCUTS` reste proposé dans Réglages › Accueil.
- Une préférence `home_order` / `home_hidden` non vide n'est jamais réinterprétée.
- Performance : listes paresseuses avec `key` et `contentType` ; état de focus local à la carte ; agrandissement au focus par `graphicsLayer` ; voiles dessinés une fois (`drawWithCache`) ; une seule image de fond décodée à la taille affichée ; aucun flou.
- Navigation à la télécommande logique et stable (voir la section du même nom dans la spec) : l'ordre du focus suit l'ordre visuel ; aucune direction ne perd le focus ni ne le pose sur un élément décoratif ; le focus ne bouge jamais sans touche ; il revient sur l'élément quitté.
- Appareils modestes (`LocalLowRam` vrai, ou API < 31) : image à la une fixe, sans rotation ni fondu.
- Tout nouveau texte : `strings.xml` en `values`, `values-fr`, `values-es`, `values-ar` + une ligne par langue dans `app/src/test/resources/i18n-golden/{en,fr,es,ar}.json`. Ce plan n'ajoute qu'un texte (tâche 4).
- Aucune mention d'une appli concurrente dans le code, les commits, le README ou le CHANGELOG.
- Commits sans ligne d'attribution automatique. Pas de version publiée par ce plan.
- `I18nGoldenTest` échoue en local sur certaines machines pour des chiffres arabes (`ScreenTimeStrings.minutes(n)`…) : échec connu, présent sur `main`, vert en CI. Tout autre écart est un vrai échec.

## Review Focus

1. Titre « À la une » sans image paysage (affiche seule) : l'affiche reste dans une zone fixe à droite, jamais étirée, et le texte reste lisible. → test tâche 3.
2. Aucun titre « À la une » (source sans film ni série, accueil neuf) : l'accueil commence par la salutation et les rangées comme aujourd'hui, l'heure reste visible. → test tâche 3.
3. Arabe (droite à gauche) : le voile de lisibilité est du côté du texte (à droite), le chiffre du Top 10 du bon côté de l'affiche. → tests tâches 2 et 6.
4. Utilisateur TV qui réaffiche les raccourcis sans changer l'ordre : son choix survit au redémarrage (les deux préférences ne redeviennent pas vides). → test tâche 1.
5. Titre très long (deux lignes au plus) : les boutons « Regarder » et « Plus d'infos » restent entièrement dans l'image. → test tâche 3.

---

### Task 1: Défaut de disposition selon l'appareil

**Files:**
- Modify: `K/ui/home/HomeLayout.kt`
- Modify: `K/ui/home/HomeViewModel.kt:381`, `K/ui/settings/SettingsScreen.kt:771,781`, `K/ui/settings/PersonalizationDialogs.kt:152` (appelants de `parse`, `DEFAULT`, `orderCsv`)
- Test: `T/ui/home/HomeLayoutTest.kt`

**Interfaces:**
- Produces:
  - `HomeLayout.defaultFor(tv: Boolean): HomeLayout` — TV : ordre par défaut, `hidden = setOf(HomeBlock.SHORTCUTS)` ; téléphone : `HomeLayout.DEFAULT`.
  - `HomeLayout.parse(orderCsv: String?, hiddenCsv: String?, tv: Boolean = false): HomeLayout` — les deux chaînes vides → `defaultFor(tv)` ; sinon comportement actuel.
  - `HomeLayout.orderCsv(tv: Boolean = false): String` — `""` seulement si `this == defaultFor(tv)` ; sinon la liste complète des blocs. `hiddenCsv()` inchangé.

- [ ] **Step 1: Écrire les tests qui échouent** dans `HomeLayoutTest` :

```kotlin
@Test fun tv_jamaisPersonnalise_raccourcisMasques() {
    val l = HomeLayout.parse("", "", tv = true)
    assertFalse(l.isVisible(HomeBlock.SHORTCUTS))
    assertFalse(HomeSectionId.SHORTCUTS in l.apply(HomeSectionId.entries))
    assertEquals(HomeSectionId.CHANNELS, l.apply(HomeSectionId.entries).first())
}
@Test fun telephone_jamaisPersonnalise_inchange() =
    assertEquals(HomeLayout.DEFAULT, HomeLayout.parse("", "", tv = false))
@Test fun tv_preferenceExistante_jamaisReinterpretee() =
    assertTrue(HomeLayout.parse("", "TOP10", tv = true).isVisible(HomeBlock.SHORTCUTS))
@Test fun tv_raccourcisReaffiches_survitAuRedemarrage() {
    val shown = HomeLayout.defaultFor(true).toggle(HomeBlock.SHORTCUTS)
    val back = HomeLayout.parse(shown.orderCsv(tv = true), shown.hiddenCsv(), tv = true)
    assertTrue(back.isVisible(HomeBlock.SHORTCUTS))
}
@Test fun tv_defaut_enregistreVide() {
    val d = HomeLayout.defaultFor(true)
    assertEquals("", d.orderCsv(tv = true)); assertEquals("SHORTCUTS", d.hiddenCsv())
}
```

Le dernier test fixe un point : le défaut TV s'enregistre avec `hiddenCsv() == "SHORTCUTS"`, donc `parse` doit aussi rendre `defaultFor(true)` pour `("", "SHORTCUTS", tv = true)` — c'est déjà le cas par le chemin normal.

- [ ] **Step 2: Lancer et constater l'échec** — `./gradlew --offline -q :app:testDebugUnitTest --tests '*HomeLayoutTest*'` → échec de compilation (`defaultFor` inconnu).
- [ ] **Step 3: Implémenter** les trois signatures ci-dessus dans `HomeLayout.kt`. `apply` ne change pas : son raccourci `if (this == DEFAULT)` ne concerne que le défaut du téléphone, et le défaut TV passe par le chemin général, qui retire déjà les blocs de `hidden`.
- [ ] **Step 4: Brancher les appelants.** `tv` vaut `com.ultratv.tv.nativeapp.ui.common.isTelevision(context)`. `HomeViewModel` : passer `tv` à `parse`. `SettingsScreen` : `parse(..., tv)` aux deux endroits et `orderCsv(tv)` à l'enregistrement. `PersonalizationDialogs` : « Réinitialiser » applique `HomeLayout.defaultFor(tv)`. Vérifier par `grep -rn "HomeLayout\.\(parse\|DEFAULT\)\|orderCsv()" K` qu'il ne reste aucun appel sans `tv` hors `MobileHome` et les tests.
- [ ] **Step 5: Lancer** `--tests '*HomeLayoutTest*' --tests '*HomeLogicTest*' --tests '*PersonalizationTest*'` → tout passe.
- [ ] **Step 6: Commit** — `Accueil : raccourcis masqués par défaut sur TV, choix existants conservés`.

### Task 2: Composants de dessin communs (lot 0)

**Files:**
- Create: `K/ui/design/Streaming.kt`
- Test: `T/ui/design/StreamingTest.kt`

**Interfaces:**
- Produces:
  - `data class ScrimStops(val startAlpha: List<Pair<Float, Float>>, val bottomAlpha: List<Pair<Float, Float>>)` et `fun imageScrimStops(): ScrimStops` — pure. Voile de début : alpha 1,0 à 0 %, 0,86 à 45 %, 0 à 100 % sur les 62 premiers pour cent de la largeur. Voile bas : alpha 0 à 0 %, 1,0 à 100 % sur les 40 derniers pour cent de la hauteur.
  - `fun Modifier.imageScrims(start: Boolean = true, bottom: Boolean = true, rtl: Boolean, pageColor: Color): Modifier` — `drawWithCache` ; voile de début en `Color(0xFF0A0C10)` (toujours sombre), du côté gauche si `!rtl`, droit si `rtl` ; voile bas vers `pageColor` (appelant : `Ux.Bg`).
  - `@Composable fun LiveBadge(modifier: Modifier = Modifier)` — pastille « EN DIRECT » : texte existant `LocalDs.current.live` (`R.string.ds_live`, déjà traduit), fond `Ux.Accent`, texte `Ux.OnAccent`.
  - `@Composable fun ProgressLine(fraction: Float, modifier: Modifier)` — barre de 6 unités, piste `Color.White` à 22 %, remplissage `Ux.Accent`, `fraction` borné à 0..1.
  - `fun scrimSideIsLeft(rtl: Boolean): Boolean` — pure, `!rtl`.

- [ ] **Step 1: Tests qui échouent** dans `StreamingTest` (JUnit pur pour les deux premiers, Robolectric `@Config(sdk=[34])` + `createComposeRule` pour le troisième) :

```kotlin
@Test fun voiles_valeursDeLaMaquette() {
    val s = imageScrimStops()
    assertEquals(listOf(0f to 1f, 0.45f to 0.86f, 1f to 0f), s.startAlpha)
    assertEquals(listOf(0f to 0f, 1f to 1f), s.bottomAlpha)
}
@Test fun voile_duCoteDuTexte_enArabe() { assertTrue(scrimSideIsLeft(rtl = false)); assertFalse(scrimSideIsLeft(rtl = true)) }
@Test fun progression_horsBornes_neDepassePas() {
    rule.setContent { Column { ProgressLine(-1f, Modifier.width(100.dp).testTag("a")); ProgressLine(7f, Modifier.width(100.dp).testTag("b")) } }
    rule.onNodeWithTag("a").assertExists(); rule.onNodeWithTag("b").assertExists()   // ne plante pas, rien ne déborde
}
```

- [ ] **Step 2: Constater l'échec** — `--tests '*StreamingTest*'` → compilation.
- [ ] **Step 3: Implémenter** `Streaming.kt` selon les signatures. Un seul `Brush` par voile, créé dans `drawWithCache` (recalculé seulement si la taille change).
- [ ] **Step 4: Lancer** `--tests '*StreamingTest*'` → passe.
- [ ] **Step 5: Commit** — `Design : voiles sur image, pastille Direct et ligne de progression communs`.

### Task 3: Image « À la une » en plein écran

**Files:**
- Modify: `K/ui/home/HomeScreen.kt` (`HomeTvContent` élément `"top"`, `Hero`, `HeroBody`, `HomeGreeting`, suppression de `embedShortcuts` et de l'appel à `ShortcutGrid` à côté du hero)
- Test: Create `T/ui/home/HomeTvStructureTest.kt` (même montage que `HomeRenderTest`, qui appelle `HomeTvContent` avec des données fixes : s'en servir comme modèle)

**Interfaces:**
- Consumes: `Modifier.imageScrims(...)` (tâche 2), `HomeLayout.defaultFor` (tâche 1).
- Produces: `testTag` `"home-hero"` (le bloc image), `"hero-watch"` (bouton principal), `"hero-info"`, `"home-clock"`, `"home-shortcuts"` (rangée de raccourcis quand elle est affichée), `"home-greeting"`.

Valeurs fixées par la maquette (unités `design`) : image pleine largeur de la zone de contenu, hauteur **600** ; plus de carte arrondie ni de panneau de texte opaque ; bloc de texte à `HomeStart` du bord, largeur max **820** ; titre Sora ExtraBold **96**, deux lignes au plus, ellipse ; ligne d'infos existante (`HeroInfoRow`) ; résumé deux lignes au plus ; boutons hauteur **76** ; heure en haut à droite (`"home-clock"`). La salutation (`"home-greeting"`) n'est affichée que s'il n'y a pas d'image à la une. Image décodée à `DpSize(1920.design, 600.design)`. Sans image paysage : affiche 2:3 dans une zone fixe de 300 × 450 à droite, comme aujourd'hui en plus grand. Les points de pagination restent en bas à droite de l'image.

Comportements à préserver (ils ont chacun un commentaire dans le code actuel) : focus initial sur « Regarder » une seule fois par visite (`heroFocusDone`) ; `onFocusTop` qui ramène la liste en haut ; fondu de 500 ms seulement si `rotateHero`, coupé net sinon ; `NoTextWrap`.

- [ ] **Step 1: Tests qui échouent** dans `HomeTvStructureTest` (français, sombre, 1920 × 1080, TV) :

```kotlin
@Test fun aLaUne_pleineLargeur_sansGrilleDeRaccourcis() {
    setHome(hero = heroWithBackdrop, layout = HomeLayout.defaultFor(true))
    val hero = rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
    assertEquals(600.dp.value, hero.height.value, 1f)            // en unités design à l'échelle du test
    rule.onNodeWithTag("home-shortcuts").assertDoesNotExist()
    rule.onNodeWithTag("home-clock").assertExists()
    rule.onNodeWithTag("home-greeting").assertDoesNotExist()
    rule.onNodeWithTag("hero-watch").assertIsFocused()
}
@Test fun aLaUne_sansImagePaysage_afficheDansSaZone() {
    setHome(hero = heroPosterOnly); rule.onNodeWithTag("home-hero").assertExists(); rule.onNodeWithTag("hero-watch").assertIsFocused()
}
@Test fun sansTitreALaUne_salutationEtRangees() {
    setHome(hero = null); rule.onNodeWithTag("home-hero").assertDoesNotExist()
    rule.onNodeWithTag("home-greeting").assertExists(); rule.onNodeWithTag("home-clock").assertExists()
}
@Test fun titreTresLong_boutonsDansLImage() {
    setHome(hero = heroWithBackdrop.copy(title = "Titre ".repeat(30)))
    val hero = rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
    val watch = rule.onNodeWithTag("hero-watch").getUnclippedBoundsInRoot()
    assertTrue(watch.bottom <= hero.bottom && watch.top >= hero.top)
}
@Test fun raccourcisChoisis_affichesEnRangee() {
    setHome(hero = heroWithBackdrop, layout = HomeLayout.defaultFor(true).toggle(HomeBlock.SHORTCUTS))
    rule.onNodeWithTag("home-shortcuts").assertExists()
}
```

- [ ] **Step 2: Constater l'échec** — `--tests '*HomeTvStructureTest*'`.
- [ ] **Step 3: Implémenter** dans `HomeScreen.kt`. L'élément `"top"` de la `LazyColumn` ne porte plus de marge horizontale quand l'image est affichée ; l'image reçoit `imageScrims(rtl = LocalLayoutDirection.current == LayoutDirection.Rtl, pageColor = Ux.Bg)`. `SHORTCUTS`, quand il est visible, passe par la rangée existante `ShortcutsRow` (déjà utilisée quand il n'y a pas de hero). Supprimer `ShortcutGrid` s'il n'a plus d'appelant.
- [ ] **Step 4: Lancer** `--tests '*HomeTvStructureTest*' --tests '*HomeRenderTest*' --tests '*HeroRotationTest*' --tests '*HomeLogicTest*'` → passe.
- [ ] **Step 5: Regarder les captures** produites par `HomeRenderTest` (`home-tv-fr.png`, `-light`, `-en`, `-ar` ; le dossier de sortie est `outDir` dans ce test) : texte lisible sur l'image dans les quatre, voile à droite en arabe, rien de rogné. Corriger avant de valider.
- [ ] **Step 6: Commit** — `Accueil TV : titre à la une en image plein écran`.

### Task 4: « Ma liste » depuis l'image à la une

**Files:**
- Modify: `K/ui/home/HomeViewModel.kt`, `K/ui/home/HomeScreen.kt` (`HeroBody`)
- Modify: `app/src/main/res/values{,-fr,-es,-ar}/strings.xml`, `K/i18n/DesignStringsB1.kt`, `app/src/test/resources/i18n-golden/{en,fr,es,ar}.json`
- Test: `T/ui/home/HomeTvStructureTest.kt`

**Interfaces:**
- Consumes: la bascule de favori déjà utilisée par les fiches film et série (chercher `toggleFavorite` dans `K/ui/movies` et `K/ui/detail`) : même dépôt, même appel, aucune nouvelle table.
- Produces: `HomeViewModel.heroInList: StateFlow<Set<String>>` (clés `"m<id>"` / `"s<id>"` des titres à la une présents dans les favoris) ; `HomeViewModel.toggleHeroList(h: HeroItem)` ; `val DesignStrings.heroMyList` (clé `ds_hero_my_list` : en « My list », fr « Ma liste », es « Mi lista », ar « قائمتي ») ; `testTag` `"hero-list"` ; `contentDescription` du bouton = `heroMyList`.

- [ ] **Step 1: Tests qui échouent** : `"hero-list"` existe, est atteint par ▶ depuis `"hero-info"`, et son clic appelle la fonction passée (`onToggleList` reçu par `HomeTvContent`, compteur dans le test).
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter** : bouton rond de 76 après « Plus d'infos », icône « + » ou « ✓ » selon `heroInList` ; ajouter le texte dans les quatre langues, l'extension dans `DesignStringsB1.kt`, et une ligne `"DesignStrings/DesignStringsB1Kt.getHeroMyList()"` dans chaque golden.
- [ ] **Step 4: Lancer** `--tests '*HomeTvStructureTest*' --tests '*I18n*'` → passe (hors écarts connus des chiffres arabes).
- [ ] **Step 5: Commit** — `Accueil TV : ajouter le titre à la une à Ma liste`.

### Task 5: Rangée « En direct maintenant » en vignettes larges

**Files:**
- Modify: `K/ui/home/HomeScreen.kt` (`ChannelRow`, `ChannelCard`, `CHANNEL_CARD_HEIGHT`, `SeeAllCard`)
- Test: `T/ui/home/HomeTvStructureTest.kt`

**Interfaces:**
- Consumes: `LiveBadge`, `ProgressLine` (tâche 2) ; `ChannelCardUi` tel qu'il est (aucun champ ajouté : si le programme en cours est absent, la carte montre seulement la chaîne).
- Produces: `testTag` `"home-channel-<id>"` par carte.

Valeurs : carte **392 × 220**, coins 16, écart 20 ; en haut à gauche `LiveBadge` puis numéro et nom de chaîne ; en bas titre du programme (Sora Bold 26, une ligne, ellipse) et horaire ; `ProgressLine` collée au bas. « Tout voir » reste la dernière carte, à la même hauteur. Focus : `FocusSurface` existant avec `focusedScale = 1.06f` et l'anneau de focus du thème (comportement par défaut, sans couleur imposée).

- [ ] **Step 1: Tests qui échouent** : la première carte fait 392 × 220 ; ▼ depuis `"hero-watch"` amène le focus sur la première carte de chaîne ; une carte sans programme en cours s'affiche sans planter et sans `ProgressLine`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer** `--tests '*HomeTvStructureTest*' --tests '*HomeRenderTest*'` → passe ; regarder les captures.
- [ ] **Step 5: Commit** — `Accueil TV : rangée Direct en vignettes larges avec progression`.

### Task 6: Top 10 à grand chiffre et focus des affiches

**Files:**
- Modify: `K/ui/home/HomeScreen.kt` (`Top10Row`, `PosterRow`, `PosterRowPadding`)
- Modify: `K/ui/catalog/CatalogGrid.kt` (`PosterCell`) et `K/ui/catalog/CatalogV2.kt` (`PosterCellTv`) : deux paramètres facultatifs
- Test: `T/ui/home/HomeTvStructureTest.kt`

**Interfaces:**
- Produces: `PosterCell(..., focusedScale: Float? = null, ringColor: Color? = null)` — `null` = comportement actuel, pour que le catalogue ne change pas ; l'accueil passe `1.10f` et laisse `ringColor` à `null` : l'anneau reste celui du thème. `testTag` `"top10-rank-<n>"`.

Valeurs : cellule Top 10 de **336** de large ; chiffre Sora ExtraBold **300**, tracé au contour (épaisseur 4, couleur `Ux.Text3`), derrière l'affiche, du côté du début de lecture ; affiche de 200 du côté opposé. Le chiffre est décoratif pour les lecteurs d'écran (`clearAndSetSemantics { }`, le `testTag` étant posé sur un parent) ; l'affiche garde sa description. `PosterRowPadding` : la marge haute suit le nouvel agrandissement (10 % de 300 = 15, plus l'épaisseur de l'anneau du thème) sans recouvrir le titre de la rangée.

- [ ] **Step 1: Tests qui échouent** : dix rangs `"top10-rank-1"`…`"top10-rank-10"` au plus, dans l'ordre ; en arabe, le rang est à droite de son affiche ; une affiche focalisée de `PosterRow` ne recouvre pas le titre de sa rangée (bornes non sécantes) ; `PosterCell` sans les nouveaux paramètres rend la même taille qu'avant dans un test du catalogue existant.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.** L'agrandissement reste dans `graphicsLayer` ; ne pas ajouter d'état remonté à la rangée.
- [ ] **Step 4: Lancer** `--tests '*HomeTvStructureTest*' --tests '*HomeRenderTest*' --tests '*Catalog*'` → passe ; regarder les captures.
- [ ] **Step 5: Commit** — `Accueil TV : Top 10 à grand chiffre, focus des affiches à 10 % et anneau blanc`.

### Task 7: Appareils modestes

**Files:**
- Modify: `K/ui/home/HomeScreen.kt`
- Test: `T/ui/home/HeroRotationTest.kt` ou `HomeTvStructureTest.kt`

**Interfaces:**
- Produces: `internal fun heroMayRotate(rotateHero: Boolean, lowRam: Boolean, sdk: Int): Boolean` — vrai seulement si `rotateHero && !lowRam && sdk >= 31`.

- [ ] **Step 1: Test qui échoue** : `heroMayRotate(true, false, 34)` vrai ; faux pour `(true, true, 34)`, `(true, false, 30)`, `(false, false, 34)`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter** et s'en servir pour `vm.setHeroRotating(...)` et pour le choix fondu / coupe nette. `lowRam` = `com.ultratv.tv.nativeapp.ui.common.LocalLowRam.current`.
- [ ] **Step 4: Lancer** `--tests '*HeroRotationTest*' --tests '*HomeTvStructureTest*'` → passe.
- [ ] **Step 5: Commit** — `Accueil TV : image à la une fixe sur les appareils modestes`.

### Task 8: Parcours complet de l'accueil à la télécommande

**Files:**
- Test: Create `T/ui/home/HomeTvNavigationTest.kt` (même montage que `HomeTvStructureTest` ; touches envoyées avec `performKeyInput`, voir `T/ui/settings/StalkerDialogFlowTest.kt` pour un hôte avec menu latéral et `ScreenFocusHost`)
- Modify: `K/ui/home/HomeScreen.kt` seulement si un test révèle un défaut

**Interfaces:**
- Consumes: les `testTag` des tâches 3 à 6 (`hero-watch`, `hero-info`, `hero-list`, `home-channel-<id>`, `top10-rank-<n>`) ; ajouter au besoin `home-row-<HomeSectionId>` sur le conteneur de chaque rangée et `home-card-<clé>` sur les affiches.

- [ ] **Step 1: Écrire les tests de parcours** (accueil complet : image à la une, rangée Direct, Top 10, deux rangées d'affiches ; français puis arabe pour le test 2) :
  1. `descente_complete` : ▼ répété depuis `hero-watch` passe par chaque rangée dans l'ordre affiché, un élément focalisé et entièrement visible à chaque pas ; ▼ sur la dernière rangée ne change rien.
  2. `ordre_horizontal` : dans une rangée, ▶ avance d'une carte à la fois jusqu'à « Tout voir » ou la dernière carte, où ▶ ne change rien ; ◀ revient carte par carte ; en arabe les directions sont inversées et cohérentes.
  3. `remontee` : ▲ depuis chaque rangée remonte à la rangée précédente puis à l'image à la une ; ▲ depuis `hero-watch` laisse le focus sur un élément visible.
  4. `memoire_de_rangee` : focus sur la 3e carte de la rangée Direct, ▼ puis ▲ : le focus revient sur la 3e carte, pas sur la première.
  5. `rien_de_decoratif` : sur tout le parcours des tests 1 à 3, le nœud focalisé n'est jamais `home-hero`, `home-clock`, un `top10-rank-<n>` ni un point de pagination.
  6. `donnees_qui_changent` : focus posé sur une affiche ; les rangées sont remplacées par une nouvelle liste contenant la même affiche (nouvelle instance de `HomeRows`) et l'image à la une change : le même élément reste focalisé.
  7. `retour_sur_l_accueil` : focus sur une affiche, l'accueil quitte la composition puis y revient (comme après une fiche, avec l'état sauvegardé) : le focus est sur la même affiche et `hero-watch` ne le reprend pas.
- [ ] **Step 2: Lancer** `--tests '*HomeTvNavigationTest*'`. Un test qui échoue révèle un vrai défaut de navigation.
- [ ] **Step 3: Corriger** chaque défaut dans `HomeScreen.kt` par le plus petit changement possible (`focusProperties`, `focusRestorer`, `focusGroup`, clé stable), en gardant les correctifs existants. Si un point ne peut pas être testé sous Robolectric, l'écrire dans le rapport avec ce qui garantit le comportement dans le code, plutôt qu'un test qui n'affirme rien.
- [ ] **Step 4: Lancer** `--tests '*HomeTv*' --tests '*HomeRenderTest*' --tests '*StalkerDialogFlowTest*'` → passe.
- [ ] **Step 5: Commit** — `Accueil TV : parcours à la télécommande vérifié par des tests`.

### Task 9: Mesure de fluidité, documents et PR

**Files:**
- Modify: `CHANGELOG.md` (section « Non publié »), `README.md`, `README.fr.md` (description de l'accueil si elle cite la grille de raccourcis), `docs/screenshots/{fr,en,ar}/home*.png`
- Vérifier : `android-native/app/src/main/baseline-prof.txt`

- [ ] **Step 1: Suite complète** — `./gradlew --offline -q :app:testDebugUnitTest` → 0 échec hors `I18nGoldenTest` (chiffres arabes seulement).
- [ ] **Step 2: Mesure avant / après.** Sur un même appareil ou émulateur Android TV, installer le debug de `main` puis celui de la branche. Pour chacun : démarrage à froid, 30 s de parcours de l'accueil (▼ jusqu'à la dernière rangée, ▶ au bout de deux rangées, retour en haut). Relever dans `adb logcat` les lignes du tag `perf` pour `home` (`frames=… janky=… slow=… p95≈…`) et toute ligne `perf stall … route=home`. Critère : ni le taux d'images lentes ni le p95 ne se dégradent, aucune nouvelle ligne `stall`. Si aucun appareil ni émulateur n'est disponible, s'arrêter et le dire : ce point ne se remplace pas par une estimation.
- [ ] **Step 3: Profil de démarrage.** Ouvrir `baseline-prof.txt` : s'il nomme des classes de `ui/home` supprimées ou renommées (par exemple `ShortcutGrid`), mettre les lignes à jour ; s'il ne contient que des règles par paquet, ne rien changer.
- [ ] **Step 4: Tous les thèmes.** Ajouter à `HomeRenderTest` un test qui rend l'accueil dans CHAQUE thème de `AppTheme` (focus sur une carte de la rangée Direct, puis sur une affiche) et écrit `home-tv-theme-<nom>.png` ; il échoue si un thème plante. Ouvrir chaque capture : texte lisible, élément focalisé évident, accent et anneau du thème, rien d'écrit en dur qui jure. Corriger tout défaut avant de continuer.
- [ ] **Step 4 bis: Captures.** Copier les captures de `HomeRenderTest` vers `docs/screenshots` (fr sombre et clair, en, ar).
- [ ] **Step 5: CHANGELOG et README**, puis commit — `Accueil TV : journal des modifications, captures`.
- [ ] **Step 6: PR** vers `main`, corps : ce qui change à l'écran, les comportements préservés (liste de la tâche 3), le tableau de mesure avant / après, les captures. Ne pas fusionner sans CI verte ; ne rien publier.
