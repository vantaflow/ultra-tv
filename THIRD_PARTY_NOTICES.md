# Composants tiers

Le code d'Ultra TV est sous licence MIT (voir `LICENSE`). L'application Android distribuée embarque aussi les
bibliothèques ci-dessous, chacune sous sa propre licence.

## Lecture (Android)

| Composant | Licence | Remarque |
|---|---|---|
| AndroidX Media3 (ExoPlayer) | Apache-2.0 | moteur de lecture principal |
| Décodeur audio FFmpeg de Media3 (`androidx.media3.decoder.ffmpeg`) | Apache-2.0 (extension) + FFmpeg | voir ci-dessous |
| LibVLC (`org.videolan.android:libvlc-all`) | LGPL-2.1+ (libVLC) | second moteur ; certains modules VLC ont leur propre licence, voir videolan.org |

### FFmpeg

Le décodeur AC3 / E-AC3 / DTS / TrueHD d'ExoPlayer repose sur FFmpeg.

- **Version LGPL (recommandée)** : `android-native/tools/ffmpeg-lgpl/build.sh` (ou le workflow GitHub
  « FFmpeg LGPL ») construit l'extension Media3 avec un FFmpeg configuré **sans** `--enable-gpl` ni `--enable-nonfree`
  (le script s'arrête sinon) et dépose `android-native/app/libs/media3-decoder-ffmpeg-lgpl.aar`. Quand ce fichier est
  présent, le build l'utilise. FFmpeg est alors sous LGPL-2.1+ : son code source correspond à la branche et au commit
  indiqués dans `media3-decoder-ffmpeg-lgpl.ffmpeg-commit.txt` (https://git.ffmpeg.org/ffmpeg.git) ; tout le nécessaire
  pour le recompiler et relier une version modifiée est public (ce dépôt, androidx/media, FFmpeg).
- **Repli** : sans ce fichier, le build utilise `org.jellyfin.media3:media3-ffmpeg-decoder`, publié sous **GPL-3.0**.
  Un APK construit ainsi doit être diffusé selon les termes de la GPL-3.0.

## Données et services

- **TMDB** : affiches, synopsis, distribution, filmographies. *This product uses the TMDB API but is not endorsed or
  certified by TMDB.*
- **OMDb** : notes IMDb / Rotten Tomatoes / Metacritic (clé du Worker, facultative).
- **TheIntroDB** : passages intro / résumé / générique (base communautaire, sans clé).
- **Trakt**, **OpenSubtitles** : via le Worker, comptes de l'utilisateur.

## Polices

Sora et Manrope : SIL Open Font License 1.1.
