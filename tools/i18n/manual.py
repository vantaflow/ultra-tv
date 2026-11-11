"""Reprises manuelles : fonctions que le convertisseur ne sait pas lire (pluriels arabes à 4 formes, formats, listes).

Appelé par migrate.py après la conversion automatique. Chaque remplacement échoue bruyamment si le texte attendu
n'est plus là, pour que la migration reste reproductible.
"""
import re


def _sub(path, pattern, repl, flags=re.S):
    s = path.read_text(encoding="utf-8")
    n, c = re.subn(pattern, lambda m: repl, s, count=1, flags=flags)
    assert c == 1, f"motif introuvable dans {path.name} : {pattern[:60]}"
    path.write_text(n, encoding="utf-8", newline="\n")


def _add(CAT, section, key, kind, values):
    CAT.entries[key] = dict(kind=kind, values=values, src=section)
    for rel, keys in CAT.sections:
        if rel == section:
            keys.append(key)
            return
    CAT.sections.append((section, [key]))


def apply(CAT, ROOT, KT):
    kt = ROOT / KT

    # DesignStrings.modeScheduledDesc : l'heure reste formatée en Kotlin (chiffres latins, comme avant).
    _sub(kt / "i18n/DesignStrings.kt", r'fun modeScheduledDesc\(h: Int\) = when \(lang\) \{.*?\n',
         'fun modeScheduledDesc(h: Int) = tx(R.string.ds_mode_scheduled_desc, "%02d".format(h))\n')
    _add(CAT, "i18n/DesignStrings.kt", "ds_mode_scheduled_desc", "string", {
        "en": "Every night at %1$s:00", "fr": "Chaque nuit à %1$s:00", "es": "Cada noche a las %1$s:00", "ar": "كل ليلة %1$s:00"})

    # GuideStrings.sourceGuideValue : deux chaînes au lieu de mots collés dans un gabarit.
    _sub(kt / "i18n/GuideStrings.kt", r'fun DesignStrings\.sourceGuideValue\(m: GuideMode, host: String\) = t\(.*?\n\)\n',
         'fun DesignStrings.sourceGuideValue(m: GuideMode, host: String) =\n'
         '    if (m == GuideMode.REPLACE) tx(R.string.guide_source_value_replaces, host) else tx(R.string.guide_source_value_completes, host)\n')
    _add(CAT, "i18n/GuideStrings.kt", "guide_source_value_replaces", "string", {
        "en": "Replaces · %1$s", "fr": "Remplace · %1$s", "es": "Reemplaza · %1$s", "ar": "يستبدل · %1$s"})
    _add(CAT, "i18n/GuideStrings.kt", "guide_source_value_completes", "string", {
        "en": "Completes · %1$s", "fr": "Complète · %1$s", "es": "Completa · %1$s", "ar": "يُكمل · %1$s"})

    # PersonalizationStrings : compteurs avec les 4 formes arabes (singulier, duel, 3–10, au-delà).
    f = kt / "i18n/PersonalizationStrings.kt"
    _sub(f, r'    return when \(lang\) \{\n        AppLang\.French -> if \(n <= 1\) "\$f chaîne".*?\n    \}\n',
         "    return tp(R.plurals.perso_channel_count, n, f)\n")
    _sub(f, r'    return when \(lang\) \{\n        AppLang\.French -> if \(n <= 1\) "\$f section".*?\n    \}\n',
         "    return tp(R.plurals.perso_section_count, n, f)\n")
    _add(CAT, "i18n/PersonalizationStrings.kt", "perso_channel_count", "plural", {
        "en": {"one": "%1$s channel", "other": "%1$s channels"},
        "fr": {"one": "%1$s chaîne", "other": "%1$s chaînes"},
        "es": {"one": "%1$s canal", "other": "%1$s canales"},
        "ar": {"one": "قناة واحدة", "two": "قناتان", "few": "%1$s قنوات", "other": "%1$s قناة"}})
    _add(CAT, "i18n/PersonalizationStrings.kt", "perso_section_count", "plural", {
        "en": {"one": "%1$s section", "other": "%1$s sections"},
        "fr": {"one": "%1$s section", "other": "%1$s sections"},
        "es": {"one": "%1$s sección", "other": "%1$s secciones"},
        "ar": {"one": "قسم واحد", "two": "قسمان", "few": "%1$s أقسام", "other": "%1$s قسمًا"}})

    # ProfileStrings.colorNames : tableau de ressources.
    _sub(kt / "ui/profile/ProfileStrings.kt", r'val colorNames get\(\) = when \(lang\) \{.*?\n    \}\n',
         "val colorNames get() = I18n.stringArray(lang, R.array.prof_color_names)\n")
    _add(CAT, "ui/profile/ProfileStrings.kt", "prof_color_names", "array", {
        "en": ["Blue", "Pink", "Green", "Amber", "Purple", "Red", "Cyan", "Lime"],
        "fr": ["Bleu", "Rose", "Vert", "Ambre", "Violet", "Rouge", "Cyan", "Lime"],
        "es": ["Azul", "Rosa", "Verde", "Ámbar", "Morado", "Rojo", "Cian", "Lima"],
        "ar": ["أزرق", "وردي", "أخضر", "عنبري", "بنفسجي", "أحمر", "سماوي", "ليموني"]})

    # LivePreviewStrings.seconds : le séparateur décimal français reste géré en Kotlin.
    _sub(kt / "ui/live/LivePreview.kt", r'        return when \(lang\) \{ AppLang\.French -> s\.replace.*?\n',
         "        return tx(R.string.lp_seconds, if (lang == AppLang.French) s.replace('.', ',') else s)\n")
    _add(CAT, "ui/live/LivePreview.kt", "lp_seconds", "string", {"en": "%1$s s", "fr": "%1$s s", "ar": "%1$s ث"})

    # Commentaires d'en-tête devenus faux avec la migration.
    _sub(kt / "i18n/Strings.kt", r"/\*\*\n \* Lightweight inline translation table\..*?\*/\nenum class AppLang",
         "/**\n"
         " * Langue de l'interface, indépendante de la langue du système ([System] = automatique). Ajouter une langue :\n"
         " * un dossier `res/values-xx/strings.xml` + une entrée ici (code, nom affiché dans sa langue, sens d'écriture) ;\n"
         " * le sélecteur des Réglages lit cette liste, les écrans n'ont rien à changer. Une clé absente d'une langue\n"
         " * affiche l'anglais (res/values), la langue source. Voir [I18n].\n"
         " */\nenum class AppLang")
    _sub(kt / "i18n/DesignStrings.kt",
         r" \* Textes des écrans « Claude Design », EN / FR / ES / AR côte à côte \(une ligne = une chaîne,\n"
         r" \* impossible d'oublier une langue\)\. Complète \[Strings\]\. Aucune donnée factice : ce ne sont que\n"
         r" \* des libellés d'interface\.\n",
         " * Textes des écrans « Claude Design », lus dans les strings.xml de res/values et res/values-xx selon la langue\n"
         " * de l'interface (voir [I18n]). Complète [Strings]. Aucune donnée factice : ce ne sont que des libellés.\n")

