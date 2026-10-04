<p align="center">
  <img src="docs/screenshots/fr/banner.png" alt="Ultra TV" width="100%" />
</p>

<h1 align="center">Ultra TV</h1>

<p align="center">
  <strong>Lecteur IPTV natif pour Android TV et Google TV.</strong><br/>
  Kotlin · Compose for TV · Media3 (ExoPlayer) + LibVLC · Room · Hilt
</p>

<p align="center">
  <a href="https://github.com/khalilbenaz/ultra-tv/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/khalilbenaz/ultra-tv/actions/workflows/ci.yml/badge.svg" /></a>
  <a href="https://github.com/khalilbenaz/ultra-tv/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/khalilbenaz/ultra-tv?label=release" /></a>
  <a href="LICENSE"><img alt="Licence MIT" src="https://img.shields.io/badge/licence-MIT-0284c7" /></a>
  <a href="https://khalilbenaz.github.io/ultra-tv/"><img alt="Site" src="https://img.shields.io/badge/site-GitHub%20Pages-d91e2b" /></a>
</p>

<p align="center">
  <a href="README.en.md">🇬🇧 English version</a> ·
  <a href="https://khalilbenaz.github.io/ultra-tv/">Site</a> ·
  <a href="https://github.com/khalilbenaz/ultra-tv/releases/latest/download/UltraTV-debug.apk">Télécharger l'APK</a> ·
  <a href="https://github.com/khalilbenaz/ultra-tv/releases">Releases</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

---

Ultra TV lit vos propres abonnements IPTV (**Xtream Codes**, **M3U / M3U8** en lien ou en fichier). Toute l'interface est native et pilotée à la télécommande ; le catalogue (chaînes, films, séries, guide, historique, favoris) vit dans une base Room locale. Il ne fournit **aucun contenu**.

> Les captures ci-dessous utilisent exclusivement des **données synthétiques** (faux serveur Xtream local, `android-native/tools/fake-xtream`).

<p align="center">
  <img src="docs/screenshots/fr/home.png" alt="Accueil" width="48%" />
  <img src="docs/screenshots/fr/live.png" alt="Direct" width="48%" />
  <img src="docs/screenshots/fr/guide.png" alt="Guide des programmes" width="48%" />
  <img src="docs/screenshots/fr/detail.png" alt="Fiche film" width="48%" />
  <img src="docs/screenshots/fr/player.png" alt="Lecteur" width="48%" />
  <img src="docs/screenshots/fr/settings.png" alt="Réglages" width="48%" />
  <img src="docs/screenshots/fr/profiles.png" alt="Qui regarde ?" width="48%" />
  <img src="docs/screenshots/fr/languages.png" alt="Réglages, panneau Langues" width="48%" />
</p>

Captures aussi disponibles en anglais ([`docs/screenshots/en`](docs/screenshots/en)) et en arabe, interface en miroir ([`docs/screenshots/ar`](docs/screenshots/ar)).

## Fonctionnalités

- **Recherche** depuis n'importe quel écran : bouton en haut du menu latéral, touches Recherche et micro de la télécommande.
- **Direct** : catégories actives, filtre par langue dès la première source, séparateurs en en-têtes, badges de qualité, zapping par numéro, retour à la chaîne précédente, 20 chaînes récentes, recherche instantanée (FTS).
- **Guide** : grille horaire, rappels, enregistrements programmés, **replay** (catch-up Xtream) depuis le guide quand la source le permet.
- **Pause du direct** (timeshift) : tampon disque circulaire pour les flux MPEG-TS.
- **Films et séries** : fiches enrichies via **TMDB** (affiche, synopsis, distribution), reprise de lecture, saisons en onglets.
- **Deux moteurs de lecture** : ExoPlayer (Media3) et **LibVLC**, choix Auto / ExoPlayer / VLC, décodage Auto / Matériel / Logiciel, repli automatique et mémorisation par chaîne.
- **Sous-titres** : recherche en ligne (OpenSubtitles via le Worker), style avancé (taille, couleur, fond, contour, position, décalage).
- **Profils** : « Qui regarde ? », profil Enfants, favoris, historique et langues par profil.
- **Google TV** : Watch Next, chaîne Favoris, recherche vocale et globale, liens profonds `ultratv://`.
- **Thèmes** Sombre / Clair / Automatique (le lecteur reste toujours sombre) ; interface en anglais, français, espagnol et arabe (RTL).
- **Veille** : minuterie 30 / 60 / 90 min ou fin de programme ; mise à jour intégrée depuis les releases GitHub.

## Appareils

Un seul compte, tous vos écrans : même interface, même fournisseur partout. Ajoutez-le une fois dans le [tableau de bord cloud](#synchronisation-cloud) et chaque appareil le reçoit.

### Android TV et Google TV

L'application d'origine, pilotée à la télécommande (voir les captures ci-dessus et [Fonctionnalités](#fonctionnalités)).

### Smartphone Android

Interface tactile : barre de navigation en bas, listes et grilles au doigt, lecteur à gestes avec image dans l'image ; les formulaires se calent au-dessus du clavier.

<p align="center">
  <img src="docs/screenshots/phone/fr/home.png" alt="Accueil sur smartphone" width="23%" />
  <img src="docs/screenshots/phone/fr/live.png" alt="Direct sur smartphone" width="23%" />
  <img src="docs/screenshots/phone/fr/detail.png" alt="Fiche film sur smartphone" width="23%" />
  <img src="docs/screenshots/phone/fr/player.png" alt="Lecteur sur smartphone" width="23%" />
</p>

### Tablette Android

Menu latéral, liste et fiche côte à côte.

<p align="center">
  <img src="docs/screenshots/tablet/fr/live.png" alt="Direct sur tablette" width="48%" />
  <img src="docs/screenshots/tablet/fr/home.png" alt="Accueil sur tablette" width="48%" />
</p>

### Windows et macOS

Le même design sur ordinateur : l'application web de [`web/`](web/README.md) empaquetée par Electron ([`electron/`](electron/README.md)), utilisable aussi dans un navigateur. Xtream Codes et M3U (lien ou fichier) ; catalogue gardé en local (IndexedDB), identifiants chiffrés avec le trousseau du système, lecture directe (aucun proxy distant), mise à jour automatique via les Releases GitHub. **Recherche** : bouton rond sous le logo, raccourci `Ctrl/Cmd + K` ou `/` depuis n'importe quel écran.

<p align="center">
  <img src="docs/screenshots/desktop/fr/06-direct.png" alt="Direct sur le bureau" width="48%" />
  <img src="docs/screenshots/desktop/fr/14-reglages.png" alt="Réglages sur le bureau" width="48%" />
</p>

Toutes les captures du bureau (données synthétiques, FR et EN) : [`docs/screenshots/desktop/`](docs/screenshots/desktop/). Publication : tag `desktop-vX.Y.Z` (workflow `desktop-release.yml`, séparé des tags Android `v*`).

### Tableau de bord cloud

Gérez appareils et sources depuis un navigateur : **<https://ultratv-config.khalilbenaz.workers.dev>**.

<p align="center">
  <img src="docs/screenshots/cloud/devices.png" alt="Appareils appairés" width="48%" />
  <img src="docs/screenshots/cloud/sharing.png" alt="Partage des sources par appareil" width="48%" />
</p>

### Synchronisation multi-appareils

- **Même fournisseur partout** : un fournisseur ajouté dans le tableau de bord est reçu par tous les appareils du compte.
- **Source propre à un appareil** : une source ajoutée sur un appareil lui reste privée tant que vous ne la partagez pas.
- **Partage choisi par appareil** : source par source, vous cochez les appareils destinataires (dans l'application : Réglages › Sources › *Partager cette source* ; ou dans le tableau de bord).
- Les modifications d'un compte sont sérialisées côté Worker (verrou Durable Object) : deux appareils qui écrivent en même temps ne s'écrasent pas.

## Téléchargements

| Appareil | Fichier |
|---|---|
| Android (TV, smartphone, tablette) | [`UltraTV-debug.apk`](https://github.com/khalilbenaz/ultra-tv/releases/latest/download/UltraTV-debug.apk) universel, ou un APK par processeur `UltraTV-<version>-<abi>.apk` (`arm64-v8a`, `armeabi-v7a`, `x86_64`), plus `SHA256SUMS.txt` |
| macOS | `UltraTV-<version>-mac-universal.dmg` (Apple Silicon et Intel) |
| Windows | `UltraTV-<version>-win-x64.exe` (installeur NSIS) |
| Tableau de bord cloud | <https://ultratv-config.khalilbenaz.workers.dev> (aucune installation) |

Tout est sur la page [Releases](https://github.com/khalilbenaz/ultra-tv/releases) (les fichiers bureau portent les tags `desktop-v…`).

## Installation

Android 9 ou plus récent (API 28).

**APK.** Téléchargez [`UltraTV-debug.apk`](https://github.com/khalilbenaz/ultra-tv/releases/latest/download/UltraTV-debug.apk) (somme SHA-256 sur la page de la release) et installez-le.

**Downloader** (box sans navigateur) : ouvrez l'application [Downloader](https://www.aftvnews.com/downloader/), saisissez le code **`5248504`**, autorisez les sources inconnues, installez.

**adb** :

```bash
adb connect IP_DE_LA_BOX:5555
adb install -r UltraTV-debug.apk
```

Au premier lancement : choisissez le type de source (Xtream Codes, lien M3U, fichier M3U ou **Depuis le cloud**), saisissez-la, puis cochez vos langues (seules les catégories correspondantes sont téléchargées).

Une adresse du type `…/get.php?username=…&password=…` collée comme lien M3U est reconnue et ajoutée comme source Xtream Codes (beaucoup de fournisseurs bloquent `get.php`).

## Synchronisation cloud

Pour ne pas saisir d'identifiants à la télécommande, gérez vos sources depuis un navigateur : **<https://ultratv-config.khalilbenaz.workers.dev>** (l'adresse est aussi affichée, avec un QR code, dans l'application : Réglages › Sources).

1. Créez un compte sur le tableau de bord et ajoutez vos sources.
2. Dans le tableau de bord, choisissez **Appairer une TV**.
3. Sur la box : **Depuis le cloud** (écran Source de l'assistant) ou Réglages › Sources › *Synchroniser depuis le cloud*. Un code à 8 caractères s'affiche ; saisissez-le dans le tableau de bord.
4. La box importe la configuration puis synchronise.

L'appareil reçoit un jeton aléatoire de 256 bits (haché côté serveur, chiffré dans le Keystore, révocable). Vos identifiants sont **chiffrés côté serveur** (AES-256-GCM). L'étiquette affichée pour la box (« UTV-XXXXXX ») n'est qu'un nom, jamais une clé.

**Auto-hébergement.** Vous pouvez déployer votre propre Worker (voir [Worker Cloudflare](#worker-cloudflare)) : créez les espaces KV, définissez les secrets `SESSION_SECRET`, `PROVIDER_ENC_KEY` et `OPS_TOKEN` (aucune valeur n'est publiée), puis `wrangler deploy` ; dans l'application, Réglages › Sources › *Adresse du tableau de bord* pointe alors vers le vôtre.

## Sécurité

- Identifiants des sources **chiffrés au repos** sur l'appareil ; sur le Worker, AES-256-GCM.
- **Aucune URL de flux, aucun identifiant n'est affiché** ni journalisé ; les messages d'erreur sont filtrés (`UserText`). Un nom de source vide ne reprend pas l'adresse du serveur.
- Aucun jeton partagé dans l'APK. Détails, modèle de menace et rotation des anciens secrets : [SECURITY.md](SECURITY.md).

## Réglages automatiques

À la première ouverture, l'**`AdaptiveProfile`** mesure l'appareil (mémoire, tas, micro-benchmark) et le classe **Bas / Moyen / Haut**. Il en déduit tampon, parallélisme et taille des lots de synchro, plafond de résolution et de débit (box à peu de mémoire : 720p / 6 Mb/s) et ajuste le tampon si des coupures surviennent. Tout reste modifiable : Réglages › Lecture (moteur, décodage, préréglage de tampon : Faible latence, Auto, Équilibré, Stable, Personnalisé).

## Développement

JDK 17 requis.

```bash
git clone https://github.com/khalilbenaz/ultra-tv && cd ultra-tv/android-native
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
./gradlew assembleDebug                            # APK universel
./gradlew testDebugUnitTest                        # tests unitaires (JUnit, Robolectric)
./gradlew assembleRelease                          # APK par ABI (arm64-v8a, armeabi-v7a, x86_64)
```

- La version vient du fichier [`VERSION`](VERSION) ; `versionCode = major×10000 + minor×100 + patch` (1.2.0 → 10200).
- **Faux serveur Xtream** pour tester sans abonnement : `python3 android-native/tools/fake-xtream/server.py` (l'émulateur y accède via `http://10.0.2.2:8099`, identifiants `test` / `test`).
- Build debug : intents de débogage (`debug_route`, `debug_theme`, `debug_engine`, `debug_decoder`, `debug_buffer`…) pour les captures et les mesures.
- **CI** ([ci.yml](.github/workflows/ci.yml)) : web (tsc + vitest), Android (compilation + tests unitaires), Worker. Publication : [release.yml](.github/workflows/release.yml) sur tag `v*` (APK universel `UltraTV-debug.apk`, APK par processeur `UltraTV-<version>-<abi>.apk` et `SHA256SUMS.txt`) ; site : [pages.yml](.github/workflows/pages.yml).

## Worker Cloudflare

`cloudflare-config/` : tableau de bord d'appairage, stockage chiffré des sources, ingestion des crashs, proxies TMDB et OpenSubtitles. Procédure complète : [cloudflare-config/README.md](cloudflare-config/README.md).

```bash
cd cloudflare-config && npm ci
wrangler kv namespace create CONFIG                # coller les ids dans wrangler.toml
wrangler secret put SESSION_SECRET                 # >= 32 caractères aléatoires
wrangler secret put PROVIDER_ENC_KEY               # clé AES-256 en base64
wrangler secret put OPS_TOKEN                      # mot de passe de /crashes et /logs
wrangler secret put TMDB_READ_TOKEN                # jeton de lecture TMDB v4 (fiches enrichies)
wrangler secret put TMDB_API_KEY                   # clé TMDB v3 (repli si pas de jeton v4)
wrangler secret put OPENSUBTITLES_API_KEY          # clé OpenSubtitles (sous-titres en ligne)
npm test && wrangler deploy --dry-run
```

TMDB et OpenSubtitles sont facultatifs : sans leurs secrets, l'application masque les fonctions correspondantes. Pour un fork, compilez avec `-PULTRA_WORKER_URL=https://votre-worker.workers.dev`.

## Limites connues

- Le **replay** et la **pause du direct** dépendent de la source (catch-up Xtream, flux MPEG-TS) ; indisponibles sinon.
- Les fiches TMDB et les sous-titres en ligne exigent un Worker configuré avec les secrets ci-dessus.
- La première synchro d'un très gros catalogue (≈ 55 000 chaînes) prend environ une minute sur un appareil de milieu de gamme et davantage sur un modèle d'entrée de gamme.
- L'APK grossit avec LibVLC (≈ 53 Mo en arm64-v8a, contre ≈ 9 Mo en 1.0.x).
- Les mesures publiées dans les notes de version viennent d'émulateurs Android TV (rendu logiciel) : à confirmer sur matériel réel.

## Licence

MIT, voir [LICENSE](LICENSE). Ultra TV est un client IPTV : utilisez uniquement des listes, guides et identifiants que vous avez le droit d'utiliser. Les polices Sora et Manrope sont sous licence OFL.
