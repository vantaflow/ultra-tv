<p align="center">
  <img src="docs/screenshots/en/banner.png" alt="Ultra TV" width="100%" />
</p>

<h1 align="center">Ultra TV</h1>

<p align="center">
  <strong>Native IPTV player for Android TV and Google TV.</strong><br/>
  Kotlin · Compose for TV · Media3 (ExoPlayer) + LibVLC · Room · Hilt
</p>

<p align="center">
  <a href="https://github.com/khalilbenaz/ultra-tv/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/khalilbenaz/ultra-tv/actions/workflows/ci.yml/badge.svg" /></a>
  <a href="https://github.com/khalilbenaz/ultra-tv/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/khalilbenaz/ultra-tv?label=release" /></a>
  <a href="LICENSE"><img alt="MIT licence" src="https://img.shields.io/badge/licence-MIT-0284c7" /></a>
  <a href="https://khalilbenaz.github.io/ultra-tv/en/"><img alt="Site" src="https://img.shields.io/badge/site-GitHub%20Pages-d91e2b" /></a>
</p>

<p align="center">
  <a href="README.md">🇫🇷 Version française</a> ·
  <a href="https://khalilbenaz.github.io/ultra-tv/en/">Website</a> ·
  <a href="https://github.com/khalilbenaz/ultra-tv/releases/latest/download/UltraTV-debug.apk">Download the APK</a> ·
  <a href="https://github.com/khalilbenaz/ultra-tv/releases">Releases</a> ·
  <a href="CHANGELOG.md">Changelog (French)</a>
</p>

---

Ultra TV plays your own IPTV subscriptions (**Xtream Codes**, **M3U / M3U8** as a link or a file). The whole interface is native and built for the remote; the catalogue (channels, movies, series, guide, history, favourites) lives in a local Room database. It provides **no content** at all.

> The screenshots below use **synthetic data only** (a local fake Xtream server, `android-native/tools/fake-xtream`). Channel and programme names come from that data set, not from the app.

<p align="center">
  <img src="docs/screenshots/en/home.png" alt="Home" width="48%" />
  <img src="docs/screenshots/en/live.png" alt="Live TV" width="48%" />
  <img src="docs/screenshots/en/guide.png" alt="TV guide" width="48%" />
  <img src="docs/screenshots/en/detail.png" alt="Movie details" width="48%" />
  <img src="docs/screenshots/en/player.png" alt="Player" width="48%" />
  <img src="docs/screenshots/en/settings.png" alt="Settings" width="48%" />
  <img src="docs/screenshots/en/profiles.png" alt="Who's watching?" width="48%" />
  <img src="docs/screenshots/en/languages.png" alt="Settings, Languages panel" width="48%" />
</p>

The same screenshots exist in French ([`docs/screenshots/fr`](docs/screenshots/fr)) and in Arabic, with a mirrored interface ([`docs/screenshots/ar`](docs/screenshots/ar)).

## Features

- **Search** from any screen: a button at the top of the side rail, plus the remote's Search and microphone keys.
- **Live TV**: active categories, language filter from the first source, section separators, quality badges, number zapping, back to the previous channel, 20 recent channels, instant search (FTS).
- **Guide**: time grid, reminders, scheduled recordings, **replay** (Xtream catch-up) from the guide when the source allows it.
- **Live pause** (timeshift): circular disk buffer for MPEG-TS streams.
- **Movies and series**: rich details through **TMDB** (poster, plot, cast), resume playback, seasons as tabs.
- **Two playback engines**: ExoPlayer (Media3) and **LibVLC**; Auto / ExoPlayer / VLC, decoding Auto / Hardware / Software, automatic fallback and per-channel memory.
- **Subtitles**: online search (OpenSubtitles through the Worker), advanced style (size, colour, background, outline, position, offset).
- **Profiles**: "Who's watching?", Kids profile, per-profile favourites, history and languages.
- **Google TV**: Watch Next, Favourites channel, voice and global search, `ultratv://` deep links.
- **Themes** Dark / Light / Automatic (the player always stays dark); interface in English, French, Spanish and Arabic (RTL).
- **Sleep**: 30 / 60 / 90 min timer or end of programme; built-in update from GitHub releases.

## Installation

Android 9 or newer (API 28).

**APK.** Download [`UltraTV-debug.apk`](https://github.com/khalilbenaz/ultra-tv/releases/latest/download/UltraTV-debug.apk) (SHA-256 sum on the release page) and install it. Lighter per-processor builds (`UltraTV-<version>-arm64-v8a.apk`, `armeabi-v7a`, `x86_64`) are published with a `SHA256SUMS.txt`; the in-app updater picks the right one for your device.

**Downloader** (boxes without a browser): open the [Downloader](https://www.aftvnews.com/downloader/) app, enter the code **`5248504`**, allow unknown sources, install.

**adb**:

```bash
adb connect BOX_IP:5555
adb install -r UltraTV-debug.apk
```

On first launch: choose the source type (Xtream Codes, M3U link, M3U file or **From the cloud**), enter it, then tick your languages (only the matching categories are downloaded).

An address like `…/get.php?username=…&password=…` pasted as an M3U link is recognised and added as an Xtream Codes source (many providers block `get.php`).

## Cloud sync

To avoid typing credentials with a remote, manage your sources from a browser: **<https://ultratv-config.khalilbenaz.workers.dev>** (the address is also shown in the app, with a QR code: Settings › Sources).

1. Create an account on the dashboard and add your sources.
2. In the dashboard, choose **Pair a TV**.
3. On the box: **From the cloud** (Source step of the wizard) or Settings › Sources › *Sync from the cloud*. An 8-character code is displayed; enter it in the dashboard.
4. The box imports the configuration, then syncs.

The device receives a random 256-bit token (hashed on the server, encrypted in the Keystore, revocable). Your credentials are **encrypted on the server side** (AES-256-GCM). The label shown for the box ("UTV-XXXXXX") is only a name, never a key.

**Self-hosting.** You can deploy your own Worker (see [Cloudflare Worker](#cloudflare-worker)): create the KV namespaces, set the `SESSION_SECRET`, `PROVIDER_ENC_KEY` and `OPS_TOKEN` secrets (no value is published here), then `wrangler deploy`; in the app, Settings › Sources › *Dashboard address* then points to yours.

## Security

- Source credentials **encrypted at rest** on the device; AES-256-GCM on the Worker.
- **No stream URL and no credential is displayed** or logged; error messages are filtered (`UserText`). An empty source name does not fall back to the server address.
- No shared token in the APK. Details, threat model and rotation of old secrets: [SECURITY.md](SECURITY.md).

## Automatic tuning

On first launch, the **`AdaptiveProfile`** measures the device (memory, heap, micro-benchmark) and rates it **Low / Medium / High**. From that it derives buffer, parallelism and sync batch size, resolution and bitrate caps (low-memory boxes: 720p / 6 Mb/s) and adjusts the buffer if drop-outs occur. Everything stays adjustable: Settings › Playback (engine, decoding, buffer preset: Low latency, Auto, Balanced, Stable, Custom).

## Development

JDK 17 required.

```bash
git clone https://github.com/khalilbenaz/ultra-tv && cd ultra-tv/android-native
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
./gradlew assembleDebug                            # universal APK
./gradlew testDebugUnitTest                        # unit tests (JUnit, Robolectric)
./gradlew assembleRelease                          # per-ABI APKs (arm64-v8a, armeabi-v7a, x86_64)
```

- The version comes from the [`VERSION`](VERSION) file; `versionCode = major×10000 + minor×100 + patch` (1.1.0 → 10100), identical for every ABI.
- **Fake Xtream server** to test without a subscription: `python3 android-native/tools/fake-xtream/server.py` (the emulator reaches it at `http://10.0.2.2:8099`, credentials `test` / `test`); `demo.py` serves the synthetic data set used for the screenshots.
- Debug build: debug intents (`debug_route`, `debug_theme`, `debug_lang`, `debug_engine`, `debug_decoder`, `debug_buffer`…) for screenshots and measurements.
- **CI** ([ci.yml](.github/workflows/ci.yml)): web (tsc + vitest), Android (compile + unit tests), Worker. Publishing: [release.yml](.github/workflows/release.yml) on a `v*` tag (universal `UltraTV-debug.apk`, per-processor `UltraTV-<version>-<abi>.apk` and `SHA256SUMS.txt`); website: [pages.yml](.github/workflows/pages.yml).

## Cloudflare Worker

`cloudflare-config/`: pairing dashboard, encrypted source storage, crash ingestion, TMDB and OpenSubtitles proxies. Full procedure: [cloudflare-config/README.md](cloudflare-config/README.md).

```bash
cd cloudflare-config && npm ci
wrangler kv namespace create CONFIG                # paste the ids into wrangler.toml
wrangler secret put SESSION_SECRET                 # >= 32 random characters
wrangler secret put PROVIDER_ENC_KEY               # AES-256 key, base64
wrangler secret put OPS_TOKEN                      # password for /crashes and /logs
wrangler secret put TMDB_READ_TOKEN                # TMDB v4 read token (rich details)
wrangler secret put TMDB_API_KEY                   # TMDB v3 key (fallback without a v4 token)
wrangler secret put OPENSUBTITLES_API_KEY          # OpenSubtitles key (online subtitles)
npm test && wrangler deploy --dry-run
```

TMDB and OpenSubtitles are optional: without their secrets, the app hides the matching features. For a fork, build with `-PULTRA_WORKER_URL=https://your-worker.workers.dev`.

## Known limitations

- **Replay** and **live pause** depend on the source (Xtream catch-up, MPEG-TS streams); unavailable otherwise.
- TMDB details and online subtitles require a Worker configured with the secrets above.
- The first sync of a very large catalogue (≈ 55,000 channels) takes about a minute on a mid-range device and longer on an entry-level one.
- The APK grows with LibVLC (≈ 53 MB in arm64-v8a, against ≈ 9 MB in 1.0.x).
- Measurements published in the release notes come from Android TV emulators (software rendering): to be confirmed on real hardware.

## Licence

MIT, see [LICENSE](LICENSE). Ultra TV is an IPTV client: only use playlists, guides and credentials you are entitled to use. The Sora and Manrope fonts are under the OFL licence.
