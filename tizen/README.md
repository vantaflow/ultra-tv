# Ultra TV pour Samsung TV (Tizen)

Même application que sur Android et le bureau : la web app (`../web`) est compilée en mode TV
(cible ES2017, Chromium 76+) et empaquetée en **widget Tizen** (`.wgt`).

- **Appareils** : TV Samsung 2020 et plus (Tizen 5.5, 6.0, 6.5, 7.0, 8.0). Les TV 2016–2019
  (Chromium 47–66) sont hors cible : pas de `Promise.allSettled` (Chromium 76) et interface web
  trop lourde pour leurs moteurs.
- **Identifiants** : `com.ultratv.app` / paquet `com.ultratv` (à aligner sur le compte Seller
  Office le jour de la publication boutique).

## Build

```bash
node scripts/build.mjs            # → dist/UltraTV-Tizen-<version>.wgt (non signé sans Tizen Studio)
```

Avec Tizen Studio installé (CLI détectée automatiquement, ou `TIZEN_STUDIO_HOME=/chemin/vers/tizen-studio`) :

```bash
node scripts/build.mjs                              # .wgt non signé
TIZEN_SECURITY_PROFILE=<profil> node scripts/build.mjs   # .wgt signé par le profil de certificats
```

## Installation sur un TV (test)

Le `.wgt` doit être **signé** : un certificat auteur (compte Samsung) et un certificat
distributeur lié au **DUID** de votre TV (jusqu'à 20 DUID par certificat distributeur).

1. Installez [Tizen Studio](https://developer.samsung.com/smart-tv/develop/tools/download/) avec
   le paquet *TV* (v3.0 minimum requis par le SDK ; le compilateur du certificat demande un compte
   Samsung gratuit).
2. Certificate Manager (`Device Manager` > `Certificate` ou `Tools > Certificate Manager`) :
   créez un **certificat auteur Samsung** (mot de passe), puis un **certificat distributeur** en
   ajoutant le DUID de votre TV (Réglages TV > Général > Gestionnaire de périphériques >
   Informations TV, ou via Device Manager connecté au TV).
3. Connectez le TV au PC : réseau local + mode « Apps en cours de développement » activé
   (Apps > tapez `12345` dans la barre du store > mot de passe du TV) puis
   `Device Manager > Remote Device Manager` (adresse IP du TV).
4. Package du widget puis installation :

```bash
cd tizen
TIZEN_SECURITY_PROFILE=<profil> node scripts/build.mjs
~/tizen-studio/tools/ide/bin/tizen install -n dist/UltraTV-Tizen-<version>.wgt -t <nom-du-tv>
~/tizen-studio/tools/ide/bin/tizen run -t <nom-du-tv> -p com.ultratv.app
```

Ou graphiquement : `Tizen Studio > Device Manager > Install` (clic droit sur le `.wgt`).

## Publication (Samsung Apps TV)

Inscription [Samsung Seller Office](https://seller.samsung.com) → produit « Smart TV » →
binaire `.wgt` **signé par les certificats Samsung de distribution** (pas les vôtres) : suivez le
parcours de soumission TV (fiche, captures, questionnaire de contenu). L'app doit être revue avant
publication. Un compte vendeur validé (souvent 2–4 semaines) est nécessaire.

## Limites connues

- **CORS** : hls.js et mpegts.js chargent les segments via XHR, donc exigent un proxy CORS (Réglages
  › Lecture) comme dans un navigateur. Le **HLS natif** (lecteur du TV) est ajouté en dernier
  recours par le moteur et lit la plupart des flux m3u8 sans proxy — les flux TS purs restent à
  couvrir par proxy.
- **Enregistrements, notifications système, DNS personnalisé, mise à jour intégrée** : propres à
  Android/Electron, absents ici (aucun pont natif sur TV).
- Le lancement automatique au démarrage du TV n'est pas pris en charge par les widgets web.
