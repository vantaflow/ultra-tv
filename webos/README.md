# Ultra TV pour LG TV (webOS)

Même application que sur Android et le bureau : la web app (`../web`) est compilée en mode TV
(cible ES2017, Chromium 79+) et empaquetée en **application webOS** (`.ipk`).

- **Appareils** : TV LG avec webOS 6 (2021) et plus ; webOS 5 (2020) en meilleur effort.
  Les webOS 3–4.5 (2016–2019) sont hors cible.
- **Identifiants** : `com.ultratv.app` (à aligner sur le compte Seller Portal le jour de la
  publication boutique).

## Build

```bash
node scripts/build.mjs    # → dist/UltraTV-webOS-<version>.ipk (ares-package via npx, Node 20+)
```

Le CLI webOS (`@webos-tools/cli@3.2.6`) est téléchargé par npx au premier lancement. Pour
l'installer en dur : `npm i -g @webos-tools/cli`.

## Installation sur un TV (test)

1. Installez l'app **Developer Mode** sur le TV (LG Content Store) et connectez-la à votre compte
   [LG Developer](https://webostv.developer.lge.com) (gratuit). Activez `Dev Mode` et `Key Server`
   dans l'app ; la session expire (50 h sur webOS 6+), il faut la renouveler.
2. Depuis le PC (le CLI gère les clés Novacom automatiquement) :

```bash
npx -y -p @webos-tools/cli ares-setup-device          # nom = tv, adresse = IP du TV, passphrase = celle de Dev Mode
npx -y -p @webos-tools/cli ares-install --device tv webos/dist/UltraTV-webOS-<version>.ipk
npx -y -p @webos-tools/cli ares-launch --device tv com.ultratv.app
```

Ou graphiquement : `ares-launch`/`ares-install` en une fois via
[webOS TV SDK](https://webostv.developer.lge.com/develop/tools/sdk-overview) (Visual Studio Code).

## Publication (LG Content Store)

Inscription [Seller Portal](https://seller.lgappstv.com) → produit « TV App » → binaire `.ipk`,
fiche, captures, questionnaire. LG revoit l'app (compatible télécommande, conformité UI) puis la
publie dans le Content Store. Un compte vendeur validé est nécessaire (vérification d'entreprise).

## Limites connues

- **CORS** : hls.js et mpegts.js chargent les segments via XHR, donc exigent un proxy CORS (Réglages
  › Lecture) comme dans un navigateur. Le **HLS natif** (lecteur du TV) est ajouté en dernier
  recours par le moteur et lit la plupart des flux m3u8 sans proxy — les flux TS purs restent à
  couvrir par proxy.
- **Enregistrements, notifications système, DNS personnalisé, mise à jour intégrée** : propres à
  Android/Electron, absents ici (aucun pont natif sur TV).
- En mode développeur, l'app installée disparaît à l'expiration de la session (50 h) : relancer
  Key Server puis réinstaller.
