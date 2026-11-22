# Interface façon streaming — lot 7 : TV en direct et Guide (TV, téléphone, tablette) — plan d'implémentation

État : brouillon avancé — modèle de focus, tâches 1 à 12 et tableaux d'erreurs écrits ; reste à faire : auto-relecture complète (vérifier chaque numéro de ligne cité sur l'arbre au moment de l'exécution, les tailles exactes du téléphone, les noms de tests remplacés dans `GuideRecompositionTest`).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** « TV en direct » (catégories, liste des chaînes, aperçu) et « Guide » (sans aperçu) refaits dans le langage des maquettes `Direct`, `ListeChaines` et `Guide`, sur TV puis au toucher, avec une navigation à la télécommande déterministe (la position est une donnée, jamais réparée après coup) et un Guide dont un déplacement ne coûte que la case quittée, la case atteinte et l'en-tête — 50e centile du zapping ▼ ▲ au niveau de l'accueil sur la box du propriétaire.

**Architecture:** Le Guide TV abandonne les cases focalisables : la grille est UN seul nœud focalisable, toujours composé, qui reçoit les touches et tient un curseur pur `GuideCursor` (chaîne, rang, heure d'ancrage, début du programme) ; un réducteur pur `GuideNav.reduce` décide de chaque touche ; l'affichage « case en surbrillance » est une lecture de ce curseur (au dessin), le défilement suit le curseur par `requestScrollToItem` sans animation. Le même principe sert la liste des chaînes du Direct (`LiveCursor` : catégorie, rang, clé de chaîne). Les ViewModels gardent leurs données (`GuideGridViewModel`, `GuideProgrammeCache`, `LiveViewModel`, `SharedLiveSession`) ; seules des lectures d'affichage sont ajoutées. L'aperçu vidéo du Guide et sa pastille disparaissent ; celui du Direct garde sa vidéo composée d'une chaîne à l'autre (même mécanisme que `GuidePreviewVideo`, 1c590586).

**Tech Stack:** Kotlin, Jetpack Compose (foundation `LazyColumn` / `LazyListState.requestScrollToItem`, `BasicText` avec `ColorProducer`), tv-material3, Hilt, Room + Paging 3, Coil 3, Media3 (aperçu du Direct, inchangé) ; tests Robolectric + compose-ui-test (`mainClock.autoAdvance = false` pour l'ordre des images).

**Spec:** `docs/superpowers/specs/2026-11-21-interface-streaming-design.md` (écrans 20 et 10, sections « Navigation à la télécommande », « Performance et fluidité », « Périmètre élargi le 2026-11-21 »). Maquettes (1920 × 1080, unités `design`) : `Direct.dc.html` (écran 20), `ListeChaines.dc.html` (écran 8, langage des lignes de chaîne, partagé avec le lot 5), `Guide.dc.html` (écran 10, SANS aperçu vidéo : décision du propriétaire, « dans le Guide nouveau design on n'a pas l'aperçu de la chaîne, on laisse sans aperçu »). Registre et relectures qui motivent les décisions : `.superpowers/sdd/2026-11-21-interface-streaming-lot-0-1-accueil-tv/` (`progress.md`, `guide-perf-analysis.md`, `guide-perf-review.md`, `guide-2-review.md`, `guide-3-review.md`, `retours-1-review.md`, `retours-3-review.md`, `analyse-logos.md` L4, `audit-erreurs-journaux.md` P5-P7).

Chemins abrégés : `K = android-native/app/src/main/kotlin/com/ultratv/tv/nativeapp`, `T = android-native/app/src/test/kotlin/com/ultratv/tv/nativeapp`, `R = android-native/app/src/main/res`. Commandes depuis `android-native/`. Test ciblé : `./gradlew --offline -q :app:testDebugUnitTest --tests '<motif>'`. Dépend du lot 2 pour `UiLog` / `uiAttempt` / `ImageFailureTally` (si le lot 7 passe avant, la tâche 1 les crée avec EXACTEMENT les signatures du plan du lot 2, tâche 2).

## Points à trancher par le propriétaire

Le plan applique la proposition ; chaque point est isolé et se défait seul.

1. **Mise en page de « TV en direct » sur TV.** La maquette `Direct` (écran 20) range les chaînes en rangées de vignettes par catégorie ; la liste actuelle (catégories à gauche → chaînes → aperçu), que vise votre règle de Retour, correspond à la maquette `ListeChaines`. **Proposé :** colonne de catégories et lignes de chaînes de `ListeChaines` (mêmes lignes que la liste des chaînes du lecteur, lot 5), et à droite l'aperçu de `Direct` (vidéo en haut, collée au bord, voilée vers la couleur de page ; dessous : DIRECT, numéro · nom, titre, progression, « Ensuite », boutons « Regarder en plein écran » et « Guide »). Alternative : rangées de vignettes de la maquette `Direct` (plus de défilement horizontal, 1 240 chaînes de « Toutes » sur une rangée). Tâches 7 et 8.
2. **Réglage « Aperçu » du Guide (pastille ajoutée en rc3).** Le nouveau Guide n'a pas d'aperçu : **proposé** de retirer la pastille et tout le code d'aperçu du Guide (`GuidePreviewPane`, `GuidePreviewVideo`, démarrage à l'ouverture, règles « un seul essai » et « son lié à l'image » propres au Guide). La préférence elle-même reste : c'est celle du Direct (Réglages › Lecture › « Aperçu vidéo dans le Direct »), inchangée. Tâche 3.
3. **Image du programme dans l'en-tête du Guide.** La maquette montre l'image du programme ; le guide (XMLTV, Xtream, Stalker) n'en fournit pas en base (`EpgEntity` n'a ni image ni icône, et le modèle de données est hors périmètre). **Proposé :** le logo de la chaîne en grand dans la zone de droite (décodé à 400 × 400 au plus), sans voile. Alternative : zone vide. Tâche 3.
4. **Chaînes sans programme ce jour-là.** **Proposé :** lignes compactes (40) « Pas de programme ce jour-là » que le curseur VISITE (un appui chacune) ; OK y lance la chaîne. Alternative : le curseur les saute (◀ moins d'appuis, mais une chaîne sans guide ne peut plus être lancée depuis le Guide). Tâche 2.
5. **Défilement vertical sans animation dans le Guide et la liste du Direct** (premier levier de fluidité identifié par trois relectures). **Proposé :** par défaut sur tous les appareils ; le réglage caché `guide_instant_scroll` disparaît. Tâches 2 et 7.
6. **Retour dans le Guide :** **proposé** d'ouvrir directement le menu latéral (votre règle « autres écrans principaux »), après fermeture d'une fenêtre ouverte ; ◀ à la butée du temps (appui neuf seulement) ouvre aussi le menu, comme aujourd'hui. Tâche 4.

## Global Constraints

Conditions d'acceptation de CHAQUE tâche (exigences du propriétaire, recopiées, complétées pour ce lot) :

- « Tous les écrans, toute l'application, sans la moindre erreur, pour tous les appareils » : box et téléviseurs Android TV / Google TV / Fire TV à la télécommande, téléphones et tablettes au toucher (portrait et paysage, clavier ou manette), du plus modeste (2 Go, 32 bits, Android 7.1) au plus récent.
- Chaque tâche produit un changement VISIBLE et livrable seul dans une pré-version (« Tous les écrans faits ou pas encore ? … je ne vois aucun changement ») ; seule exception : la tâche 1 (outillage), livrée dans la même pré-version que la tâche 2.
- Aucune anomalie, aucun plantage : clés uniques dans toutes les listes paresseuses (`uniqueKeys` / `rememberUniqueKeys` de `K/ui/common/SafeKeys.kt`, `rememberSafeItemKey` pour les listes paginées), aucun index hors bornes (liste vide, à un élément, placeholder de pagination), aucun `FocusRequester` non attaché (toute demande passe par une cible dont l'attache est connue, ou `runCatching { requestFocus() }` journalisé).
- **Navigation à la télécommande logique et stable, par construction :** la position est une donnée ; il existe toujours un élément focalisable ; AUCUNE réparation différée (`LaunchedEffect`, `view.post`, `withFrameNanos`, `delay`) ne déplace le focus — vérifié par `grep` à la tâche 12 dans `K/ui/guide` et `K/ui/live` ; le focus initial est donné une fois par visite par la cible d'entrée de `ScreenFocusHost` (`screenEntryTarget()`) ; ▲ ▼ changent de ligne, ◀ ▶ parcourent ; ◀ au début d'une rangée ouvre le menu latéral, ▶ depuis le menu revient sur l'élément quitté ; Retour ferme d'abord ce qui est ouvert, puis : liste des chaînes → catégorie en cours (même hors écran), catégories → menu latéral ; Guide → menu latéral (`K/ui/components/RailBack.kt`) ; arabe : ◀ ▶ miroités, ordre visuel respecté.
- **Performance « au top & fluidité »** : un déplacement dans le Guide ne recompose et ne remesure que la case quittée, la case atteinte et l'en-tête (plafonds de la sonde, tâche 1) ; aucune animation de défilement ; aucun `FocusSurface` par case du Guide ; aucun état lu en haut d'écran qui change à chaque touche ; images décodées à taille explicite (logos 96 × 96 dans les lignes, 400 × 400 dans l'en-tête) ; travail de données hors fil principal ; objectif chiffré sur la box (tâche 11).
- Les 10 thèmes conservés (DARK, LIGHT, AUTO, OLED, MIDNIGHT, FOREST, PLUM, SAND, CONTRAST, TEAL) : jetons `Ux.*`, aucune couleur en dur hors de l'exception « posé sur une image ou la vidéo » (voile `#0A0C10`, encres `ImageInk`, boutons `OnImageButton`) ; anneau de focus du thème partout ailleurs.
- Textes : en, fr, es, ar écrits à la main (`R/values{,-fr,-es,-ar}/strings.xml` + golden `T/../resources/i18n-golden/{en,fr,es,ar}.json`), autres langues par Weblate ; arabe : chiffres latins 1 2 3 PARTOUT (heures, compteurs, numéros de chaîne, « 42 min restantes ») par `DisplayLocale` / `DisplayFormats` ; aucun texte en dur ; aucun texte tronqué dans une zone de hauteur fixe (`NoTextWrap`, vérifié à la taille de texte 1,3).
- Gestion d'erreur et journaux (« une bonne gestion d'erreur, des logs complets », « jamais un écran mort ») : chargement, vide, erreur, synchro en cours dessinés avec un message et une action ; aucun `runCatching` / `catch` muet dans les fichiers touchés (`uiAttempt` / `UiLog.failure(écran, action, cause)`) ; `CancellationException` relancée ; jamais d'identifiant, de mot de passe ni d'adresse de flux complète dans un journal (`UtvLog.maskUrl`, `LogSanitizer`).
- Zéro régression : ViewModels, dépôts et DAO inchangés sauf ajout nécessaire à l'affichage ; préférences jamais réinterprétées ; correctifs passés conservés (tableau de la tâche 1).
- Jamais de mention d'une appli concurrente (code, commentaires, commits, CHANGELOG) ; jamais de ligne `Co-Authored-By` ni `Claude-Session` dans les commits.
- `I18nGoldenTest` vert sous JDK 17 et 27 ; tests avec le tas par défaut de 2 Go (573d45b6).
- Le lot se termine par : suite complète verte, captures, CHANGELOG, mesure sur la box, liste de contrôle sur appareil (tâche 12).

## Modèle de focus du Guide (décision centrale, tâche 2)

Zones focalisables, toujours composées : `CHIPS` (rangée des jours et catégories, vraies `FocusSurface`), `ACTIONS` (boutons de l'en-tête, vraies `FocusSurface`), `GRID` (UN nœud `focusable()`, `testTag("guide-grid")`, qui porte `screenEntryTarget()`). Les cases et le message « aucun programme » ne sont PAS focalisables : ce sont des dessins du curseur.

```kotlin
// K/ui/guide/GuideCursor.kt (pur)
@Immutable data class GuideCursor(
    val rank: Int,                 // rang de la ligne dans la liste paginée (0..)
    val channelKey: String?,       // channelKey(c) une fois la ligne chargée ; null tant que placeholder
    val anchorMs: Long,            // heure « mémorisée » : ▲ ▼ visent le programme qui la couvre
    val progStart: Long?,          // début du programme désigné (clé stable à travers les réimports), null = ligne sans programme
    val message: Int = -1,         // ≥ 0 : la grille est vide, index de l'action virtuelle du message
)
sealed interface GuideMove {
    data class Cursor(val c: GuideCursor) : GuideMove
    data class Shift(val c: GuideCursor, val windowStart: Long) : GuideMove   // décalage de fenêtre (bord)
    data class Day(val c: GuideCursor, val windowStart: Long, val day: Int) : GuideMove
    data object ToActions : GuideMove      // ▲ depuis la première ligne, en-tête avec actions
    data object ToChips : GuideMove        // ▲ depuis la première ligne sans action, ou depuis le message
    data object OpenMenu : GuideMove       // ◀ (▶ en arabe) à la butée, appui neuf
    data object Consumed : GuideMove       // butée, rien ne bouge, touche avalée
    data class Activate(val c: GuideCursor) : GuideMove   // OK
}
object GuideNav {
    fun reduce(c: GuideCursor, key: GuideKey, rows: GuideRows, window: Long, rtl: Boolean, freshPress: Boolean, nowMs: Long): GuideMove
    fun resolve(c: GuideCursor, rows: GuideRows, window: Long): GuideCell?   // ce qui est dessiné en surbrillance
    fun initial(rows: GuideRows, window: Long, nowMs: Long): GuideCursor     // première ligne qui a une case à nowMs, sinon rang 0
}
enum class GuideKey { UP, DOWN, LEFT, RIGHT, OK, LONG_OK, REWIND_DAY, FORWARD_DAY }
// GuideRows : vue pure de ce que l'écran sait au moment de la touche — taille, clé de chaîne par rang (null = placeholder),
// programmes lus par chaîne (null = pas encore lus), « rien ce jour-là » par chaîne.
```

Transitions (`reduce`, ◀ ▶ donnés en sens de lecture : en arabe la touche physique est miroitée avant l'appel) :

| Touche | Cas | Résultat |
|---|---|---|
| ▼ | rang < taille − 1 | rang + 1, même `anchorMs` ; `progStart` = programme de la nouvelle ligne qui couvre `anchorMs`, sinon le plus proche dans la fenêtre, sinon null (ligne compacte ou vide) |
| ▼ | dernière ligne | `Consumed` |
| ▲ | rang > 0 | rang − 1, mêmes règles |
| ▲ | rang 0 | `ToActions` si l'en-tête a au moins une action, sinon `ToChips` |
| ▶ (avant) | programme suivant dans la fenêtre | `progStart` = son début, `anchorMs` = max(son début, fenêtre) |
| ▶ | suivant hors fenêtre (données présentes) | `Shift` par `guideEdgeShift(window, true, focused, neighbour)` (trou ≥ 3 h compris), curseur sur le voisin |
| ▶ | aucun suivant dans les données lues de la chaîne | `Shift` borné qui garde le programme visible, puis `Consumed` (jamais de fenêtre vide) |
| ◀ (arrière) | symétrique | idem ; à la butée (rien de plus tôt ET plage lue commençant avant le début du jour) : `OpenMenu` si `freshPress`, sinon `Consumed` (corrige Mn-C) |
| ◀ ▶ | ligne sans programme | `Consumed` (aucun saut vers une autre chaîne) |
| ⏪ ⏩ | — | `Day(day ∓ 1)`, `anchorMs` = même heure l'autre jour, même chaîne ; jour borné à [−1, +2] sinon `Consumed` |
| OK / OK long | case | `Activate` (fenêtre d'actions du programme / rappel) ; ligne sans programme → regarder la chaîne |
| toute touche | `message ≥ 0` | ◀ ▶ changent l'action virtuelle (bornées), OK l'exécute, ▲ → `ToChips`, ▼ `Consumed` |

Cas limites (chacun a son test, tâche 2) :
- **Grille vide** (aucune chaîne, ou aucune case dans la fenêtre pour toutes les lignes lues) : le nœud reste focalisable, dessine le message et ses actions virtuelles (`message = 0`) ; quand des données arrivent, `message` repasse à −1 et la surbrillance se pose par `initial` — **le focus ne bouge pas** (il est déjà sur le nœud).
- **Ligne sans programme / compacte** : surbrillance de toute la ligne, `progStart = null`.
- **Bord de fenêtre** : décalage décidé dans la touche, fenêtre affichée = demandée dès que les programmes des lignes visibles la couvrent (`readyFor`, existant) ; tant qu'elle ne l'est pas, l'ancienne reste affichée avec le curseur.
- **Minuit, changement de jour** : la liste des chaînes ne dépend pas du jour (6906ddea, gardé) ; la pastille du jour est DÉRIVÉE de la fenêtre affichée (`dayOffsetOf(shownWindow, nowMs)`), jamais stockée (corrige Mn-1, Mn-E).
- **Synchronisation en cours / réimport** : les programmes changent d'identifiant, pas de début ; `progStart` désigne toujours le même programme ; programme disparu → `resolve` prend celui qui couvre `anchorMs`.
- **Placeholder de pagination** : le curseur peut viser un rang non chargé (`channelKey = null`) ; la ligne squelette est en surbrillance ; la clé est complétée à l'arrivée (donnée, pas focus).
- **Changement de catégorie** : curseur = `initial` de la nouvelle liste (une fois, à l'arrivée de la première page), `channelKey` conservé si la chaîne y figure (lecture `rankOf` dans la page chargée, sinon rang 0).
- **Retour du lecteur / mort du processus** : `GuideCursor`, fenêtre, jour et catégorie sont dans le `SavedStateHandle` du ViewModel ; le `focusRestorer` de `ScreenFocusHost` rend le nœud qui avait le focus (grille, action ou pastille) ; la surbrillance est recalculée depuis les données — aucune nouvelle demande de focus.
- **Arabe** : `reduce` reçoit des directions logiques ; placement des cases par `placeRelative` (inchangé).
- **Touche maintenue** : `freshPress = repeatCount == 0` ; une touche maintenue n'ouvre jamais le menu ; une seule décision par événement `KeyDown`.

Défilement : à chaque `Cursor`, si la ligne n'est pas entièrement visible, `listState.requestScrollToItem(rank − 1 coerceAtLeast 0)` dans le même appui (aucune animation, aucune image intermédiaire). Ce que lit le dessin : `cursorState.value` dans `drawBehind` / `ColorProducer` de la case → les cases non concernées ne sont ni recomposées ni remesurées.

## Review Focus

1. ⏪ vers « Hier » purgé puis OK sur « Revenir à maintenant » avec la vidéo du Direct partagée encore active (ordre des images différent sur la box) : le focus reste sur la grille, la surbrillance sur la case de maintenant. → test tâche 2 (`message_puisDonnees_focusResteSurLaGrille_horlogeManuelle`).
2. Retour du lecteur après « Regarder » depuis l'en-tête sur la chaîne 40 : focus sur « Regarder », surbrillance sur la case de la chaîne 40, aucune autre demande de focus ni ouverture de flux. → test tâche 10 (`guide_retourDuLecteur_surRegarder_curseurGarde`).
3. Catégorie de 300 chaînes, ▼ maintenu jusqu'en bas pendant que les pages arrivent : jamais de focus perdu, jamais de saut, la dernière ligne atteinte ; aucune image longue due à une recherche 2D. → test tâche 2 (`basMaintenu_traversePlaceholders_finSurDerniereLigne`).
4. Direct : retour du plein écran sur la 30e catégorie, chaîne 120 : focus sur la chaîne 120, puis Retour → catégorie 30 visible et focalisée, puis Retour → menu sur « TV en direct ». → test tâche 7 (`direct_retourPleinEcran_puisRetourRetour`).
5. Guide en arabe, taille de texte 1,3, titre de 120 caractères : boutons de l'en-tête entiers, description sur 2, 1 ou 0 ligne, jamais coupée ; heures en chiffres latins. → test tâche 3 (`entete_arabe_police13_titreLong`).

---

### Task 1: Instrument de mesure et filet de non-régression (Guide, Direct)

Seule tâche sans changement visible ; livrée avec la tâche 2.

**Files:**
- Modify: `K/ui/guide/GuideProbe.kt` (ajouts : `chips`, `actions`, `scrollJumps`)
- Create: `K/ui/live/LiveProbe.kt` (même schéma : `rows: HashMap<String, Int>`, `categories`, `preview`, `screen`, `videoHosts` = attaches / détaches de la vue vidéo)
- Create: `T/ui/guide/GuideHarness.kt` (hôte TV 1920 × 1080 et téléphone `390×844`, `844×390`, `800×1280` ; faux ViewModel : liste paginée simulée de N chaînes, programmes publiés de façon ASYNCHRONE avec délai réglable, horloge figée injectable, langue, thème, taille de texte)
- Create: `T/ui/live/LiveHarness.kt` (même principe : 60 catégories, liste paginée, faux `SharedLiveSession`)
- Create: `T/ui/guide/GuideRegressionTest.kt`, `T/ui/live/LiveRegressionTest.kt`
- Create: `android-native/tools/mesure-navigation.sh` (protocole de la tâche 11, sans effet sur l'appli)
- Test existants gardés : `T/ui/guide/*` (12 fichiers), `T/ui/live/*` (5 fichiers), `T/ui/player/engine/SharedLiveSession*`

**Interfaces:**
- Produces: `internal fun ComposeContentTestRule.setGuide(tv: Boolean = true, width: Int = 1920, height: Int = 1080, lang: AppLang = AppLang.French, theme: AppTheme = AppTheme.DARK, fontScale: Float = 1f, data: FakeGuideData, probe: GuideProbe? = null)` ; `class FakeGuideData(channels: Int, programmesDelayMs: Long = 0, nothingThatDay: Set<Int> = emptySet())` ; idem `setLive(...)`, `FakeLiveData(categories: Int, channelsPerCategory: Int)`.
- Produces (outil) : `tools/mesure-navigation.sh <paquet> <touche> [appuis=8] [espacement=0.5]` — vérifie `cmd package compile -m speed-profile -f` et `status=speed-profile` (`dumpsys package dexopt`), lance `dumpsys gfxinfo <paquet> reset`, envoie les appuis par `input keyevent`, écrit les centiles 50 / 90 / 95 / 99, le nombre d'images et le taux d'images lentes.

Comportements à préserver (tests sur le code ACTUEL, verts avant toute modification) :

| Comportement (correctif d'origine) | Test qui le garde |
|---|---|
| Fenêtre de 3 h en créneaux de 30 min, `guideWindowStart`, `normalizeRow` (chevauchements), `slotFor` | `GuideGridTest` (existant) |
| Bord : trou ≥ 3 h sauté, butée sans fenêtre vide (`guideEdgeShift`) | `GuideGridTest.edgeShift_*` (existant) |
| Cache par plage, génération, éviction 200 chaînes, rien d'affiché retiré | `GuideProgrammeCacheTest` (existant) |
| Liste des chaînes indépendante du jour et de minuit (6906ddea) | `GuideMidnightTest` (existant) |
| Groupe : même liste que le Direct, mémorisé par profil | `GuideGroupsTest` (existant) |
| Jamais un chargement sans fin (`guideLoadState`) | `GuideLoadStateTest` (existant) |
| Actions d'un programme (Regarder, Revoir, Rappel, Enregistrer) selon passé / en cours / futur | `ProgramActionButtonTest` (existant) |
| Chaîne verrouillée jamais lue en aperçu (Direct) ; aperçu arrêté en arrière-plan ; jamais deux flux | `LivePreviewPolicyTest` (existant) |
| Direct : catégorie d'arrivée et chaînes « séparateur » écartées | `LandingAndJunkTest` (existant) |
| Direct : Retour → catégorie en cours même hors écran (f89eaf62) | `LiveBackToCategoriesTest` (existant) |
| Clés de liste départagées (8bbd6b62) | `DrawerDuplicateKeysTest`, `SafeKeysTest` (existants) |
| Ligne de télémétrie de l'aperçu sans adresse ni nom de chaîne | `GuideRegressionTest.journalApercu_sansAdresse` (nouveau, déplacé vers le Direct à la tâche 3) |

- [ ] **Step 1: Écrire les hôtes, la sonde et les tests du tableau**, plus un relevé de référence `GuideRegressionTest.releve_avant` qui imprime (sans affirmer) les compteurs actuels de ▶, ▼, ▼ avec ligne entrante, bord de fenêtre, arrivée de programmes — chiffres recopiés dans la PR.
- [ ] **Step 2: Lancer** `--tests '*ui.guide*' --tests '*ui.live*' --tests '*SharedLive*'` → vert. Un rouge révèle un défaut préexistant : le consigner, le corriger dans la tâche qui touche ce code.
- [ ] **Step 3: Mesure « avant » sur la box** (protocole de la tâche 11) avec la dernière pré-version : chiffres dans `scratchpad/mesure-lot7-avant.txt` puis dans la PR.
- [ ] **Step 4: Commit** — `Guide et Direct : sonde de recompositions, hôtes de test et filet de non-régression avant la refonte`.

### Task 2: Guide TV — grille à curseur (modèle de focus unique), maquette de la grille

**Files:**
- Create: `K/ui/guide/GuideCursor.kt` (`GuideCursor`, `GuideMove`, `GuideKey`, `GuideRows`, `GuideCell`, `GuideNav`, `dayOffsetOf`)
- Create: `K/ui/guide/GuideGrid.kt` (grille TV : nœud focalisable, lignes, cases dessinées, ligne de l'heure, échelle)
- Modify: `K/ui/guide/GuideGridScreen.kt` (suppression de `GuideFocusPlan`, du rattrapage `rescue`, de `inKey` / `view.post`, du `RequestInitialFocus` de la grille, de `GuideCell` focalisable, de `GuideScrollSpec` ; `GuideTvContent` reçoit le curseur), `K/ui/guide/GuideDebug.kt` (supprimé), `K/MainActivity.kt` (lecture de l'extra `guide_instant_scroll` retirée)
- Modify: `GuideGridViewModel` : `cursor: StateFlow<GuideCursor?>` + `setCursor(c)` adossés au `SavedStateHandle` (clé `guide_cursor`, sérialisé en `LongArray`/`String`)
- Test: Create `T/ui/guide/GuideNavTest.kt` (JVM pur), Create `T/ui/guide/GuideCursorFocusTest.kt` (compose-ui-test), Modify `T/ui/guide/GuideRecompositionTest.kt`

**Interfaces:**
- Consumes: `GuideHarness`, `GuideProbe` (tâche 1) ; `guideEdgeShift`, `slotFor`, `normalizeRow`, `guideNothingThatDay` ; `screenEntryTarget()` ; `rememberSafeItemKey`, `peekOrNull`, `getOrNull` ; `LocalRailMenu`.
- Produces: les signatures du « Modèle de focus » ci-dessus ; `@Composable internal fun GuideGrid(rowsCount: Int, rowAt: (Int) -> ChannelEntity?, progsFor: (Long) -> List<EpgEntity>?, cursor: State<GuideCursor?>, shownWindow: State<Long>, clock: State<Long>, onKey: (GuideKey, freshPress: Boolean) -> Boolean, listState: LazyListState, focusUp: FocusRequester, modifier: Modifier)` ; `testTag` `"guide-grid"`, `"guide-row-<rang>"`, `"guide-cell-<channelId>-<startMs>"`, `"guide-cell-on"` (case en surbrillance), `"guide-message"`, `"guide-message-action-<i>"`.

Valeurs (maquette `Guide`) : grille à 72 du bord, haut 544, lignes de 92 (`designT`), écart 10 ; cellule de chaîne 288, rayon 14, fond `Ux.Card2`, numéro Sora Bold 24, logo 52 rayon 10 (`decodeSize` 104 × 104), nom Bold 20 / 24 deux lignes ; écart 12 ; cases rayon 14, padding 12 / 18, titre Bold 22 une ligne, horaire SemiBold 18, barre de progression 5 rayon 3 (programme en cours) ; fond : en cours `Ux.NowTint`, autres `Ux.Card`, surbrillance `Ux.Cta` + texte `Ux.TextOnLight` + anneau du thème 4 séparé par un liseré de la couleur de page (maquette : `0 0 0 4px page, 0 0 0 8px anneau`) ; échelle des heures à 500, 6 × 258, Bold 20 `Ux.Text3` ; repère de l'heure 3 × hauteur de grille `Ux.Accent` et pastille « 01:31 » ExtraBold 18 (`DisplayFormats`), dessinés au dessin depuis `clock` ; ligne compacte 40, nom `Ux.Text3`, mention `D.guideNoProgrammeDay` (existant).

Règles de construction : le nœud grille est `Modifier.focusable().onPreviewKeyEvent { … }` : traduit l'événement en `GuideKey` (◀ ▶ miroités en RTL), appelle `GuideNav.reduce` avec un `GuideRows` construit À LA TOUCHE (lectures des listes du moment, jamais d'état composé), applique le `GuideMove` (curseur, fenêtre, jour, `focusUp.requestFocus()` pour `ToActions`/`ToChips`, `railMenu?.open()` pour `OpenMenu`) ; `focusProperties { up = focusUp; left = Cancel; right = Cancel; down = Cancel }` (le nœud décide seul) ; cases = `Box` dessinés, AUCUN `FocusSurface`, AUCUN `combinedClickable` (la sémantique d'accessibilité du nœud décrit la case courante : chaîne, titre, horaire, état) ; texte de case par `BasicText(color = ColorProducer)` lisant le curseur au dessin ; `TitleCleaner` calculé hors composition (dans le ViewModel, une fois par chaîne).

Plafonds de la sonde (assertions de `GuideRecompositionTest`, remplaçant les anciennes) :

| Geste | écran | lignes | cases (contenu) | mesures de cases | en-tête | pastilles |
|---|---|---|---|---|---|---|
| ▶ ◀ dans la fenêtre | 0 | 0 | ≤ 2 | 0 | ≤ 1 | 0 |
| ▼ ▲ sans ligne entrante | 0 | 0 | ≤ 2 | 0 | ≤ 1 | 0 |
| ▼ ▲ avec ligne entrante | 0 | ≤ 1 | ≤ 2 + cases de la ligne entrante (≤ 8) | ≤ 8 | ≤ 1 | 0 |
| décalage au bord | 0 | ≤ lignes visibles (≤ 8) | ≤ 2 par ligne (entrantes) | ≤ cases visibles (≤ 64) | ≤ 1 | 0 |
| arrivée des programmes d'une chaîne | 0 | 1 | cases de cette ligne | ses cases | 0 (sauf ligne du curseur : 1) | 0 |
| minute qui passe | 0 | 0 | 0 | 0 | 0 | 0 |

- [ ] **Step 1: Tests JVM qui échouent** (`GuideNavTest`) : une fonction par ligne du tableau des transitions et par cas limite — `bas_memeAncrage_caseQuiCouvre`, `bas_ligneSansProgramme_progStartNull`, `bas_derniereLigne_consomme`, `haut_rang0_versActions_ouPastilles`, `droite_suivantDansFenetre`, `droite_bord_decalage`, `droite_trou5h_sauteSurMemeChaine`, `droite_finDesDonnees_decalageBorneSansFenetreVide`, `gauche_butee_appuiNeuf_menu`, `gauche_butee_toucheMaintenue_consomme`, `gauche_buteeMaisSoireeLaVeille_pasDeMenu` (Mn-C), `jour_suivant_memeHeureMemeChaine`, `jour_bornes_consomme`, `reimport_nouveauxIds_memeProgramme`, `placeholder_curseurSurRangNonCharge`, `initial_premiereLigneAvecCase`, `initial_grilleVide_message0`, `message_gaucheDroiteBornes_okExecute`, `jourDerive_minuit_etHeureDEte`, `arabe_directionsLogiques` (le miroir est fait par l'appelant : test sur la traduction des touches).
- [ ] **Step 2: Tests compose qui échouent** (`GuideCursorFocusTest`) : `ouverture_focusSurLaGrille_avantLesDonnees` (programmes à 2 s : le nœud a le focus dès la première image, surbrillance à l'arrivée, `assertIsFocused` inchangé) ; `message_puisDonnees_focusResteSurLaGrille_horlogeManuelle` (`mainClock.autoAdvance = false`, Review Focus n° 1) ; `basMaintenu_traversePlaceholders_finSurDerniereLigne` (Review Focus n° 3) ; `quatreDirections_depuisChaqueBord` ; `haut_versActions_puisBas_retourMemeCase` ; `menuLateral_allerRetour_memeCase` ; `reimportPendantFocus_memeCase` ; `minuit_pendantFocus_pastilleDuJourSuit` ; `ligneCompacte_visitee_okRegarde` ; `arabe_parcours_miroir` ; `defilement_sansAnimation_uneImage` (après ▼ sur la dernière ligne visible : la ligne est entièrement visible à l'image suivante, `mainClock.advanceTimeByFrame()` une fois). `GuideRecompositionTest` : plafonds du tableau.
- [ ] **Step 3: Constater l'échec** — `--tests '*GuideNav*' --tests '*GuideCursorFocus*' --tests '*GuideRecomposition*'`.
- [ ] **Step 4: Implémenter.** Supprimer le code de rattrapage listé (aucun appelant restant) ; `grep -n "LaunchedEffect\|withFrameNanos\|view.post\|RequestInitialFocus" K/ui/guide/GuideGrid.kt` → aucun résultat.
- [ ] **Step 5: Lancer** `--tests '*ui.guide*'` → vert ; captures `guide-tv-grille-fr.png`, `-ar`, `-light` regardées.
- [ ] **Step 6: Commit** — `Guide TV : grille à curseur, un seul élément focalisable, position gardée par donnée, défilement sans animation`.

### Task 3: Guide TV — en-tête du programme (maquette), sans aperçu ; retrait de la pastille « Aperçu »

**Files:**
- Modify: `K/ui/guide/GuideGridScreen.kt` (`ProgramInfo`, `ProgramButtons`, suppression de `GuidePreviewPane`, `GuidePreviewVideo`, `NO_PREVIEW_PREFS`, `NO_SESSION_STATE`, `previewLabel`, `onPreviewSetting`, `previewChoice`, `firstChannel`)
- Modify: `K/ui/player/engine/SharedLiveSession.kt`, `PlaybackSession.kt` : `previewMode` et `gateAudioOnPicture` restent (le Direct les reprend à la tâche 8) mais ne sont plus posés par le Guide
- Modify: `R/values{,-fr,-es,-ar}/strings.xml` (retrait de `dg_preview_chip` des 4 langues écrites à la main seulement si aucun appelant ; golden)
- Test: Modify `T/ui/guide/GuideHeaderTest.kt` ; supprimer `GuidePreviewVideoTest.kt`, `GuidePreviewSettingTest.kt` (fonction retirée, raison dans la PR) ; Create `T/ui/guide/GuideHeaderActionsTest.kt`

**Interfaces:**
- Consumes: `GuideCursor` / `GuideNav.resolve` (tâche 2), `programActionState`, `ReplayUrls.availability`, `ProbeHeader`, `ProbeButtons`, `LogoBox(decodeSize)`, `PillButton`, `NoTextWrap`, `DisplayFormats`.
- Produces: `internal fun guideHeaderActions(state: ProgramActionState, isNow: Boolean): List<GuideAction>` (pur) avec `enum class GuideAction { WATCH, FROM_START, REMIND, RECORD, REPLAY }` ; en cours : WATCH, FROM_START (si replay disponible), RECORD ; futur : WATCH (la chaîne), REMIND, RECORD ; passé : REPLAY (si disponible), WATCH ; `internal fun descLines(textScale: Float, isNow: Boolean): Int` (pur, 0..2, budget de `guide-perf-analysis.md` C3.2) ; `testTag` `"guide-action-<nom>"`.

Valeurs (maquette) : bloc à 72 / 56, largeur 1000, écart 14 : intitulé Bold 20 espacement 2 `Ux.Text2` « MAINTENANT · CHAÎNE · 00:24 – 02:13 » (capitales sauf arabe) ; titre Sora ExtraBold 64 / 70, `letterSpacing` −1, UNE ligne avec « … » ; progression 520 × 8 + « 42 min restantes » Bold 22 (programme en cours seulement) ; description Medium 24 / 34 sur `descLines` lignes ; boutons 68, écart 14 : premier `PillButton(bg = Ux.Cta, iconPath = Icons.Play)`, autres `PillButton` ; zone de droite 1000 × 400 : logo de la chaîne du curseur (point n° 3), 200 de haut, centré, `decodeSize` 400 × 400. Hauteur de l'en-tête FIXE (aucune remesure de la grille quand le titre change).

Focus de l'en-tête : la liste d'actions est FIGÉE tant que l'en-tête a le focus (`remember` sur l'entrée du focus) : aucun bouton ne disparaît sous le focus quand un programme se termine ; l'action exécutée relit `programActionState` au clic et explique par un message (`Toaster`) si elle n'est plus possible. ▼ → grille (même case), ▲ → pastille du jour sélectionné, ◀ depuis la première action → menu latéral, ▶ ◀ entre actions.

- [ ] **Step 1: Tests qui échouent** : `guideHeaderActions_*` (3 états × replay oui / non) ; `descLines_bornes` ; `entete_arabe_police13_titreLong` (Review Focus n° 5) ; `entete_actionsFigeesPendantLeFocus` (programme qui se termine pendant le focus sur « Me rappeler » : le bouton reste, clic → message) ; `entete_unSeulRecompositionParDeplacement` (sonde : `header ≤ 1`, `buttons == 0` entre deux programmes futurs) ; `aucunApercu_aucunFluxOuvert` (faux `SharedLiveSession` : `preview` jamais appelé en ouvrant et en parcourant le Guide) ; `pastilleApercu_absente`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.** Ouvrir le Guide arrête un aperçu du Direct encore actif (`shared.stopPreview()` à l'entrée, connexion unique).
- [ ] **Step 4: Lancer** `--tests '*ui.guide*' --tests '*SharedLive*' --tests '*I18n*'` → vert.
- [ ] **Step 5: Commit** — `Guide TV : en-tête du programme façon maquette, sans aperçu vidéo, actions stables sous le focus`.

### Task 4: Guide TV — rangée des jours et des catégories, Retour

**Files:**
- Modify: `K/ui/guide/GuideGridScreen.kt` (`DayChip`, `CategoryChip` → `GuideChipsRow` dans un nouveau `K/ui/guide/GuideChips.kt`)
- Test: Create `T/ui/guide/GuideChipsTest.kt`

**Interfaces:**
- Consumes: `dayOffsetOf` (tâche 2), `guideGroupOptions`, `DirectCategory`, `formatCount`, `rememberUniqueKeys`, `RailMenuState`, `ContentFocusMemory`.
- Produces: `@Composable internal fun GuideChipsRow(days: List<GuideDay>, shownDay: Int, groups: List<DirectCategory>, group: String, onDay: (Int) -> Unit, onNow: () -> Unit, onGroup: (String) -> Unit, gridFocus: FocusRequester, selectedChip: FocusRequester)` ; `data class GuideDay(val offset: Int, val label: String)` ; `testTag` `"guide-day-<offset>"`, `"guide-now"`, `"guide-group-<id>"`.

Valeurs : rangée à 72 / 420, `LazyRow` paresseuse, pastilles 56, padding 24, rayon 28, Bold 22, écart 10 ; « Hier », « Aujourd'hui », « Demain », nom du jour J+2 (mémorisé par jour calendaire et langue), « Maintenant », puis 34 d'écart et les catégories « Favoris », « Tout · 1 240 », « Sport · 143 »… (compteurs `formatCount`, chiffres latins) ; sélectionnée : `Ux.Text` fond / `Ux.Bg` encre (inversion, lisible dans les 10 thèmes, vérifié tâche 12), autres `Ux.Surface2` / `Ux.Text` ; focus : anneau du thème. Le dialogue de choix de catégorie (`groupOpen`) reste au toucher seulement.

Navigation : ▼ depuis toute pastille → grille (curseur inchangé) ; ◀ depuis la première pastille → menu latéral ; ▶ depuis le menu → élément quitté ; choisir une catégorie : liste rechargée, focus RESTE sur la pastille, curseur `initial` à l'arrivée de la première page ; « Maintenant » : fenêtre de maintenant, curseur à `nowMs` sur la même chaîne. Retour (toute zone du Guide, aucune fenêtre ouverte) → menu latéral sur « Guide » (point n° 6, `RailBackHandler` existant ; vérifier que le Guide n'enregistre plus aucun `BackHandler` propre).

- [ ] **Step 1: Tests qui échouent** : `pastilleDuJour_deriveeDeLaFenetre_apresMinuit` ; `categorie_focusResteSurLaPastille_curseurInitialALArrivee` ; `maintenant_memeChaine` ; `gaucheDepuisPremiere_menu` ; `retour_depuisGrilleActionsPastilles_menu` ; `retour_fenetreOuverte_fermeSeulement` ; `centCategories_toutesAtteignables_clesUniques` (deux catégories de même nom et même identifiant) ; `arabe_miroir_chiffresLatins`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer** `--tests '*ui.guide*' --tests '*RailBack*'` → vert.
- [ ] **Step 5: Commit** — `Guide TV : jours et catégories en pastilles, jour suivi après minuit, Retour vers le menu latéral`.

### Task 5: Guide — états vides, erreur, synchronisation, journaux

**Files:**
- Modify: `K/ui/guide/GuideGrid.kt` (message dans le nœud grille), `K/ui/guide/GuideLoadState.kt`, `GuideGridViewModel` (les `runCatching` de `playLive`, lecture des programmes → `uiAttempt`)
- Modify: strings + golden (textes nouveaux seulement s'il en faut ; réutiliser `D.guideNoDataYet`, `D.guideNoProgrammeDay`, `S.sync.messageFor`)
- Test: Create `T/ui/guide/GuideStatesTest.kt`, `T/ui/guide/GuideLogTest.kt`

Erreurs du Guide → ce que voit l'utilisateur → ce qui est journalisé (écran `guide`) :

| Cas | À l'écran | Journal |
|---|---|---|
| Aucune source active | message « Ajoutez une source » + action virtuelle « Ouvrir les sources » ; pastilles actives | rien (pas une erreur) |
| Guide jamais téléchargé, synchro en cours | ligne de progression dans l'en-tête (`guideProgressLine`) ; grille avec les chaînes et squelettes | rien |
| Guide en échec (réseau, délai, identifiants, réponse illisible) | message `S.sync.messageFor(kind)` + « Réessayer » (relance `retryEpg` de la source active) | `UiLog.failure("guide", "epg", cause)` une fois par échec (la synchro journalise déjà le détail sous `UtvSync`) |
| Catégorie sans chaîne avec guide | message + « Toutes les chaînes » | rien |
| Aucun programme dans la fenêtre (jour purgé « Hier », « +2 » vide) | message + « Revenir à maintenant » (masqué si on y est déjà, Mn-4) + « Toutes les chaînes » | rien |
| Lecture Room des programmes en échec | lignes squelettes + message en tête « Programmes indisponibles » + « Réessayer » | `UiLog.failure("guide", "programmes", t)` |
| Lecture d'une chaîne impossible | gérée par le lecteur (lots 4-6) | — |
| Rappel / enregistrement refusé (place, conflit) | message existant (`Toaster`) | `UiLog.failure("guide", "enregistrement", t)` si exception |
| Logo introuvable | initiales (inchangé) | synthèse par visite (`ImageFailureTally("guide")`) |

- [ ] **Step 1: Tests qui échouent** : une fonction par ligne du tableau (rendu + action + focus resté sur `"guide-grid"`) ; `GuideLogTest.journal_sansAdresseNiIdentifiant` (exception portant `http://h:8080/live/jean/s3cret/1.ts`) ; `annulation_jamaisJournalisee`.
- [ ] **Step 2: Constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer** `--tests '*ui.guide*' --tests '*UiLog*'` → vert.
- [ ] **Step 5: Commit** — `Guide : chaque état vide ou d'erreur dit sa cause et propose une action, journal sans identifiant`.

### Task 6: Guide au toucher (téléphone, tablette)

**Files:**
- Modify: `K/ui/guide/GuideGridScreen.kt` (`GuideTouch`, `TouchGuideRow`, `TouchNoProgrammeRow` → `K/ui/guide/GuideTouch.kt`)
- Test: Modify `T/ui/guide/TouchGuideCellTest.kt`, Create `T/ui/guide/GuideTouchLayoutTest.kt`

Mise en page retenue (non maquettée, langage du lot 2, dp) : en haut, en-tête compact du programme touché (titre Sora 22, horaire, « Regarder » 48) ; pastilles des jours puis des catégories sur une `LazyRow` ; grille : colonne de chaînes figée 112 (portrait) / 168 (paysage, tablette), lignes 64, créneaux de 128 par 30 min, défilement horizontal commun à toutes les lignes (un seul état), repère de l'heure ; appui sur une case → fenêtre d'actions existante ; tablette ≥ 840 : en-tête à gauche (360) et grille à droite. Mêmes états que la tâche 5, même cache, `nowProvider` à la minute (pas de `System.currentTimeMillis()` en composition). Clavier / manette : le même nœud à curseur que la TV (curseur et réducteur partagés).

- [ ] **Step 1: Tests qui échouent** : `portrait_390_colonneFigee_defilementCommun` ; `paysage_844_et_tablette_deuxVolets` ; `appui_ouvreActions` ; `jourVide_message` (Mn-9) ; `police13_rienDeRogne` ; `arabe_miroir` ; `clavier_curseurCommeSurTv`.
- [ ] **Step 2: Constater l'échec.** **Step 3: Implémenter.** **Step 4: Lancer** `--tests '*ui.guide*'`.
- [ ] **Step 5: Commit** — `Guide au toucher : même langage, colonne des chaînes figée, deux volets sur tablette`.

### Task 7: TV en direct — catégories et liste des chaînes (maquette `ListeChaines`), curseur de liste, Retour

**Files:**
- Create: `K/ui/live/LiveCursor.kt` (pur), `K/ui/live/LiveChannelRow.kt` (ligne de chaîne partagée avec le lot 5)
- Modify: `K/ui/live/LiveScreen.kt` (`CategoryRow`, `ChannelList` ; suppression des focus différés : `delay(250)` l. 209, boucle `delay(150)`/`delay(50)` l. 433-437, `refocusKey` + `delay(3_000)` l. 119-128), `K/ui/live/LiveViewModel.kt` (`cursor` dans le `SavedStateHandle`)
- Test: Create `T/ui/live/LiveCursorTest.kt`, `T/ui/live/LiveListFocusTest.kt` ; Modify `LiveBackToCategoriesTest.kt`

**Interfaces:**
- Produces:
  - `@Immutable data class LiveCursor(val category: String, val rank: Int, val channelKey: String?)` ; `object LiveNav { fun reduce(c: LiveCursor, key: LiveKey, size: Int): LiveMove ; fun afterReturn(c: LiveCursor, keyOf: (Int) -> String?): LiveCursor }` — même principe que le Guide : la liste des chaînes est UN nœud focalisable, la ligne en surbrillance est une donnée.
  - `@Composable fun LiveChannelRow(number: Int?, logo: String?, name: String, programme: String?, fraction: Float, playing: Boolean, highlighted: () -> Boolean, onImage: Boolean, modifier: Modifier)` — ligne de 110, sans focus propre ; `onImage = true` pour le lecteur (lot 5).
  - `testTag` `"live-cat-<id>"`, `"live-list"`, `"live-row-<rang>"`, `"live-row-on"`.

Valeurs (maquette `ListeChaines`) : colonne des catégories à 72 / 60, largeur 340 : titre « TV en direct » Sora ExtraBold 44, marge 18 ; lignes 68, rayon 16, padding 22, nom 26 / compteur 20 SemiBold ; sélectionnée `Ux.Surface2` + `Ux.Text` ExtraBold ; autres transparentes + `Ux.Text2` SemiBold ; focus : `FocusSurface` du thème (vraies cibles : la liste des catégories est courte et statique). Liste à 460 / 150, largeur 760, lignes 110, écart 10, rayon 18, padding 22, écart 22 : numéro Sora 30 aligné à la fin 54, logo 76 rayon 14 (`decodeSize` 152 × 152), nom Sora 28 + programme 20 une ligne « … », progression 6, badge « EN COURS » ExtraBold 16 sur la chaîne qui joue dans l'aperçu ; fond `Ux.Card`, surbrillance `Ux.Cta` + `Ux.TextOnLight` + anneau du thème. En-tête de liste à 460 / 62 : « <Catégorie> · <N> chaînes » (texte actuel). Données `nowNext` lues PAR LIGNE (plus en haut de l'écran : `LiveScreen.kt:103`).

Focus : entrée de l'écran = la liste (`screenEntryTarget`), curseur = chaîne mémorisée ou rang 0 ; ▲ ▼ dans la liste = curseur (défilement `requestScrollToItem`, sans animation) ; ◀ depuis la liste → catégorie en cours (vraie demande dans l'appui, la colonne y défile d'abord par `scrollToItem` comme f89eaf62) ; ▶ depuis une catégorie → liste (curseur gardé) ; ▶ depuis la liste → « Regarder en plein écran » de l'aperçu (tâche 8) ; ◀ depuis une catégorie → menu latéral ; OK sur une catégorie → liste au rang 0 ; Retour : liste ou aperçu → catégorie en cours (même hors écran) ; catégories → menu latéral sur « TV en direct ».

Erreurs du Direct (liste) → écran → journal (écran `live`) :

| Cas | À l'écran | Journal |
|---|---|---|
| Aucune chaîne en local | carte `NoDataStateCard` (existante) avec ses actions | rien |
| Catégorie vide / Favoris vides | message dans le nœud liste (`D.noChannels` / `D.noFavorites`) ; le nœud reste focalisable | rien |
| Chaîne verrouillée | fenêtre de code (existante) | rien |
| Lecture Room en échec | message « Liste indisponible » + Réessayer | `UiLog.failure("live", "chaines", t)` |
| Guide en cours absent | ligne sans programme (nom seul) | rien |
| Logo introuvable | initiales | `ImageFailureTally("live")` |

- [ ] **Step 1: Tests qui échouent** : `LiveCursorTest` (bornes, liste vide, placeholder, `afterReturn` retrouve la clé, sinon même rang borné) ; `LiveListFocusTest` : `ouverture_focusSurLaListe_chaineMemorisee` ; `direct_retourPleinEcran_puisRetourRetour` (Review Focus n° 4) ; `soixanteCategories_retourSurLaQuarantieme` ; `gaucheDroite_entreColonnes_curseurGarde` ; `pagesQuiArrivent_focusNeBougePas` ; `changementDeCategorie_rang0_sansDelai` ; `arabe_miroir` ; recompositions (`LiveProbe`) : ▼ → lignes ≤ 2, écran 0, catégories 0.
- [ ] **Step 2: Constater l'échec.** **Step 3: Implémenter** ; `grep -n "delay(" K/ui/live/LiveScreen.kt` → aucun `delay` qui précède une demande de focus. **Step 4: Lancer** `--tests '*ui.live*'`.
- [ ] **Step 5: Commit** — `TV en direct : catégories et chaînes façon maquette, liste à curseur, Retour vers la catégorie puis le menu`.

### Task 8: TV en direct — aperçu (maquette `Direct`), vidéo gardée composée, son lié à l'image

**Files:**
- Modify: `K/ui/live/LiveScreen.kt` (`Preview`, `SharedVideo`, calcul de `shown` hors du haut de l'écran), `K/ui/live/LivePreview.kt`
- Test: Create `T/ui/live/LivePreviewPaneTest.kt` ; Modify `LivePreviewPolicyTest.kt`

**Interfaces:**
- Consumes: `SharedLiveSession` (`preview`, `openFullscreen`, `setPictureShown`, `hostAttached/Detached`), `LivePreviewPolicy`, `LiveCursor` (tâche 7), `imageScrims(pageColor = Ux.Bg)`, `PillButton`.
- Produces: `@Composable internal fun LivePreviewPane(channel: () -> ChannelEntity?, live: PlaybackSession?, shared: SharedLiveSession?, onFullscreen: () -> Unit, onGuide: (ChannelEntity) -> Unit, modifier: Modifier)` ; `testTag` `"live-fullscreen"`, `"live-open-guide"`.

Règles : vidéo à droite, collée au haut et au bord (x 1268 → 1920, 16:9), voiles début + bas vers `Ux.Bg` (dégradés de la maquette, `drawWithCache`) ; dessous, sur la page : DIRECT (pastille `LiveBadge`), « 12 · Stade 1 », titre Sora ExtraBold 44 deux lignes, progression + horaire, « Ensuite · 22:45 · Le Club », boutons 68 « Regarder en plein écran » (principal) et « Guide » (ouvre le Guide sur cette chaîne : curseur initial = (chaîne, maintenant)). La vue vidéo RESTE composée tant qu'une session `PREVIEW` vit, même quand elle lit une autre chaîne ; le logo opaque de la chaîne montrée la recouvre jusqu'à son image (mécanisme de `GuidePreviewVideo`, 1c590586, déplacé ici) : plus aucune surface détruite à chaque changement de chaîne (`LiveProbe.videoHosts` : 1 attache pour 10 changements). Son : `gateAudioOnPicture = true` dans le Direct (le son de l'ancienne chaîne n'est jamais entendu sous le logo de la nouvelle) ; repli de moteur inchangé (`previewMode = false`). Délai : `LivePreviewPolicy.delayMs(pv.delayMs)` inchangé. Ouvrir le plein écran depuis la chaîne prête = sans rechargement (inchangé).

Erreurs de l'aperçu : flux en erreur → logo + « Aperçu indisponible · <cause courte> » sous la vidéo, OK reste « Regarder en plein écran » (le lecteur dira la cause complète) ; journal `play` existant (`preview error kind=…`), sans adresse.

- [ ] **Step 1: Tests qui échouent** : `dixChangements_uneSeuleAttacheVideo` ; `sonCoupe_desQueLaChaineMontreeChange` ; `apercuEnErreur_messageEtPleinEcranPossible` ; `boutonGuide_ouvreLeGuideSurLaChaine` ; `apercuDesactive_aucunFlux` (existant gardé) ; `arabe_videoABordGauche` ; `themeClair_voileVersLaPage`.
- [ ] **Step 2: Constater l'échec.** **Step 3: Implémenter.** **Step 4: Lancer** `--tests '*ui.live*' --tests '*SharedLive*' --tests '*PlaybackSession*'`.
- [ ] **Step 5: Commit** — `TV en direct : aperçu façon maquette, vidéo jamais recréée d'une chaîne à l'autre, son seulement avec l'image`.

### Task 9: TV en direct au toucher (téléphone, tablette)

**Files:** Modify `K/ui/live/LiveScreen.kt` (`MobileLiveLayout`, puces `CatChip` avec `uniqueKeys` — M2 de retours-3-review) ; Test : Create `T/ui/live/LiveTouchTest.kt`.

Mise en page retenue (dp) : téléphone : puces de catégories en `LazyRow`, liste de `LiveChannelRow` (hauteur 72, logo 48), appui = lecture ; tablette (`usesTwoPane`) : liste à gauche (420), aperçu à droite (logo + programme + « Regarder », pas de vidéo au toucher : inchangé). Mêmes états que la tâche 7.

- [ ] **Step 1: Tests qui échouent** : `telephone_appuiLit` ; `tablette_appuiChoisit_regarderLit` ; `puces_clesUniques_idSpecial` ; `police13` ; `arabe`. **Step 2-4** comme ci-dessus. **Step 5: Commit** — `TV en direct au toucher : même langage, deux volets sur tablette`.

### Task 10: Retour du lecteur et mémoire de position (Guide, Direct), mort du processus

**Files:** Modify `GuideGridViewModel`, `LiveViewModel` (`SavedStateHandle`), `K/ui/guide/GuideGridScreen.kt`, `K/ui/live/LiveScreen.kt` ; Test : Create `T/ui/guide/GuideReturnTest.kt`, `T/ui/live/LiveReturnTest.kt`.

Règles : Guide : « Regarder » (en-tête ou fenêtre d'actions) → lecteur ; Retour → même nœud focalisé (grille ou bouton), même curseur, même fenêtre, même jour, même catégorie ; aucun flux ouvert par le Guide. Direct : retour du plein écran → curseur sur la chaîne regardée (dernier zap, `shared.channelKey`), la liste défile jusqu'à elle par `requestScrollToItem` à la première mesure où sa page est chargée (donnée), l'aperçu garde la chaîne sans rechargement (`holdAfterReturn`, inchangé). Recréation de l'activité (mort du processus sur box 2 Go) : mêmes positions depuis le `SavedStateHandle`.

- [ ] **Step 1: Tests qui échouent** : `guide_retourDuLecteur_surRegarder_curseurGarde` (Review Focus n° 2) ; `guide_retourDuLecteur_depuisFenetreDActions_surLaCase` ; `guide_recreation_positionsRestaurees` (`StateRestorationTester`) ; `direct_retour_chaineZappee_loinDansLaListe` (chaîne 250, pages chargées après) ; `direct_recreation`. **Step 2-4.** **Step 5: Commit** — `Guide et Direct : retour du lecteur sur l'élément quitté, positions gardées même après recréation`.

### Task 11: Mesure sur la box avant / après

**Files:** `android-native/tools/mesure-navigation.sh` (tâche 1) ; résultats dans la PR et dans `.superpowers/sdd/<registre>/progress.md`.

Protocole (accord du propriétaire pour la box ; une installation ; box laissée utilisable) :
1. Installer la pré-version ; `adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE …` puis `adb shell cmd package compile -m speed-profile -f <paquet>` ; vérifier `status=speed-profile` dans `dumpsys package dexopt` AVANT toute mesure (erreur de la rc5).
2. Guide ouvert depuis le menu, « Toutes », Aujourd'hui, aucune synchro en cours (pas de ligne de progression), aperçu du Direct arrêté.
3. Pour chaque direction ▼ ▲ ▶ ◀ : `dumpsys gfxinfo <paquet> reset`, 8 appuis espacés de 0,5 s, `dumpsys gfxinfo <paquet>` ; deux passes ; relever 50e / 90e / 99e centiles, images par appui, taux d'images lentes, lignes `perf guide` et `perf stall`.
4. Même chose dans la liste du Direct (▼ ▲, aperçu actif puis désactivé) et sur l'accueil (référence : 32 ms au 50e centile).
5. `dumpsys meminfo <paquet>` après 5 min de ◀ ▶ sur trois jours.

Objectif : Guide ▼ ▲ ◀ ▶ au 50e centile ≤ celui de l'accueil mesuré le même jour (≈ 32 ms ; référence rc4 : 46-113 ms), 90e centile ≤ 60 ms, aucune nouvelle ligne `perf stall` ; Direct ▼ ▲ pas plus lent qu'avant (tâche 1). Si l'objectif n'est pas atteint : pas de fusion ; relever une trace Perfetto d'un ▼ isolé et le signaler au propriétaire avec les chiffres.

- [ ] **Step 1: Mesure « après »** ; **Step 2:** tableau avant / après dans la PR ; **Step 3: Commit** des chiffres dans le registre — `Guide et Direct : mesures sur la box avant et après`.

### Task 12: Dix thèmes, captures, CHANGELOG, suite complète, contrôle sur appareil

**Files:** Modify `T/ui/guide/GuideRenderTest.kt`, Create `T/ui/live/LiveRenderTest.kt`, `T/ui/guide/GuideThemeContrastTest.kt` ; `CHANGELOG.md`, `README.md`, `README.fr.md`, `docs/screenshots/{fr,en,ar}/*guide*`, `*live*` ; vérifier `android-native/app/src/main/baseline-prof.txt`.

- [ ] **Step 1: Contrastes**, chaque `AppTheme.entries`, TV et téléphone : texte de case sur `Ux.Card` ≥ 4,5 ; texte en surbrillance sur `Ux.Cta` ≥ 7 ; pastille sélectionnée (`Ux.Text` / `Ux.Bg`) ≥ 4,5 ; anneau ≥ 3 sur la page ; repère de l'heure ≥ 3.
- [ ] **Step 2: Rendu de tous les thèmes** (Guide, Direct ; TV ; téléphone portrait) puis captures de documentation (sombre, clair, arabe, anglais ; TV, téléphone portrait et paysage, tablette ; états vides et d'erreur). Ouvrir CHAQUE capture.
- [ ] **Step 3: `grep`** : `grep -rn "LaunchedEffect\|withFrameNanos\|view.post\|delay(" K/ui/guide K/ui/live` — chaque occurrence restante est justifiée en commentaire et ne déplace jamais le focus.
- [ ] **Step 4: Profil de démarrage** : lignes nommant `GuideCell`, `GuideFocusPlan`, `GuidePreviewPane`, `CategoryRow`, `ChannelList` mises à jour.
- [ ] **Step 5: Suite complète** `./gradlew --offline -q :app:testDebugUnitTest` → 0 échec, deux passes ; i18n sous JDK 17 et 27.
- [ ] **Step 6: CHANGELOG et README** (fr, en) ; commit — `Guide et Direct : captures, journal des modifications`.
- [ ] **Step 7: Liste de contrôle sur appareil** (cochée par le propriétaire) : (1) ouverture du Guide depuis le menu sans toucher, puis en pressant ▶ dès l'OK : focus sur la grille, case de maintenant ; (2) ⏪ « Hier » → message → OK « Revenir à maintenant », 10 fois ; (3) ▼ maintenu sur « Toutes » ; (4) minuit vers 23:50 ; (5) « Réessayer » le guide pendant le parcours ; (6) Regarder → Retour (Guide et Direct) ; (7) Direct : 30e catégorie, Retour, Retour ; (8) aperçu : 10 changements de chaîne, aucun son sous le mauvais logo ; (9) arabe, police très grande ; (10) les 10 thèmes ; (11) téléphone et tablette ; (12) mesures de la tâche 11.
- [ ] **Step 8: PR** vers `main` : ce qui change, points tranchés, comportements préservés, tests retirés (aperçu du Guide) et pourquoi, mesures avant / après, captures. Pas de fusion sans CI verte ; aucune version publiée sans accord du propriétaire.
