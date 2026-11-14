# TV Samsung (Tizen) et LG (webOS)

Statut : **implémenté.** La même application web (`../web`) est empaquetée en widget Tizen
(`../tizen`, `.wgt`) pour les TV Samsung et en application webOS (`../webos`, `.ipk`) pour les TV
LG. Contexte d'origine : demande d'une version pour les téléviseurs Samsung et LG.

## Pourquoi c'est une web app empaquetée

Les deux plateformes sont des navigateurs embarqués :

| Plateforme | Moteur | Paquet | Réglages/fonctions spécifiques |
|---|---|---|---|
| Tizen (Samsung) | Chromium intégré au TV | `.wgt` (widget web : `config.xml` + assets) | Certificats Samsung obligatoires pour installer |
| webOS (LG) | Chromium intégré au TV | `.ipk` (`appinfo.json` + assets, ar/`ares-package`) | Mode développeur obligatoire pour installer |

Ultra TV est déjà une web app (React + Vite, lecteur hls.js / mpegts.js / HTML5) : rien à
réécrire. Il fallait :

1. **une couche plate-forme TV** (`web/src/lib/tv.ts`) : détection de l'appareil (User-Agent),
   transposition des touches de la télécommande, sortie d'application, polyfills pour Chromium 76 ;
2. **un build compatible TV** (`web/vite.config.ts`, `VITE_TV=1`) : cible **ES2017** (les TV n'ont
   pas les syntaxes récentes) et **Web Worker classique** pour la synchro (les module workers
   exigent Chromium 80+, absent des TV 2020 — Chromium 76/79) ;
3. **les deux paquets** (`tizen/`, `webos/`) et **le CI** (`.github/workflows/tv.yml`).

## Compatibilité

| Appareil | Support | Moteur | Verdict |
|---|---|---|---|
| Samsung TV 2020+ (Tizen 5.5 → 8.0) | ✅ cible | Chromium 76+ | `Promise.allSettled` requis (Chromium 76) |
| Samsung TV 2016–2019 (Tizen 2.4 → 5.0) | ❌ hors cible | Chromium 47–66 | `allSettled`, React 19, poids de l'appli |
| LG webOS 6+ (2021+) | ✅ cible | Chromium 79+ | idem |
| LG webOS 5 (2020) | ⚠️ meilleur effort | Chromium ~68 | tester avant de promettre |
| LG webOS 3–4.5 (2016–2019) | ❌ hors cible | Chromium 38–66 | idem Samsung |

`required_version="5.5"` (Tizen) exclut les TV trop anciennes côté installeur ; côté webOS, la
version minimale est déclarée à l'installation (Developer Mode / Seller Portal).

## Ce que fait la couche TV (`web/src/lib/tv.ts`)

Toujours **inactive** hors TV (navigateur, bureau) : détection par User-Agent
(`Tizen` / `SMART-TV` / `Maple`, `Web0S`).

- **Touches** (transposées en touches clavier que l'interface comprend déjà) :

| Touche télécommande | Événement produit |
|---|---|
| Retour (Tizen 10009, webOS 461, doublon webOS en 8) | `Escape` — ferme lecteur/panneau ; l'app ne se ferme pas d'elle-même (Accueil/Exit pour quitter) |
| Lecture / pause / lecture-pause (415, 19, 10252) | `Espace` (lecture/pause) |
| Rembobinage / avance (412 / 417) | `←` / `→` (± 10 s, films et épisodes) |
| Stop (413) | `Escape` |
| Chaîne + / − (427 / 428) | `↑` / `↓` (zapping du direct) |
| Exit (Tizen 10182) | sortie d'application (`tizen.application.exit()` / `window.close()`) |

- **Enregistrement Tizen** : les touches média, chaîne ± et Exit ne sont livrées qu'après
  `tizen.tvinputdevice.registerKey` (privilège `tv.inputdevice` dans `config.xml`) ; la couche les
  enregistre au démarrage. Sur webOS, `disableBackHistoryAPI` (appinfo.json) livre le Retour à
  l'appli au lieu d'un retour d'historique.
- **Saisie protégée** : pendant la frappe (clavier virtuel TV), le retour arrière reste un retour
  arrière ; les touches média ne déclenchent rien d'indésirable.
- **Polyfill** `crypto.randomUUID` (Chromium 92+ uniquement ; les TV 2020 ne l'ont pas).
- **HLS natif** (voir ci-dessous), activé par `import.meta.env.VITE_TV` côté moteur.

## HLS natif : lecture sans proxy sur TV

Dans un navigateur, hls.js (et mpegts.js) chargent les segments par XHR → **CORS obligatoire**,
donc proxy (Cloudflare/Vercel/Deno, Réglages › Lecture). Sur TV, la couche média native lit
elle-même les playlists **m3u8** (comme une TV câblée), **sans CORS ni proxy**.

Le moteur de lecture ajoute donc, quand l'app tourne sur TV (`isTv()`), un candidat
**« HLS natif » en dernier recours** : direct et films (repli m3u8) et playlists M3U HLS — après
hls.js et MPEG-TS, jamais sur la rediffusion (TS pur, illisible en natif). Source :
`player/resolve.ts` (`opts.nativeHls`), `player/engine.ts` (`format "hlsn"`, `loadNative(rawHls)`).

Les flux **TS purs** restent à couvrir par proxy (Réglages › Lecture) ; le direct en m3u8 (cas
majoritaire Xtream) fonctionne sans rien configurer.

## Paquets

### Tizen (`tizen/`)

- `config.xml` : widget `com.ultratv.app` (paquet `com.ultratv`), `required_version="5.5"`,
  profil `tv`, accès réseau (`access origin="*"` + privilège `internet`, sans quoi toute requête
  est bloquée), privilège `tv.inputdevice`, plein écran 1080p, pas de tâche de fond ; version réécrite depuis `VERSION`.
- `icons/app_icon.png` (512×512), rendue depuis le logo (`tools/tv-icons.mjs`).
- `scripts/build.mjs` : build `web:tv` → assemblage → `.wgt`. Sans CLI Tizen Studio : zip non
  signé (un `.wgt` est un zip). Avec : `tizen package`, signé si `TIZEN_SECURITY_PROFILE`.

Install/test sur TV : certificats Samsung (auteur + distributeur lié au DUID) puis `tizen
install` / Device Manager — pas-à-pas dans `tizen/README.md`. Publication boutique via **Samsung
Seller Office**.

### webOS (`webos/`)

- `appinfo.json` : `com.ultratv.app`, type `web`, `uiRevision: 2`, `requiredACG: []` (l'app ne
  fait aucun appel Luna) ; version réécrite depuis `VERSION`.
- Icônes (`icon` 80, `largeIcon` 160, `bgImage` 960×568, `splashBackground` 1920×1080).
- `scripts/build.mjs` : build `web:tv` → assemblage → `.ipk` via
  `ares-package` (`@webos-tools/cli@3.2.6` via npx, Node 20+).

Install/test sur TV : app **Developer Mode** (compte LG Developer) puis `ares-install` —
pas-à-pas dans `webos/README.md`. Publication boutique via **LG Seller Portal**.

## CI (`.github/workflows/tv.yml`)

- Push/PR : `.wgt` (non signé) et `.ipk` en artefacts de vérification.
- Tag `tv-vX.Y.Z` (doit égaler `VERSION`) : les deux binaires sont joints à la GitHub Release
  (jamais « Latest », même règle que desktop-release.yml).

## Regénérer les icônes TV

```bash
node tools/tv-icons.mjs    # depuis electron/build/icon.svg (PNG commités, cf. convention bureau)
```

## Limites

- Enregistrements, notifications système, DNS personnalisé, mise à jour intégrée : ponts natifs
  Android/Electron inexistants sur TV (fonctions masquées d'elles-mêmes).
- Les TV 2020 sont le plancher : les modèles 2016–2019 ne sont pas couverts (moteurs trop vieux).
- Pas de test réel sur TV possible depuis le CI : installation manuelle (certificats/DUID côté
  Samsung, Developer Mode côté LG) avant publication boutique.
