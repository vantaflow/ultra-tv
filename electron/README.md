# Ultra TV - application bureau (Electron)

Coquille Electron autour de l'application web (`../web`, build dans `web/dist`).
Cibles : macOS (.dmg + .zip universels), Windows (installeur NSIS x64), Linux (AppImage x64, bonus).

## Choix d'Electron : version officielle, pas le fork castlabs

- Le Chromium officiel embarque deja H.264/AAC ; le HEVC passe par le decodeur de la plateforme
  (VideoToolbox, D3D11, VAAPI) active par `--enable-features=PlatformHEVCDecoderSupport`.
- Le fork castlabs (`github:castlabs/electron-releases#...+wvcus`) s'installe depuis GitHub (l'installation a
  d'ailleurs echoue sur la machine de dev : "git dep preparation failed"), et impose la signature EVS de
  castlabs pour la production sur macOS : incompatible avec un .dmg universel non signe en CI.
- AC3/EAC3/MKV ne sont donc pas decodes nativement ; si le besoin se confirme, le fork pourra revenir
  (voir plus bas) sans toucher au reste du code.

## Developpement

```bash
cd web && npm ci && npm run dev        # serveur Vite sur http://localhost:5173
cd electron && npm ci && npm run dev   # SV_DEV=1 : charge le serveur Vite, DevTools detachees
npm test                               # tests node (proxy, chemins app://, secrets)
```

## Build

```bash
cd web && npm ci && npm run build      # produit web/dist
cd ../electron && npm ci
npm run package:mac      # release/UltraTV-<v>-mac-universal.{dmg,zip}
npm run package:win      # release/UltraTV-<v>-win-x64.exe   (depuis Windows)
npm run package:linux    # release/UltraTV-<v>-linux-x64.AppImage
```

`npm run prepare:web` copie `web/dist` vers `electron/web-dist` (ignore par git ; `build.files` ne sait pas
pointer hors du dossier). `WEB_DIST=/chemin` permet d'utiliser un autre dossier.

Icones : `npm run icons` regenere `build/icon.{icns,ico,png}` et `icon-512.png` depuis `build/icon.svg`
(`@resvg/resvg-js` + `png2icons`). Les icones generees sont commitees ; le CI ne les regenere pas.

## Securite

- `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`, `webSecurity: true`, pas de `<webview>`.
  Plus de `ignore-certificate-errors` global, plus d'en-tetes CSP/X-Frame supprimes.
- L'application est servie par le protocole privilegie `app://ultratv/` (jamais `file://`) depuis l'asar :
  chemins verifies (aucun `..`, aucun `\`, fallback SPA vers `index.html` uniquement pour les routes).
  En dev, elle vient du serveur Vite.
- CSP stricte sur notre origine uniquement (voir `CSP` dans `main.cjs`) : scripts `'self'`, medias/images/connexions
  limites a `'self'` et `http://127.0.0.1:*` (le proxy), `object-src 'none'`, `frame-ancestors 'none'`.
- Navigation verrouillee (`will-navigate`, `will-redirect`) ; `window.open` http(s) => navigateur systeme,
  le reste est refuse. Permissions refusees sauf `fullscreen` et `clipboard-sanitized-write`.
- Tous les canaux IPC verifient l'expediteur (frame principale, origine `app://ultratv` ou serveur de dev).
- Fuses Electron : `runAsNode` et `NODE_OPTIONS` desactives, chargement depuis l'asar uniquement.
- Aucune URL de source ni identifiant n'est journalise (seuls des codes d'erreur).

### Proxy loopback (`proxy.cjs`)

Les fournisseurs IPTV n'envoient pas d'en-tetes CORS et servent souvent en `http://` : le renderer passe par un
proxy HTTP lie a `127.0.0.1`, port aleatoire, jeton aleatoire genere a chaque demarrage.

```
http://127.0.0.1:<port>/<token>/<base64url(urlCible)>
```

- GET / HEAD / POST vers http(s) uniquement ; la cible peut etre en LAN, mais pas le proxy lui-meme (boucle).
- `X-UT-UA` et `X-UT-Referer` envoyes par la page deviennent `User-Agent` et `Referer` en amont ;
  `Range`, `If-Range`, `Accept*`, `Content-Type` sont transmis.
- Redirections suivies cote main (5 max). Corps streame avec backpressure ; la fermeture du client annule l'amont.
- Manifestes HLS (type `mpegurl`, extension `.m3u8`, ou corps `#EXTM3U` contenant des balises `#EXT-X-`,
  2 Mo max) : chaque URL (lignes et `URI="..."`) est resolue contre l'URL finale puis re-enveloppee. Une simple
  liste de chaines M3U n'est pas reecrite.
- CORS : `Access-Control-Allow-Origin: app://ultratv` (ou l'origine de dev), `Allow-Headers`, `Expose-Headers:
  Content-Length, Content-Range, Accept-Ranges` ; requetes `OPTIONS` repondues ; toute autre origine => 403.
- Tolerance TLS (certificats expires/auto-signes) : agent HTTPS dedie a ce module, jamais globale.

### Secrets

`window.ultratv.encrypt/decrypt` utilisent `safeStorage` (format `enc:v1:<base64>`). Si le stockage securise est
indisponible (ou Linux `basic_text`), le format est `plain:v1:<base64>` (simple encodage, sans protection) et
`secretsSecure` vaut `false` : l'UI doit en avertir l'utilisateur.

## Mise a jour automatique

`electron-updater` (GitHub Releases, `khalilbenaz/ultra-tv`) : verification 5 s apres le lancement (hors dev et
`ULTRATV_HEADLESS`), telechargement automatique, installation a la fermeture. Le statut est pousse au renderer
(`onUpdateStatus`) : `checking | available | not-available | downloading | downloaded | unavailable | error`.

- La release doit etre **publiee** (le workflow cree un brouillon) pour etre vue par les clients.
- **macOS non signe** : Squirrel.Mac refuse d'installer une mise a jour non signee. L'application detecte
  l'absence d'identite (`codesign`) et passe en etat `unavailable` sans planter ; l'utilisateur telecharge alors
  le nouveau .dmg a la main. Il en va de meme en dev.
- Windows (NSIS) et AppImage se mettent a jour sans signature (SmartScreen peut avertir a l'installation).

## Signature (optionnelle)

Le workflow ne signe que si les secrets existent ; sinon le build est non signe. Sur macOS, `scripts/adhoc-sign.cjs`
(hook `afterSign`) applique alors une signature ad hoc : sans elle, le bundle modifie est tue au lancement sur
Apple Silicon. Ce n'est pas une vraie signature : au premier lancement, clic droit > Ouvrir.

| Plateforme | Secrets GitHub |
| --- | --- |
| macOS | `MAC_CSC_LINK` (.p12 en base64), `MAC_CSC_KEY_PASSWORD`, `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` (notarisation) |
| Windows | `WIN_CSC_LINK`, `WIN_CSC_KEY_PASSWORD` |

## Publication

`.github/workflows/desktop-release.yml` : tag `desktop-vX.Y.Z` (doit egaler la version de `electron/package.json`)
ou lancement manuel (artefacts seulement). Sur tag, le job `release` cree une release **brouillon** avec les
installeurs et les `latest*.yml` utilises par electron-updater (via `gh`, sans action tierce).

## Variables d'environnement (tests / diagnostic)

| Variable | Effet |
| --- | --- |
| `SV_DEV=1` | Charge le serveur Vite (`ULTRATV_DEV_URL`, defaut `http://localhost:5173`), pas de CSP injectee |
| `ULTRATV_HEADLESS=1` | Fenetre masquee (`show:false`), pas de verification de mise a jour |
| `ULTRATV_USER_DATA=<dossier>` | Profil isole (`app.setPath('userData')`) |
| `ULTRATV_DEVTOOLS=1` | Autorise les DevTools en production |

Pilotage par Playwright / CDP : lancer le binaire avec `--remote-debugging-port=<port>`.

## Retour eventuel au fork castlabs

Remplacer la devDependency `electron` par `github:castlabs/electron-releases#v33.4.11+wvcus`, puis prevoir la
signature EVS (macOS/Windows) pour distribuer avec Widevine ; l'installation depuis GitHub doit etre testee en CI.

## Cloud

Le processus principal expose `window.ultratv.cloudRequest({url, method, headers, body})`
(`cloudfetch.cjs`) pour joindre le Worker de configuration : HTTPS obligatoire (HTTP seulement vers
la boucle locale, pour les tests), redirections jamais suivies, seuls les en-têtes `Authorization`,
`Content-Type`, `Accept` et `If-None-Match` passent, délai de 15 s, réponse limitée à 1 Mo. Rien
n'est journalisé. Voir `../web/README.md` pour le protocole.

## Tests de bout en bout

Les parcours Playwright `_electron` utilisent `ULTRATV_USER_DATA` (profil isolé). Attention : avec
`ULTRATV_HEADLESS=1` (fenêtre masquée) Chromium bride les minuteurs et les `requestAnimationFrame`
du renderer, ce qui ralentit fortement les tests ; préférer une fenêtre visible en CI locale.
