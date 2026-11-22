# Interface façon streaming — lots 4, 5 et 6 : lecteur (direct, liste des chaînes, audio / sous-titres / qualité, erreur, film et série) — plan d'implémentation

État : brouillon — reste à écrire : les étapes détaillées (tests nommés, valeurs de maquette au pixel) des tâches 5 à 12, la section Review Focus finalisée, l'auto-relecture. Les décisions (points à trancher, contraintes, correctifs à conserver, conception « blocage et reconnexion », plafonds d'ouverture, liste des tâches) sont écrites et font foi.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Le lecteur montre des commandes peu nombreuses et nommées (maquettes `LecteurDirect`, `OsdZapping`, `OsdRecentes`, `ListeChaines`, `OsdOptions`, `LecteurErreur`, `LecteurFilm`), ne laisse jamais l'utilisateur devant une image figée sans explication ni devant un « Chargement » sans fin, se reconnecte seul et vite quand le direct se bloque, et garde tous les correctifs de lecture passés.

**Architecture:** Les moteurs, `PlaybackSession`, `PlaybackPlanner` et `SharedLiveSession` restent ; on leur ajoute des états d'affichage (`SessionState.recovery`, `SessionState.opening`) et deux règles pures testées (`LiveRecovery`, `OpenBudget`). L'OSD garde `OsdActions.build` (ordre, actions conditionnelles) ; la règle nouvelle « sept premières actions nommées, le reste sous Plus » est une fonction pure. La liste des chaînes du lecteur réutilise `LiveChannelRow` (lot 7, tâche 7) avec `onImage = true`. L'écran d'erreur garde `PlayErrorText` et ne change que la mise en page.

**Tech Stack:** Kotlin, Compose, tv-material3, Hilt, Media3 / ExoPlayer, LibVLC, Room ; tests Robolectric + compose-ui-test, faux moteurs de `PlaybackSessionReconnectTest`, `MockWebServer` (portail Stalker factice de `StalkerLiveFakePortalTest`).

**Spec:** `docs/superpowers/specs/2026-11-21-interface-streaming-design.md` (écrans 7, 8, 9, 16, 17, 18, 19 ; « Commandes du lecteur direct » ; « Lecteur en différé ») ; `.superpowers/sdd/2026-11-21-interface-streaming-lot-0-1-accueil-tv/analyse-session-hors-ligne.md` (S3, S7) ; `audit-erreurs-journaux.md` (P5, P6) ; `docs/ameliorations-2026-11-22-glm.md` (propositions 3 et 4). Dépend du lot 7 pour `LiveChannelRow` (sinon la tâche 6 la crée avec EXACTEMENT la signature du plan du lot 7) et du lot 2 pour `UiLog` / `uiAttempt`.

Chemins abrégés : `K`, `T`, `R` comme dans le plan du lot 7.

## Points à trancher par le propriétaire

1. **Blocage du direct : délais.** Proposé : image figée détectée en 6 s (aujourd'hui 10 s), rechargement bloqué en 8 s (aujourd'hui 12 s) ; indication « Reconnexion… » dès 2 s de blocage ; 6 essais (0,5 / 1 / 2 / 3 / 5 / 8 s, ≈ 20 s) puis l'écran d'erreur (aujourd'hui 8 essais, ≈ 40 s, avec un message éphémère). Tâche 2.
2. **Reprise au direct après reconnexion** (jamais en retard sur le direct) ; en différé (pause du direct) la reprise se fait à la position du différé. Tâche 2.
3. **Commandes du direct** : les sept nommées de la spec (Pause, Depuis le début, Chaînes, Guide, Enregistrer, Audio et sous-titres, Favori) puis « Plus » ; « Format d'image », « Historique », « Minuterie », « Lecteur », PiP et raccourcis passent sous « Plus ». Tâche 4.
4. **Plafond d'ouverture** : direct 30 s, film / épisode 45 s de l'appui à l'image ou à l'écran d'erreur, replis compris (aujourd'hui jusqu'à ≈ 70 s avec quatre moteurs) ; un repli n'est lancé que s'il reste au moins 8 s ; un flux qui progresse (chargement mesurable) prolonge jusqu'à 90 s comme aujourd'hui. Tâche 3.
5. **Sortie audio directe (Passthrough)** : réglage « Sortie audio : Décodée (défaut) / Directe (AC3, E-AC3) » sous Réglages › Lecture › Avancé, DÉSACTIVÉ par défaut, avec repli automatique en sortie décodée après 2 s sans son ou à la première erreur audio, mémorisé pour l'appareil. Tâche 11.
6. **Épisode suivant** : carte dans les 30 dernières secondes ou au générique connu, lancement seul au bout de 12 s (maquette), « Lancer maintenant » ; désactivable (réglage existant s'il existe, sinon pas de réglage nouveau). Tâche 9.

## Global Constraints

Reprend intégralement la section Global Constraints du plan du lot 7 (appareils, chaque tâche visible et livrable seule, aucun plantage, navigation logique et stable sans réparation différée du focus, performance, 10 thèmes, textes en / fr / es / ar et chiffres latins en arabe, gestion d'erreur et journaux masqués, zéro régression, aucune appli concurrente, aucune ligne d'attribution dans les commits, suite complète verte). En plus, pour le lecteur :

- Tout ce qui est posé sur la vidéo suit l'exception de la spec : voile `#0A0C10`, encres `ImageInk`, boutons `OnImageButton` / `OnImageIconButton`, anneau blanc ; identique dans les 10 thèmes. Le panneau `OsdOptions` (fond `#0F1218`) et l'écran d'erreur (fond `#0A0C10`) sont « sur la vidéo » au sens de la spec.
- Une seule connexion fournisseur à la fois : `release()` avant toute ouverture (règle de `PlaybackSession`), jamais deux flux, y compris pendant une reconnexion.
- Aucun appel réseau ni lecture Room sur le fil principal ; aucune recomposition de l'écran du lecteur par image vidéo ou par seconde (l'horloge et la position sont lues au dessin ou dans de petites feuilles).
- Journal de chaque échec et de chaque blocage : UNE ligne par événement, étiquette `play`, champs `session=<8 hex> src=<type> live=<0|1> engine= decoder= try= played=<s> http=<n> host=<empreinte>` (format P6 de l'audit), jamais d'adresse, d'identifiant, de MAC ni de jeton.

### Correctifs passés à conserver (relevés dans les commentaires du code ; chacun garde ou reçoit un test, tâche 1)

| Correctif | Où | Test |
|---|---|---|
| « Qualité maximale » : piste la plus haute forcée, aucun plafond de débit même après des rechargements ; un plafond choisi à la main reste | `AdaptiveProfile.kt:270-275`, `ExoEngine.kt:189,234,393`, `TrackPolicy` | `TrackPolicyTest`, `AdaptiveTest` (existants) |
| 4K en Auto : plafond manuel prioritaire sur l'automatique ; écran 720p → pas de 4K ; box 1 Go → HD | `AdaptiveProfile.kt:272`, `Adaptive.kt:145-151` | `AdaptiveTest.auto_ecran720p_pasDeQuatreK`, `auto_gtvlow_*` |
| Débit initial 8 Mb/s (25 en qualité maximale) | `TrackPolicy.initialBitrateEstimate` | `TrackPolicyTest` |
| Repli moteur / décodeur : EXO auto → EXO logiciel → VLC auto → VLC logiciel ; moteur imposé jamais quitté ; HDR : matériels d'abord ; flux protégé : Media3 seul, aucun repli | `PlaybackPlanner.next`, `initial` | `EnginePlanningTest`, `PlaybackProtectedTest` |
| Démarrage figé : pas de décodage logiciel (il ne répare pas un flux qui n'arrive pas) ; VLC exclu pour Stalker sauf choix explicite | `PlaybackSession.fallbackAfter` | `PlaybackSessionStartupTest` |
| Gel peu après le démarrage = décodeur (combinaison suivante) ; gel juste après une bascule de fréquence d'écran = même combinaison | `armFrozenWatch`, `PlaybackPlanner.frozenMeansDecoder`, `DisplayModeGate` | `EarlyFreezeTest`, `FrozenLiveDetectorTest` |
| Image noire (3 vignettes noires) = « aucune image » ; copie non fiable (Amlogic, HDR) : rien conclu | `armBlackPictureCheck`, `FrameProbe` | `FrameProbeReliabilityTest` |
| HDR : bascule unique en SurfaceView, retour en TextureView mémorisé si pas d'image | `onHdr`, `HdrOutput` | `HdrOutputTest` |
| « Audio seul » confirmé 1,5 s après « prêt » (VLC) | `AUDIO_ONLY_CONFIRM_MS` | `SharedLiveSessionTest` |
| Plafond de la demande de lien 12 s / 20 s (bc6cc030) | `OPEN_LIVE_MS`, `OPEN_VOD_MS` | `PlaybackOpenTimeoutTest` |
| Une nouvelle tentative identique après 403 / réseau, connexion libérée avant | `shouldRetrySame`, `onError` | `PlaybackSessionReconnectTest` |
| Direct qui jouait : coupure = reconnexion, pas de changement de moteur ; VLC « FORMAT » après lecture = coupure | `onError` | `PlaybackSessionReconnectTest` |
| Arrière-plan : aucun rechargement (le son repartirait) | `reconnectLive` (`AppForeground.visible`) | à ajouter (`arrierePlan_aucuneReconnexion`) |
| Combinaison mémorisée par chaîne après 5 s de lecture si repli ou choix manuel | `rememberJob` | `ChannelPlaybackMemory` (test à ajouter) |
| Messages d'erreur : cause + action + code HTTP seulement quand il éclaire (1.3.72) | `PlayErrorText` | `PlayErrorTextTest` |
| Plein écran depuis l'aperçu sans rechargement ; Retour rend la main à l'aperçu | `SharedLiveSession.openFullscreen`, `demote` | `SharedLiveSessionTest` |
| Clés du tiroir de zapping départagées | `LiveDrawer` | `DrawerDuplicateKeysTest` |
| Lecture tunnelisée seulement avec SurfaceView et décodeur matériel | `TrackPolicy.tunnelingEffective` | `TrackPolicyTest` |

## Conception : blocage et reconnexion du direct (tâche 2)

Demande : « la chaîne actuelle s'est bloquée et reconnectée toute seule » → l'utilisateur ne doit ni rester devant une image gelée sans explication ni perdre sa chaîne.

- `enum class StallCause { FROZEN_POSITION, FROZEN_FRAMES, REBUFFER, ENDED, NETWORK_ERROR, AUTH }` ; `data class Recovery(val cause: StallCause, val sinceMs: Long, val attempt: Int, val max: Int)` ; `SessionState.recovery: Recovery?` (null = rien en cours).
- `object LiveRecovery` (pur) : `const val SHOW_AFTER_MS = 2_000L`, `FROZEN_MS = 6_000L`, `REBUFFER_MS = 8_000L`, `val DELAYS_MS = longArrayOf(500, 1_000, 2_000, 3_000, 5_000, 8_000)`, `fun next(attempt: Int): Long?`, `fun visible(r: Recovery?, nowMs: Long): Boolean` (après 2 s de blocage ou dès le premier essai).
- `PlaybackSession` : `FrozenLiveDetector(limitMs = LiveRecovery.FROZEN_MS)`, `STALL_MS` → `REBUFFER_MS` ; `reconnectLive(cause)` pose `recovery` (au lieu du seul `Notice.RETRYING`) ; la dernière image reste à l'écran (même moteur, même surface quand `reusable`) ; à la première image : `recovery = null`, ligne de journal `live stall … result=resumed stalledMs=<n> attempts=<n>` ; essais épuisés : `Phase.ERROR` avec la cause, ligne `result=failed`. Reprise au direct : `e.load(url, 0)` pour TS / Stalker (nouveau lien), `seekToDefaultPosition` pour HLS (`BEHIND_LIVE` inclus) ; en différé : position du différé.
- S3 (jeton expiré en lecture, Stalker) : sur 401 / 403 / 458 d'un flux `stalker://` qui jouait, UNE invalidation de session (`StreamUrlResolver.reauth(url)` → `StalkerClient.invalidate`) puis nouveau lien, au plus une fois par lancement de moteur ; disjoncteur : 3 refus d'autorisation en 10 min → plus aucune poignée de main automatique pendant 15 min (appels secondaires échouent tout de suite) ; `StallCause.AUTH` affiché « Reconnexion à la source… ».
- S7 (signal de présence) : 2 échecs d'autorisation de `watchdog` d'affilée → rafraîchir la session en arrière-plan (jamais recharger le flux depuis la présence) ; l'erreur aujourd'hui avalée (`PlaybackSession.kt:324`) est comptée et journalisée une fois.
- Affichage : `ReconnectBadge` en haut au début de l'image, pastille `#0A0C10` 70 %, texte `ImageInk.Primary` 22 « Reconnexion… » (et « essai 2 sur 6 » en `ImageInk.Quiet`), aucune animation sous `LocalLowRam` ; jamais l'écran d'erreur tant que des essais restent ; puis `LecteurErreur` avec Réessayer (focus), Chaîne suivante, Fermer.

| Cas (lecteur direct) | À l'écran | Journal (`play`) |
|---|---|---|
| Image figée (position ou images) 6 s | dernière image + « Reconnexion… » | `live stall cause=frozen-position|frozen-frames …` puis `result=` |
| Rechargement qui dure > 8 s | indicateur de chargement 2 s, puis « Reconnexion… » | `cause=rebuffer` |
| Fin de flux (session fermée par le serveur) | « Reconnexion… » | `cause=ended` |
| Réseau / 5xx / 429 en cours de lecture | « Reconnexion… » ; épuisé → `LecteurErreur` (cause de `PlayErrorText`) | `cause=network http=<n>` |
| 401 / 403 Stalker en lecture | « Reconnexion à la source… », une réauthentification | `cause=auth reauth=1` ; disjoncteur ouvert : `circuit=open` |
| Essais épuisés | écran d'erreur clair + actions | `result=failed attempts=6` (error) |
| Appli en arrière-plan | rien (aucun rechargement) | rien |

## Liste des tâches

1. **Filet de non-régression du lecteur et instruments** — tests du tableau « Correctifs à conserver » sur le code actuel ; relevé des durées d'ouverture (`firstFrame ms`, `open ms`) dans un journal local ; outil `tools/mesure-memoire.sh` (`dumpsys meminfo` toutes les 10 s pendant 5 min de lecture, direct TS, HLS, film 4K) ; aucune modification visible, livrée avec la tâche 2.
2. **Blocage et reconnexion du direct** (conception ci-dessus, S3, S7) — visible : « Reconnexion… » sur l'image, reprise au direct, erreur claire après 6 essais. Tests : `FrozenLiveDetectorTest` (6 s), `PlaybackSessionReconnectTest` (`gel_indicateurApres2s_repriseAuDirect`, `essaisEpuises_erreurAvecCause`, `reconnexion_memeMoteurMemeSurface`, `stalker403_uneReauth_puisReprise`, `disjoncteur_troisRefus_plusDePoigneeDeMain`, `presenceRefuseeDeuxFois_sessionRafraichie_fluxIntact`, `journal_uneLigneParBlocage_sansAdresse`), rendu `ReconnectBadge` (10 thèmes, arabe).
3. **Plafonds de durée à l'ouverture et écran de chargement parlant** — `object OpenBudget { const val LIVE_MS = 30_000L; const val VOD_MS = 45_000L; const val MIN_FOR_FALLBACK_MS = 8_000L; fun allowsFallback(startedMs, nowMs, live): Boolean }` ; `SessionState.opening: OpeningStep { LINK, ENGINE, FALLBACK }` ; chargement : logo + chaîne nommée, puis après 4 s « Connexion à la source… » / « Ouverture du flux… » / « Essai avec un autre lecteur… » (remplace les messages éphémères `Notice.USING_*`), « Réessayer » et « Chaîne suivante » focalisables dès 8 s ; écran d'erreur au plus tard au plafond.
4. **Lecteur direct : commandes nommées et « Plus »** (maquette `LecteurDirect`, écran 7) — `fun osdSplit(actions: List<OsdAction>, named: Int = 7): Pair<List<OsdAction>, List<OsdAction>>` (pur) appliqué au résultat de `OsdActions.build`, ordre de la spec ; en-tête numéro / chaîne / DIRECT / qualité / HDR / heure ; programme en cours, carte « Ensuite » (Me le rappeler, Enregistrer), barre de temps du programme ; aide télécommande ; focus initial sur Pause, une fois ; Retour ferme l'OSD (`LiveOsdLogic.backEffect`, inchangé).
5. **Bandeau de zapping** (maquette `OsdZapping`, écran 16) — numéro 84, logo 96, chaîne, DIRECT, programme, progression, « Ensuite », heure, chaînes précédente / suivante ; 3 s ; ne prend jamais le focus ; saisie de numéro (`ZapNumberBox`) inchangée.
6. **Récentes et soirée** (maquette `OsdRecentes`, écran 17) — étage 1 chaînes récentes (vignettes 340 × 192, `LiveOsdLogic.recents` dédoublonnées à la source : `LiveOsd.kt:350`), étage 2 soirée de la chaîne (`OsdRibbon`, aucune nouvelle requête), étage 3 commandes ; ▲ ▼ changent d'étage avec mémoire de rangée, Retour remonte d'un étage.
7. **Liste des chaînes dans le lecteur** (maquette `ListeChaines`, écran 8, lot 5) — `LiveDrawer` refait avec `LiveChannelRow(onImage = true)` et le curseur de liste du lot 7 (`LiveCursor`), onglets Chaînes / Favoris, catégories à gauche, vidéo qui continue derrière ; Retour : liste → catégorie en cours → fermeture ; chaîne qui joue marquée « EN COURS ».
8. **Audio, sous-titres et qualité** (maquette `OsdOptions`, écran 18) — panneau à droite 1180, trois colonnes à choix unique (Audio, Sous-titres dont « Chercher en ligne », Qualité : Automatique / 4K / Full HD / HD / Économie reliés à `TrackPolicy.MAX_HEIGHTS` et « Qualité maximale », sans changer la préférence enregistrée hors choix explicite), boutons Apparence des sous-titres (`SubtitlePanel`), Décalage audio, Diagnostic du flux (`StreamDiagnostic`) ; focus initial sur le choix actif de la colonne Audio.
9. **Lecteur de film et de série** (maquette `LecteurFilm`, écran 9, lot 6) — retour rond, titre, « S2 · É4 · titre » (chiffres latins), barre de lecture avec bulle de défilement (`SeekScrubLogic`, inchangé), −10 / Pause / +10, actions nommées (Épisode suivant, Épisodes, Audio et sous-titres, Vitesse, Plus), carte « Épisode suivant » (point n° 6, `NextEpisodeCard`) ; ◀ ▶ reculer / avancer, ▲ épisodes, Retour → fiche (focus restauré sur l'épisode, lot 3).
10. **Écran d'erreur** (maquette `LecteurErreur`, écran 19, lot 5) — chaîne nommée (logo + numéro · nom) au-dessus de la cause (`PlayErrorText`), « quoi faire », Réessayer (focus) / Chaîne suivante / Fermer, code technique discret ; cas S5 « Session expirée » ajouté (`PlayErrorCase.SESSION_EXPIRED`) ; tableau par cas `PlayErrorCase` → message → journal.
11. **Sortie audio directe, en option, et mesure de la mémoire en lecture** — réglage du point n° 5 (désactivé par défaut, repli automatique et mémorisé, journal `audio passthrough fallback reason=`) ; mesure `tools/mesure-memoire.sh` sur la box AVANT toute réduction de tampon : aucune modification de `BufferPlan` dans ce lot sans chiffres (proposition GLM 4 = décision ultérieure avec les mesures).
12. **Tablette et téléphone au toucher, dix thèmes, captures, CHANGELOG, suite complète, contrôle sur appareil** — OSD tactile (`MinimalTouchOsd`) dans le même langage, rendu des 10 thèmes, captures (direct, zapping, récentes, liste, options, erreur, film ; TV et téléphone), CHANGELOG / README, suite complète deux fois, liste de contrôle sur la box (blocage provoqué en coupant le réseau 10 s puis 40 s ; portail Stalker qui refuse ; 4K Auto et Qualité maximale sur un flux 4K ; repli VLC ; HDR ; radio ; arrière-plan pendant une reconnexion ; mémoire avant / après), PR.

Chaque tâche suit le format du plan du lot 7 (Files, Interfaces, tests rouges d'abord, commit par tâche avec un message en français sans ligne d'attribution) ; les étapes détaillées des tâches 5 à 12 restent à écrire (voir l'état en tête).
