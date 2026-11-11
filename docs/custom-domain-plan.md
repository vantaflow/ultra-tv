# Plan : nom de domaine personnalisé pour le Worker

Statut : **plan, rien n'est encore fait.** Retour utilisateur (P2) : certaines box et certains réseaux bloquent `*.workers.dev`. L'appli affiche alors « Cloud injoignable » : appairage, envoi depuis le téléphone, synchro, alertes, TMDB et Trakt ne passent plus. Constaté sur une box de test.

## Ce qui existe déjà

L'adresse du Worker est déjà configurable partout, il n'y a donc pas de refonte à prévoir :

| Où | Réglage |
|---|---|
| Android, à la compilation | `ULTRA_WORKER_URL` → `BuildConfig.WORKER_URL` (`android-native/app/build.gradle.kts`) |
| Android, par l'utilisateur | Réglages › Sources › *Adresse du tableau de bord* (`workerBaseUrl`) |
| Bureau | `DEFAULT_WORKER` (`web/src/cloud/client.ts`), modifiable dans les réglages |
| Trakt | l'adresse de retour suit l'origine de la requête (`${url.origin}/trakt/callback`, `cloudflare-config/src/index.js`) |

## Étapes

1. **Acheter un domaine** court et neutre, chez Cloudflare Registrar (prix coûtant) pour que la zone soit déjà sur Cloudflare.
2. **Custom Domain du Worker** : `wrangler.toml` → `routes = [{ pattern = "cfg.<domaine>", custom_domain = true }]`, puis `wrangler deploy`. Le certificat est fourni automatiquement.
3. **Garder `workers.dev` actif** (`workers_dev = true`). Les appareils déjà appairés continuent de fonctionner sans rien changer.
4. **Repli automatique dans les applis** : essayer d'abord le domaine, puis `workers.dev` si le domaine ne répond pas (ou l'inverse pour les installations existantes), et retenir l'adresse qui répond. Le jeton d'appareil est le même, puisque c'est le même Worker et le même KV.
5. **Trakt** : ajouter `https://cfg.<domaine>/trakt/callback` aux adresses de retour autorisées de l'application Trakt, en plus de l'adresse actuelle.
6. **QR codes et liens** : `/envoyer`, `/apk` et `/download/*` sur le nouveau domaine (`BuildConfig.WORKER_URL`, `DEFAULT_WORKER`). Mettre à jour le code Downloader, le README, le site et les réponses épinglées.
7. **Sécurité** : vérifier `sameOrigin` et la CSP des pages du Worker avec la nouvelle origine. Les cookies de session sont liés à l'hôte : un compte connecté sur `workers.dev` devra se reconnecter une fois sur le nouveau domaine.

## Coût

Environ 10 $ par an pour un `.com` chez Cloudflare. Le Custom Domain d'un Worker est gratuit.

## Décision attendue

Le nom de domaine à acheter.
