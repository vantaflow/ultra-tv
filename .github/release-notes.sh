#!/usr/bin/env bash
# Notes d'une release Android GitHub, écrites sur la sortie standard. Utilisé par release.yml ; essai à blanc local :
#   .github/release-notes.sh 1.3.73            (version des fichiers publiés, ex. « 1.3.73-rc2 » ; CHANGELOG lu pour VERSION)
#   .github/release-notes.sh 1.3.73 1.3.73     (2e argument : section du CHANGELOG, « Non publié » accepté)
#
# Contenu, dans cet ordre :
#   1. l'en-tête « Which file should I download? / Quel fichier télécharger ? » (.github/release-notes-header.md,
#      {{VERSION}} = version des fichiers publiés) ;
#   2. « ## Changes in <version> (English summary) » : la sous-section « ### In English » de la version dans le
#      CHANGELOG (quelques puces courtes, rédigées avec la version) ; à défaut, une phrase qui renvoie aux notes en
#      français (avertissement dans le journal de la CI, la release n'est pas bloquée) ;
#   3. « ## Notes de version <version> (français) » : la section de la version dans le CHANGELOG, sans « ### In English ».
#
# L'invite de mise à jour de l'appli tire son résumé des rubriques Nouveau / Ajouté / Amélioré / Corrigé (UpdateNotes,
# même analyse dans toutes les versions depuis 1.3.x) : les titres de niveau 2 ci-dessus ne contiennent aucun de ces mots
# (ni « new », « added », « improved », « fixed »), donc le résumé anglais est ignoré et le résumé reste le même
# qu'avant (ReleaseNotesHeaderTest).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FV="${1:?usage: release-notes.sh <version des fichiers> [section du CHANGELOG]}"
V="${2:-$(cat "$ROOT/VERSION")}"
CHANGELOG="$ROOT/CHANGELOG.md"

# Section « ## [V] » du CHANGELOG (sans son titre), jusqu'à la section suivante.
section() { awk -v v="$V" 'index($0, "## [" v "]") == 1 { on = 1; next } on && /^## \[/ { exit } on' "$CHANGELOG"; }

SECTION="$(section)"
if [ -z "$SECTION" ]; then echo "::error::section « ## [$V] » absente du CHANGELOG" >&2; exit 1; fi

# Sous-section « ### In English » (jusqu'au prochain titre ###/##) ; le reste = notes en français.
# (pas de « exit » dans awk : lecture jusqu'au bout, sinon printf peut recevoir SIGPIPE et pipefail arrêter le script)
EN="$(printf '%s\n' "$SECTION" | awk '/^### In English[[:space:]]*$/ { on = 1; next } /^##/ { on = 0 } on')"
FR="$(printf '%s\n' "$SECTION" | awk '/^### In English[[:space:]]*$/ { skip = 1; next } skip && /^##/ { skip = 0 } !skip')"
# Lignes vides en tête et en fin retirées.
trim() { awk 'NF { found = 1 } found' | awk '{ a[NR] = $0 } END { n = NR; while (n > 0 && a[n] ~ /^[[:space:]]*$/) n--; for (i = 1; i <= n; i++) print a[i] }'; }
EN="$(printf '%s\n' "$EN" | trim)"
FR="$(printf '%s\n' "$FR" | trim)"

sed "s/{{VERSION}}/$FV/g" "$ROOT/.github/release-notes-header.md"
echo
echo "## Changes in $FV (English summary)"
echo
if [ -n "$EN" ]; then
  printf '%s\n' "$EN"
else
  echo "::warning::pas de « ### In English » dans la section [$V] du CHANGELOG" >&2
  echo "No English summary for this version: the full list of changes is below, in French."
fi
echo
echo "## Notes de version $FV (français)"
echo
printf '%s\n' "$FR"
