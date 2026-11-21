# Ultra TV — Security notes

## Threat model

Ultra TV is a self-hosted IPTV client. The deployment is single-user (your
TV box + your Cloudflare Worker), so the primary threats are:

1. **Credential leakage** — IPTV provider credentials shouldn't escape the
   device or the worker's storage (D1; KV only as a fallback).
2. **Brute force** — neither the local PIN nor the worker's account password
   should be cheaply guessable.
3. **Supply chain** — a malicious update shouldn't be installable over a
   legitimate one.

## Already in place

- **Network security config** pins TLS for `github.com`,
  `api.github.com`, `githubusercontent.com`, `*.khalilbenaz.workers.dev` and `*.weedy.dpdns.org` (the Worker's API address; the site itself is `ultratv.dpdns.org`),
  so the update channel and telemetry endpoints can't be downgraded.
- **Backup encryption** (AES-GCM + PBKDF2-SHA256, 120 k iterations, 256-bit
  key) — opt-in via a password field in Settings. Plain exports are still
  supported with a warning that creds ship in clear.
- **PIN brute-force throttle** — three wrong attempts in a row trigger a
  growing delay (1 s → 4 s → 16 s) before the comparison runs.
- **Worker authentication (v2)** — no anonymous reads. The TV is paired with a
  dashboard account through a short single-use code and receives a random
  256-bit device token (stored hashed on the Worker, encrypted with the Android
  Keystore on the device, revocable and rotatable). The MAC is a display label,
  never a key. See `cloudflare-config/README.md`.
- **Provider credentials encrypted on the Worker** — AES-256-GCM, key held in a
  Wrangler secret (`PROVIDER_ENC_KEY`), account id bound as AAD, key rotation
  supported.
- **Dashboard passwords** — PBKDF2-SHA256 (100 000 iterations, the Workers
  platform maximum), random salt, constant-time comparison. Per-IP and per-account
  rate limits with progressive lockout (Durable Object, strongly consistent).
- **Web hardening** — CSP with nonce (no `unsafe-inline`), HSTS, `X-Frame-Options:
  DENY`, `__Host-` SameSite=Strict session cookie, session-bound CSRF token plus
  `Origin` check, no open CORS.
- **Crash/log dashboards** — protected by the server-side secret `OPS_TOKEN`
  (never in the APK, never in a query string).
- **Telemetry ingestion** — two families of routes, all rate-limited and
  size-capped:
  - paired devices (`/api/event`, `/api/events`, `/api/crash`) authenticate
    with their device token;
  - devices without an account (`/api/anon/events`, `/api/anon/crash`, since
    1.3.72) need no token: the routes are open, capped per IP, per install and
    globally, keep at most 20 lines per batch and 16 KB per crash.
  Lines and anonymous pings whose device model is `robolectric` (unit tests)
  are acknowledged but never stored. If the database rejects a write, a crash
  gets `503` with `Retry-After` (the app keeps the file and sends it again at
  the next launch only); log lines get `200` with `dropped.storage` and are
  lost, because the app retries every `5xx` without reading `Retry-After` and a
  `503` would make the whole fleet retry in a loop during a long outage (for
  example an exhausted database quota). After a failed write, log lines skip
  the database for 60 s (circuit breaker, per Worker isolate). Responses
  report how many lines were stored and why the others were dropped
  (duplicate, sampling, invalid, over the batch cap, test device, storage),
  never their content.
- **What is stored** — per line: kind (log or crash), timestamp, level, tag,
  message or stack trace, app version, device model, Android SDK (crashes
  only), country (from Cloudflare, never the IP address), a short
  non-reversible fingerprint of the device (and, for paired devices, of the
  account: HMAC with a server secret). New lines never contain the IP address,
  the account login, the install ID or the MAC label (the `mac` column is no
  longer filled). Lines written before the current sanitiser was deployed may
  still contain such data until they are rewritten
  (`cloudflare-config/scripts/resanitize-events.mjs`) or expire, and so may the
  old D1 database kept as a fallback (`cloudflare-config/wrangler.toml`). Logs are kept 7
  days, crashes 30 days.
- **Telemetry sanitiser** — the Worker is the last barrier, including for old
  app versions that cannot be updated: every text field (message, stack, tag,
  device model, version) is sanitised on all ingestion routes before anything
  is written to the database or to the Worker's own logs
  (`cloudflare-config/src/sanitize.js`, `sanitizeLog`). Rules, as an allow-list:
  - **every URL** (`http`, `https`, `rtmp`, `rtsp`, `udp`, `mms`, `stalker:`, any
    `scheme://` at the start of a word, known schemes even when glued to a
    digit or a `%xx` sequence, percent-encoded once or twice, JSON-escaped with
    `\/` or `\u002F`) is reduced to its scheme, a host fingerprint (see below)
    and only the resource type: one known path word (`live`, `movie`,
    `series`, `timeshift`, `hls`, `get.php`, `player_api.php`, `portal.php`,
    `xmltv.php`…), the file extension (`.ts`, `.m3u8`, `.mkv`…) and `?*` if
    there was a query. Path segments, query values, fragments and
    `user:pass@` are dropped. Credentials that contain a space, a tab, a quote,
    `<`, `>`, a backslash or a backtick are followed: the words after a cut are
    absorbed while they look like the rest of the URL, and up to three words
    after a trailing `password=` / `username=` value (which may also hide a
    word of the log line that follows);
  - request lines (`GET /…/1.ts HTTP/1.1`), `host:port/path` and
    `user:pass@host` without a scheme, stray `user/pass/123.ts` tails and local
    paths with an account name (`C:\Users\…`, `/Users/…`, `/home/…`) are
    reduced too;
  - outside URLs, values of sensitive keys are replaced by `<redacted>` in JSON
    (`"password":"…"`, escaped or single-quoted too), query strings (also
    `%3D`/`%26`-encoded, `;`-separated, any case) and headers: user name,
    login, password, any `*token`, `*secret`, `*_key`, `api_key`, `auth`,
    `authorization` (Bearer, Basic, MediaBrowser), `X-Plex-Token`,
    `X-Emby-Token`, `mac`, `sn`, `device_id`, `signature`, `pin`, session ids,
    e-mail (also in XML tags and as the first item of a JSON array); whole
    `Cookie` / `Set-Cookie` / `Authorization` header lines;
  - by shape: MAC addresses (`:`/`-`/`%3A`/dotted) → `<mac>`, e-mail (also
    `%40` and non-ASCII letters) → `<email>`, IPv4 and IPv6 → `<lan>` /
    `<ip>`, host names in network errors (`Unable to resolve host`, `failed to
    connect to`, `Hostname … not verified`, `failed for`, `talking to`, `SNI`)
    → `<host#…>`, JWTs, `Bearer …`, `Basic …`, `utv_…` device tokens and
    `login failed for X / Y`.
  **Host fingerprint**: HMAC-SHA-256 of the lower-cased host name only (never
  the path or the credentials), keyed with the Worker secret `LOG_HOST_SALT`,
  truncated to 12 hex digits (`<host#…>`, `<lan#…>` for a private address). It
  groups errors of one provider without naming it; without the secret it can
  neither be reversed by brute force (even for an IPv4 address) nor matched
  against a list of known hosts. Anyone who holds the secret can. If the
  secret is missing, hosts are written `<host>` / `<lan>` with no fingerprint
  at all, and the Worker logs a warning. HTTP codes, exception classes,
  messages, stack line numbers, durations and codec names are kept. Every
  rule has its own test vector
  (`cloudflare-config/test/fixtures/sanitize-vectors.json`, 154 vectors;
  removing any single rule makes at least one vector fail). The app's own
  sanitiser (`LogSanitizer.kt`) has far fewer rules today; it is planned to
  apply the same ones and to read the same vectors in a JVM test, which does
  not exist yet. It cannot compute the keyed fingerprint and will write
  `<host>`.
  **What remains possible**: the rules are designed so that no credential,
  token, MAC, e-mail, IP address or full URL is stored, but they work on
  recognisable shapes. Free text without one cannot be detected by the
  Worker: a provider or server name typed by the user, « user X with password
  Y », a value behind an unknown or very short key (`u=`, `p=`, `client_id=`,
  `code=`, `session=`), a URL split across lines in a way the tail rule does
  not catch, a malformed scheme (`http:/…`, `http:…`), a bare MAC without
  separators, a short `Basic`/JWT token. A few characters at the very end of a
  password made of punctuation can also remain. The app must not send such
  text.
- **Web proxy (`web/cloudflare`)** — default-deny host allow-list, http(s) only,
  private/loopback/metadata IPs refused even if allow-listed, redirects
  re-validated hop by hop, GET/HEAD only, CORS limited to `ALLOWED_ORIGINS`.
- **Telemetry** — always on in the Android app (no opt-out); payloads are
  sanitised as described above (designed so that no credentials, MAC, e-mail,
  IP address or full URL is stored; free text without a recognisable shape
  cannot be detected). A device without an account sends crash reports and
  error/warning lines only, through the open, rate-limited `/api/anon/*`
  routes (device model, version and a random install ID of which the Worker
  keeps a short fingerprint).
- **Provider credentials at rest** — Room DB lives in app-private storage
  (`/data/data/<pkg>/files/`) which is unreadable by other apps on
  non-rooted devices.

## Worker token rotation and compromised legacy secrets

**The telemetry token that was hard-coded in `android-native/app/build.gradle.kts`
(versions up to 1.0.30, public Git history and every released APK) must be
considered compromised.** Anyone could read the crash and log dashboards of all
users and inject fake events. Removing it from the source does not un-publish it:

1. Deploy the new Worker (it no longer reads `CRASH_TOKEN` / `ADMIN_PASSWORD`, so
   the leaked token stops working immediately).
2. Set fresh secrets: `SESSION_SECRET`, `PROVIDER_ENC_KEY`, `OPS_TOKEN`.
3. Run the migration (`cloudflare-config/README.md`, « Migration des données
   existantes »): unprotected legacy entries and legacy logs are deleted,
   protected accounts are re-encrypted.
4. Treat every IPTV credential that was stored in the old format as **exposed**
   (it was readable without authentication unless `protectReads` was on): ask
   users to change their password at their IPTV provider.
5. Old APKs keep working for local playback but their cloud sync and telemetry
   receive `401`/`410`: users update and pair their TV.

Routine rotation: device token (revoke in the dashboard, or automatic rotation after
90 days), `OPS_TOKEN` / `SESSION_SECRET` (`wrangler secret put`), `PROVIDER_ENC_KEY`
(with `PROVIDER_ENC_KEY_PREVIOUS`, see the Worker README).

## Release signing — rotating from debug key

Release builds currently re-use the **debug keystore** for backwards-compat
with installs already in the wild. The build system reads env vars when
present, falling back to debug otherwise. To switch to a proper upload key
without locking users out of auto-update:

### 1. Generate a fresh release keystore (one-time)

```bash
keytool -genkey -v -keystore ultratv-release.jks \
        -keyalg RSA -keysize 4096 -validity 25000 \
        -alias ultratv-release
```

Store the keystore + passwords in a secret manager. **Never** commit them.

### 2. Build the rotation lineage

Android 9+ supports APK Signature Scheme v3 with a *lineage* file that
proves the new key is the legitimate successor of the old one. Without it,
the OS refuses to install over the existing debug-signed APK.

```bash
apksigner rotate \
  --in old.lineage_or_empty \
  --old-signer --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey \
  --new-signer --ks ultratv-release.jks --ks-key-alias ultratv-release \
  --out ultratv.lineage
```

The first rotation has no `--in` (Android infers an empty lineage from the
existing signing block at install time).

### 3. Wire the env vars

```bash
export ULTRA_KEYSTORE=/abs/path/ultratv-release.jks
export ULTRA_KEYSTORE_PASSWORD=...
export ULTRA_KEY_ALIAS=ultratv-release
export ULTRA_KEY_PASSWORD=...
export ULTRA_LINEAGE=/abs/path/ultratv.lineage
./gradlew :app:assembleRelease
```

After the first rotated release ships, every install can keep updating
in-place. Future builds keep using the new key without needing the lineage
again unless you rotate again.

## Backlog — known but not yet fixed

- **Credentials on the device** — provider passwords still live in clear in the
  Room database (`allowBackup="true"`); out of scope of the Worker work, tracked
  in the general audit.
- **Onboarding texts** still describe the old « provision your MAC » flow; the
  pairing dialog in Settings is the supported path.
- **Cert pinning on IPTV providers** — provider URLs are arbitrary; we
  validate the system trust anchors but don't pin per-host. Acceptable
  given the model (user-supplied URLs).
- **No JUnit / Compose test suite** — should land alongside the
  ViewModel refactor pass; first targets are `BackupCrypto` round-trip
  (encrypt → decrypt with wrong password rejects) and `LiveViewModel`
  flow shaping (chunked IN-list, distinctUntilChanged).
