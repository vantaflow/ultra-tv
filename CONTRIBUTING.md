# Contributing to Ultra TV

Thanks for considering a contribution. The project is organised in three sibling folders:

| Folder | Purpose |
|---|---|
| `web/` | React + TypeScript SPA — the heart of the app |
| `electron/` | Desktop shell. 99% of features come from `web/dist/`, this folder just wraps |
| `android-app/` | Capacitor wrapper for Android / Android TV / Google TV |

## Dev loop

```bash
# Web (most of the work happens here)
cd web && npm install && npm run dev

# Desktop pointing at the dev server
cd ../electron && SV_DEV=1 npm run dev

# Android — sync after every web change
cd ../android-app && npm run sync && npm run build:install
```

## Coding style

- TypeScript, strict mode.
- Functional React, hooks only.
- No premature abstractions. Three similar lines beats a wrong abstraction.
- Comments explain **why** the code exists, never **what** it does.
- No emojis in code or filenames (they're fine in UI strings).

## Adding a feature

1. Open an issue describing the user-visible behaviour first.
2. Implement in `web/` — that's enough for 90% of features (desktop + Android inherit automatically).
3. Add native code in `android-app/android/...` only when a feature genuinely needs platform access (intents, hardware…).
4. Run `npm run typecheck` and `npm run build` in `web/` before opening the PR.

## Pull requests

- One feature per PR.
- Mention any new dependency in the PR description, with a one-line justification.
- A demo GIF / screenshot is appreciated for UI changes.

## Security

Provider credentials live in the user's IndexedDB and never leave the device. Don't add telemetry, analytics, or any code that uploads user data without an explicit opt-in.

If you discover a security issue, please email the maintainer instead of opening a public issue.
