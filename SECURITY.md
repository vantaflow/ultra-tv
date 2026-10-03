# Ultra TV — Security notes

## Threat model

Ultra TV is a self-hosted IPTV client. The deployment is single-user (your
TV box + your Cloudflare Worker), so the primary threats are:

1. **Credential leakage** — IPTV provider credentials shouldn't escape the
   device or the worker's KV.
2. **Brute force** — neither the local PIN nor the worker's account password
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
  (never in the APK, never in a query string). Ingestion needs a paired device
  token and is rate-limited and size-capped.
- **Telemetry sanitiser** — the app *and* the Worker strip `?username=/?password=/
  ?token=`, `user:pass@host`, `/live/<user>/<pass>/…` and Bearer tokens before
  anything is stored.
- **Web proxy (`web/cloudflare`)** — default-deny host allow-list, http(s) only,
  private/loopback/metadata IPs refused even if allow-listed, redirects
  re-validated hop by hop, GET/HEAD only, CORS limited to `ALLOWED_ORIGINS`.
- **Telemetry opt-out** — Settings → Diagnostics distants toggle. Default
  ON for debug; flip OFF stops every event + crash POST silently.
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
