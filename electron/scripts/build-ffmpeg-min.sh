#!/usr/bin/env bash
# ffmpeg MINIMAL, LGPL, pour la conversion audio du bureau (transcode.cjs) : lit un flux http(s) / HLS / MKV / MP4 / TS,
# copie la vidéo, convertit l'audio (AC-3, E-AC-3, DTS, TrueHD…) en AAC et écrit du MPEG-TS. Rien d'autre :
# quelques Mo au lieu des ~140 Mo d'un build complet.
#
# Usage : scripts/build-ffmpeg-min.sh win|linux      → vendor/ffmpeg/ffmpeg(.exe) + LICENSE.txt + SOURCE.txt
#   win   : compilation croisée mingw-w64 (Ubuntu : apt install gcc-mingw-w64-x86-64 nasm), TLS natif Windows (Schannel)
#   linux : compilation native, TLS OpenSSL 3 (Apache-2.0, compatible LGPLv3 ; bibliothèque du système)
# Variables : FFMPEG_REF (branche / tag FFmpeg, défaut release/7.1), JOBS.
set -euo pipefail

TARGET="${1:-}"
[[ "$TARGET" == win || "$TARGET" == linux ]] || { echo "usage : build-ffmpeg-min.sh win|linux"; exit 2; }
REF="${FFMPEG_REF:-release/7.1}"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 4)}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/vendor/.ffmpeg-build}"
OUT="$ROOT/vendor/ffmpeg"
SRC="$WORK/src"

mkdir -p "$WORK"
[[ -d "$SRC/.git" ]] || git clone --depth 1 --branch "$REF" https://github.com/FFmpeg/FFmpeg.git "$SRC"

# Ce qu'il faut, et seulement ça.
PROTOCOLS=file,pipe,http,https,tcp,tls,hls,crypto,httpproxy
DEMUXERS=matroska,mov,mpegts,mpegtsraw,hls,avi,flv,mpegps,mpegvideo,h264,hevc,aac,ac3,eac3,dts,truehd,mp3,flac,ogg,wav
AUDIO_DECODERS=ac3,eac3,dca,truehd,mlp,aac,aac_latm,mp3,mp2,mp1,flac,opus,vorbis,alac,pcm_s16le,pcm_s24le,pcm_s32le,pcm_s16be,pcm_f32le,pcm_dvd,pcm_bluray
PARSERS=h264,hevc,mpeg4video,mpegvideo,vc1,av1,vp9,aac,aac_latm,ac3,dca,mlp,mpegaudio,flac,opus,vorbis
BSFS=h264_mp4toannexb,hevc_mp4toannexb,aac_adtstoasc,extract_extradata,vp9_superframe
FILTERS=aresample,aformat,anull,null,format,abuffer,abuffersink,buffer,buffersink,asetnsamples

COMMON=(
  --disable-everything --disable-autodetect --disable-doc --disable-debug
  --disable-programs --enable-ffmpeg --disable-ffplay --disable-ffprobe
  --disable-avdevice --disable-swscale --disable-postproc
  --enable-static --disable-shared --enable-small --enable-pic
  --enable-avformat --enable-avcodec --enable-avfilter --enable-swresample
  --enable-network
  --enable-protocol="$PROTOCOLS" --enable-demuxer="$DEMUXERS" --enable-muxer=mpegts
  --enable-decoder="$AUDIO_DECODERS" --enable-encoder=aac
  --enable-parser="$PARSERS" --enable-bsf="$BSFS" --enable-filter="$FILTERS"
)

case "$TARGET" in
  win)   EXTRA=(--target-os=mingw32 --arch=x86_64 --cross-prefix=x86_64-w64-mingw32- --enable-schannel --extra-ldflags=-static); EXE=ffmpeg.exe ;;
  linux) EXTRA=(--enable-openssl); EXE=ffmpeg ;;
esac

BUILD="$WORK/build-$TARGET"
rm -rf "$BUILD" && mkdir -p "$BUILD"
(cd "$BUILD" && "$SRC/configure" "${COMMON[@]}" "${EXTRA[@]}" >/dev/null)

# Garde-fous licence : ni GPL ni nonfree.
if grep -qE '#define CONFIG_(GPL|NONFREE) 1' "$BUILD/config.h"; then echo "configuration GPL/nonfree : arrêt"; exit 1; fi

make -C "$BUILD" -j"$JOBS" "$EXE" >/dev/null
rm -rf "$OUT" && mkdir -p "$OUT"
cp "$BUILD/$EXE" "$OUT/$EXE"
"${STRIP:-$([[ $TARGET == win ]] && echo x86_64-w64-mingw32-strip || echo strip)}" "$OUT/$EXE" || true
cp "$SRC/COPYING.LGPLv2.1" "$OUT/LICENSE.txt"
{
  echo "FFmpeg (LGPL-2.1+), build minimal pour Ultra TV (conversion audio)."
  echo "Source : https://github.com/FFmpeg/FFmpeg ($REF, commit $(git -C "$SRC" rev-parse HEAD))"
  echo "Recompiler : electron/scripts/build-ffmpeg-min.sh $TARGET"
} > "$OUT/SOURCE.txt"
echo "ffmpeg minimal prêt : $OUT/$EXE ($(du -h "$OUT/$EXE" | cut -f1))"
