# Composants tiers

Le code d'Ultra TV est sous licence MIT (voir `LICENSE`). L'application Android distribuée embarque aussi les
bibliothèques ci-dessous, chacune sous sa propre licence.

## Lecture (Android)

| Composant | Licence | Remarque |
|---|---|---|
| AndroidX Media3 (ExoPlayer) | Apache-2.0 | moteur de lecture principal |
| Décodeur audio FFmpeg de Media3 (`androidx.media3.decoder.ffmpeg`) | Apache-2.0 (extension) + FFmpeg LGPL-2.1+ | voir ci-dessous |
| LibVLC (`org.videolan.android:libvlc-all`) | LGPL-2.1+ (libVLC) | second moteur ; certains modules VLC ont leur propre licence, voir videolan.org |

### FFmpeg

Le décodeur audio d'ExoPlayer (AC-3, E-AC-3, DTS, TrueHD…) repose sur FFmpeg. L'APK distribué embarque la version **LGPL** :
`android-native/app/libs/media3-decoder-ffmpeg-lgpl.aar`, soit l'extension Media3 (Apache-2.0, `androidx.media3.decoder.ffmpeg`)
et la bibliothèque native `libffmpegJNI.so` (armeabi-v7a, arm64-v8a, x86, x86_64) liée à FFmpeg.

- **Licence** : FFmpeg est configuré **sans** `--enable-gpl` ni `--enable-nonfree` ; il est donc sous **LGPL-2.1+**.
- **Version** : FFmpeg, branche `release/6.0`, commit `573f2b81ebd97da81def2ba762e81b0328547004`
  (fichier `android-native/app/libs/media3-decoder-ffmpeg-lgpl.ffmpeg-commit.txt`), extension Media3 1.11.1.
- **Décodeurs inclus** : flac, alac, pcm_mulaw, pcm_alaw, mp3, aac, ac3, eac3, dca (DTS), mlp, truehd, opus, vorbis.
- **Code source** : FFmpeg sur https://git.ffmpeg.org/ffmpeg.git (commit ci-dessus) ; extension Media3 sur
  https://github.com/androidx/media ; script de construction dans ce dépôt (`android-native/tools/ffmpeg-lgpl/build.sh`).
- **Reconstruire ou remplacer** : lancer `android-native/tools/ffmpeg-lgpl/build.sh` (le script s'arrête si `--enable-gpl` ou
  `--enable-nonfree` apparaît) ou le workflow GitHub « FFmpeg LGPL » (artefact `media3-decoder-ffmpeg-lgpl`), puis déposer
  le `.aar` dans `android-native/app/libs/`. Tout le nécessaire pour recompiler et relier une version modifiée est public.
- **Repli de développement** : si le `.aar` est absent, le build retombe sur `org.jellyfin.media3:media3-ffmpeg-decoder`,
  publié sous GPL-3.0. Ce repli sert uniquement au développement : un APK ainsi construit ne doit pas être diffusé.

## Application de bureau (Windows, Linux)

| Composant | Licence | Remarque |
|---|---|---|
| ffmpeg minimal, **LGPL-2.1+** (FFmpeg 7.1, `electron/scripts/build-ffmpeg-min.sh`) | LGPL-2.1+ | embarqué dans `resources/ffmpeg` (~3 Mo), exécuté comme programme séparé pour convertir l'audio AC-3 / E-AC-3 / DTS en AAC. Compilé sans `--enable-gpl` ni `--enable-nonfree` (le script s'arrête sinon) ; TLS : Schannel (Windows), OpenSSL 3 du système (Linux). Licence, source et commit dans `resources/ffmpeg/LICENSE.txt` et `SOURCE.txt`. |

macOS : pas de ffmpeg embarqué ; celui du système est utilisé s'il est installé (Homebrew).

## Données et services

- **TMDB** : affiches, synopsis, distribution, filmographies. *This product uses the TMDB API but is not endorsed or
  certified by TMDB.*
- **OMDb** : notes IMDb / Rotten Tomatoes / Metacritic (clé du Worker, facultative).
- **TheIntroDB** : passages intro / résumé / générique (base communautaire, sans clé).
- **Trakt**, **OpenSubtitles** : via le Worker, comptes de l'utilisateur.

## Polices

Sora et Manrope : SIL Open Font License 1.1.
