# Ultra TV — application web et bureau

Lecteur IPTV (Xtream Codes et M3U, lien ou fichier) au design de l'application Android TV :
rail latéral de 88 px, listes, aperçu, lecteur plein écran. Le même code tourne dans un navigateur
et, empaqueté par Electron (`../electron`), sous macOS et Windows.

## Écrans

Accueil, Direct (séparateurs en en-têtes, badges de qualité et de langue, aperçu intégré),
Guide TV (grille horaire, rediffusion si le fournisseur propose l'archive), Films et Séries
(grille paginée virtualisée), fiches (`get_vod_info`, `get_series_info`, reprise de lecture),
Recherche, Favoris, Réglages (sources, affichage, lecture, synchronisation, langues et
catégories, langue de l'interface, profils, à propos), assistant de première source avec choix
des langues et écran de première synchronisation.

Thèmes clair, sombre ou automatique, couleur d'accent, interface en français, anglais, espagnol et
arabe (RTL). Polices Sora et Manrope **auto-hébergées** (aucune requête vers un CDN).

## Raccourcis

| Touche | Action |
|---|---|
| `/` ou Ctrl/Cmd + K | Recherche |
| ↑ ↓ PageHaut PageBas | Naviguer dans la liste des chaînes, Entrée pour lire |
| Espace | Lecture / pause |
| ← → | Reculer / avancer de 10 s (films, épisodes) |
| ↑ ↓ | Chaîne précédente / suivante (direct) ou volume (vidéos) |
| F | Plein écran · M son · P image dans l'image · I statistiques |
| Échap | Quitter le plein écran, fermer le panneau, revenir |

## Performance

* Catalogue dans IndexedDB (Dexie), jamais tenu en entier en mémoire : seules les pages visibles
  sont lues (`hooks/paged.ts`) et rendues (`ui/Virtual.tsx`, TanStack Virtual).
* Synchronisation dans un **Web Worker** (`sync/worker.ts`) : lecture HTTP en flux, découpage
  d'objets JSON au fil de l'eau (`net/json.ts`, tolérant aux caractères de contrôle), écriture
  par lots. 180 000 films / 48 000 séries / 55 000 chaînes ne bloquent jamais l'interface.
* Les lignes stockées sont maigres et indexées ; les URL de flux ne sont jamais stockées
  (reconstruites à la lecture depuis les identifiants chiffrés).
* **Générations de catalogue** : une resynchronisation écrit un nouveau catalogue puis bascule
  d'un coup ; l'ancien reste visible jusqu'à la bascule. Démarrage instantané depuis le cache.

## Lecteur

hls.js (HLS), mpegts.js (MPEG-TS), lecture native (MP4/MKV). Un seul `<video>` et un seul
moteur pour l'aperçu et le plein écran : une seule connexion au fournisseur. Dans Electron,
tout passe par le proxy loopback du processus principal (pas de CORS, pas de proxy distant) ;
dans un navigateur, il faut un proxy CORS (`../cloudflare`, `../vercel-proxy`, `../deno-proxy`,
`../val-town`) à renseigner dans Réglages → Lecture ou via `VITE_DEFAULT_PROXY_URL`.

## Sécurité

* Identifiants chiffrés au repos : `safeStorage` (Electron) ou AES-GCM avec clé WebCrypto non
  extractible (navigateur). Jamais journalisés, jamais affichés.
* Aucune URL de flux n'est affichée ni journalisée.

## Compte cloud (application de bureau)

Même fournisseur sur tous les appareils via le Worker `cloudflare-config` (par défaut
`https://ultratv-config.khalilbenaz.workers.dev`, modifiable dans Réglages › Cloud). Protocole
identique à celui de l'application Android (`CloudPairingClient.kt`) :

* **Appairage** : `POST /api/pair/start` donne un code à 8 caractères ; l'utilisateur le saisit sur le
  tableau de bord ; l'appli interroge `POST /api/pair/poll` puis stocke le jeton d'appareil chiffré
  (`safeStorage`). `429` ralentit l'interrogation, `401` efface le jeton.
* **Synchronisation** au lancement, toutes les 6 h et à la demande : `GET /api/config` (ETag /
  `If-None-Match`), puis fusion avec les sources locales — ajout, modification (nouvelle synchro du
  catalogue si les identifiants changent), retrait (`src/cloud/reconcile.ts`). La clé de fusion est
  l'identifiant du fournisseur côté Worker. Rotation du jeton après 90 jours.
* **Partage** d'une source locale (« Partager cette source ») : `POST /api/device/providers` avec
  `shareWith: "all" | [idsAppareils]` ; les sources locales restent privées tant qu'elles ne sont pas
  partagées. Option masquée si le Worker répond 404. Renommage de l'appareil : `PATCH /api/device`.
* Les requêtes vers le Worker sont faites par le processus principal d'Electron (IPC
  `cloudRequest` : HTTPS seulement, aucune redirection suivie, en-têtes filtrés), jamais depuis la
  page. Hors Electron (navigateur), l'option n'est pas proposée.

Les tests (`src/cloud/*.test.ts`) tournent contre un faux Worker (`src/cloud/fakeWorker.ts`).

## Développement

```bash
npm ci
npm run dev        # http://localhost:5173
npm test           # vitest
npm run build      # tsc + vite build -> dist/
```

Disposition : `src/db` (Dexie, requêtes), `src/net` (transport, Xtream, JSON en flux, secrets),
`src/sync` (worker, catalogue, guide XMLTV), `src/player` (moteur, hôte du lecteur),
`src/screens`, `src/ui`, `src/i18n`, `src/state`, `src/styles`, `src/lib` (ChannelNameParser,
TitleCleaner, M3U).

Les données de l'ancienne interface (base IndexedDB `ultratv`) ne sont pas migrées : la nouvelle
base s'appelle `ultratv-desktop`. Le support Stalker a été retiré.
