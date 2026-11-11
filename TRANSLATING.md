# Translating Ultra TV

Thank you for helping Ultra TV speak your language. This guide explains what to translate, how to do it with or without Git, and the rules that keep translations working on a TV remote.

## What we translate

Only the **app interface**: menus, buttons, settings, messages, errors.

Never translate the **content of playlists**: channel names, categories, movie and series titles, programme guide data. That content comes from your provider and is shown as received.

## Language status

| Status | Languages | Requirement |
|---|---|---|
| Official | English (source), French, Spanish, Arabic | 100 % of the strings, enforced by tests |
| Community | any other language | Partial is fine; missing strings fall back to English |

A community language becomes official when it is **100 % translated** and a **reviewer** (a fluent speaker, other than the author) has checked it. Open an issue to ask for the promotion.

## Option A: Weblate (no Git needed)

Translation site: <https://hosted.weblate.org/projects/ultra-tv/> (coming soon).

1. Create a Weblate account (or sign in with GitHub).
2. Open the project and pick your language (or ask for a new one).
3. Choose a component: `android` (TV and mobile app) or `desktop` (Windows and macOS app).
4. Propose translations as suggestions; a reviewer accepts them.
5. Weblate opens a pull request for us. You do not touch the repository.

## Option B: GitHub pull request

To add a new language (example: `pl`):

**Android**
- Create `android-native/app/src/main/res/values-pl/strings.xml`, based on `values/strings.xml` (English, the source), and translate the values. Untranslated keys can simply be left out.
- Add a line to the `AppLang` enum in `android-native/app/src/main/kotlin/com/ultratv/tv/nativeapp/i18n/Strings.kt`: code, native name, and `rtl = true` for right-to-left languages.
- Add `"pl"` to the `COMMUNITY` list in `StringsXmlCompletenessTest`.

**Desktop**
- Create `web/src/i18n/locales/pl.json`, based on `en.json` (flat i18next format).
- Add an entry (`code`, native `label`, `dir`, `locale`) to `LANGS` in `web/src/i18n/lang.ts`, and add the code to `COMMUNITY_LANGS` in the same file (the desktop test reads that list).

**Check, then open the PR**

```bash
cd android-native && ./gradlew :app:testDebugUnitTest --tests '*StringsXml*' --tests '*I18n*'
cd web && npx vitest run src/i18n
```

If a legitimate string is identical to English (or has no Arabic script), the Android tests point at the lists in `android-native/app/src/test/resources/` (`i18n-same-as-english-whitelist.txt`, `i18n-no-arabic-whitelist.txt`). Then open a pull request against `main`.

To fix an existing translation, edit the same files and open a PR, or use Weblate.

## Rules

- **Variables and tags stay untouched.** Keep `%1$s`, `%1$d`, `{n}`, `{name}` and any markup exactly as is. You may move them within the sentence to follow your grammar.
- **Plural forms.** Provide every form your language needs (CLDR categories `zero`, `one`, `two`, `few`, `many`, `other`).
  - Polish: `one` (1), `few` (2-4), `many` (5+), `other` (fractions).
  - Arabic: all six: `zero` (0), `one` (1), `two` (2), `few` (3-10), `many` (11-99), `other` (100+).
  - Android uses `<plurals>` with `<item quantity="few">`; the desktop uses suffixed keys such as `channels_one`, `channels_few`, `channels_many`, `channels_other`, with a `{count}` variable.
- **Android escaping.** Write `\'` for an apostrophe and `\"` for a quote. A value starting with `@` or `?` must be escaped (`\@`, `\?`). Use `&amp;` and `&lt;` in XML.
- **Short texts.** The TV interface is read from about 3 metres and buttons are small. Prefer short, direct wording.
- **Proper names stay as they are:** Ultra TV, Xtream, M3U, Stalker, VLC.
- **Tone.** Use the natural register of your language for apps (for example "tu" or "vous" in French) and stay consistent throughout.

## Report a translation mistake

Found a wrong, missing or untranslated text? Open an issue with the [translation form](https://github.com/vantaflow/ultra-tv/issues/new?template=translation.yml) (Issues tab, "Translation"). Say where it appears and give the text you propose.
