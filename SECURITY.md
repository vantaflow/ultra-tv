# Ultra TV — Security notes

## Threat model

Ultra TV is a self-hosted IPTV client. The deployment is single-user (your
TV box + your Cloudflare Worker), so the primary threats are:

1. **Credential leakage** — IPTV provider credentials shouldn't escape the
   device or the worker's KV.
2. **Brute force** — neither the local PIN nor the worker's per-MAC password
   should be cheaply guessable.
3. **Supply chain** — a malicious update shouldn't be installable over a
   legitimate one.

## Already in place

- **Network security config** pins TLS for `github.com`,
  `api.github.com`, `githubusercontent.com` and `*.khalilbenaz.workers.dev`,
  so the update channel and telemetry endpoints can't be downgraded.
- **Backup encryption** (AES-GCM + PBKDF2-SHA256, 120 k iterations, 256-bit
  key) — opt-in via a password field in Settings. Plain exports are still
  supported with a warning that creds ship in clear.
- **PIN brute-force throttle** — three wrong attempts in a row trigger a
  growing delay (1 s → 4 s → 16 s) before the comparison runs.
- **Worker `/login` rate-limit** — five wrong attempts per MAC in any 15-min
  rolling window earn a 60-s lockout, stored under `lk:<mac>` in CONFIG KV
  with a 15-min TTL.
- **Telemetry sanitiser** — every event/crash message is stripped of
  `http(s)://host/<user>/<pass>/…` paths and `?username=/?password=` query
  params before any worker POST.
- **Telemetry opt-out** — Settings → Diagnostics distants toggle. Default
  ON for debug; flip OFF stops every event + crash POST silently.
- **Provider credentials at rest** — Room DB lives in app-private storage
  (`/data/data/<pkg>/files/`) which is unreadable by other apps on
  non-rooted devices.

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

- **CSRF tokens on worker forms** — `/api/provider/:mac`, `/api/password/:mac`
  and the delete endpoints are cookie-authed with `SameSite=Lax`, which
  blocks the obvious cross-site POST attack but not all of them. Adding a
  per-session token (`csrf` cookie + hidden form field) is on the TODO.
- **Cert pinning on IPTV providers** — provider URLs are arbitrary; we
  validate the system trust anchors but don't pin per-host. Acceptable
  given the model (user-supplied URLs).
- **No JUnit / Compose test suite** — should land alongside the
  ViewModel refactor pass; first targets are `BackupCrypto` round-trip
  (encrypt → decrypt with wrong password rejects) and `LiveViewModel`
  flow shaping (chunked IN-list, distinctUntilChanged).
