#!/usr/bin/env bash
# Construit le décodeur audio FFmpeg de Media3 en version LGPL (sans --enable-gpl), pour remplacer
# org.jellyfin.media3:media3-ffmpeg-decoder, publié sous GPL-3.0 (incompatible avec la licence MIT d'Ultra TV
# pour l'APK distribué). Résultat : android-native/app/libs/media3-decoder-ffmpeg-lgpl.aar, que build.gradle.kts
# utilise automatiquement s'il est présent.
#
# Prérequis : JDK 17, Android SDK (ANDROID_HOME) avec le NDK $NDK_VER, git, make.
# Usage : android-native/tools/ffmpeg-lgpl/build.sh [version_media3]
set -euo pipefail

MEDIA3_VER="${1:-$(sed -n 's/^media3 = "\(.*\)"/\1/p' "$(dirname "$0")/../../gradle/libs.versions.toml")}"
FFMPEG_BRANCH="${FFMPEG_BRANCH:-release/6.0}"
NDK_VER="${NDK_VER:-26.1.10909125}"
MIN_API="${MIN_API:-23}"
# Décodeurs audio utiles aux flux IPTV (liste de Jellyfin + opus et vorbis) : tous LGPL dans FFmpeg.
DECODERS=(flac alac pcm_mulaw pcm_alaw mp3 aac ac3 eac3 dca mlp truehd opus vorbis)

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-$ROOT/build/ffmpeg-lgpl}"
NDK="${ANDROID_HOME:?ANDROID_HOME non défini}/ndk/$NDK_VER"
[[ -d "$NDK" ]] || { echo "NDK $NDK_VER absent ($NDK)"; exit 1; }
case "$(uname -s)" in Darwin) HOST=darwin-x86_64 ;; *) HOST=linux-x86_64 ;; esac

mkdir -p "$WORK"
[[ -d "$WORK/media" ]] || git clone --depth 1 --branch "$MEDIA3_VER" https://github.com/androidx/media.git "$WORK/media"
[[ -d "$WORK/ffmpeg" ]] || git clone --depth 1 --branch "$FFMPEG_BRANCH" https://git.ffmpeg.org/ffmpeg.git "$WORK/ffmpeg"

MOD="$WORK/media/libraries/decoder_ffmpeg/src/main"
# Garde-fou licence : le script de Media3 ne doit activer ni GPL ni nonfree.
if grep -nE -- '--enable-(gpl|nonfree)' "$MOD/jni/build_ffmpeg.sh"; then
  echo "build_ffmpeg.sh active GPL/nonfree : arrêt (on veut un FFmpeg LGPL)"; exit 1
fi
ln -sfn "$WORK/ffmpeg" "$MOD/jni/ffmpeg"
(cd "$MOD/jni" && ./build_ffmpeg.sh "$MOD" "$NDK" "$HOST" "$MIN_API" "${DECODERS[@]}")

# Vérification après coup : FFmpeg configuré sans GPL.
for cfg in "$WORK"/ffmpeg/config.h "$MOD"/jni/ffmpeg/android-libs/*/config.h; do
  [[ -f "$cfg" ]] || continue
  if grep -q '#define CONFIG_GPL 1' "$cfg"; then echo "FFmpeg compilé en GPL ($cfg) : arrêt"; exit 1; fi
done

(cd "$WORK/media" && ./gradlew --no-daemon :lib-decoder-ffmpeg:assembleRelease)
AAR="$(find "$WORK/media" -path '*outputs/aar/*' -name '*ffmpeg*release*.aar' | head -1)"
[[ -n "$AAR" ]] || { echo "AAR introuvable après la compilation"; exit 1; }
mkdir -p "$ROOT/app/libs"
cp "$AAR" "$ROOT/app/libs/media3-decoder-ffmpeg-lgpl.aar"
(cd "$WORK/ffmpeg" && git rev-parse HEAD) > "$ROOT/app/libs/media3-decoder-ffmpeg-lgpl.ffmpeg-commit.txt"
echo "OK : $ROOT/app/libs/media3-decoder-ffmpeg-lgpl.aar (Media3 $MEDIA3_VER, FFmpeg $FFMPEG_BRANCH, LGPL)"
