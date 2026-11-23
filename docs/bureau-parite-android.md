# Parité bureau ↔ Android — point de reprise

1. État : audit commencé, aucun code modifié. Branche `feat/bureau-parite` partie de `origin/main` (de42c3f6) ; `npm ci` pas encore lancé dans web/.
2. Constaté côté bureau : `web/src/screens/Settings.tsx` (9 rubriques), niveaux Essentiel/Standard/Expert dans `web/src/lib/settingsLevel.ts` ; Plex/Emby/Jellyfin/NAS déjà présents (`net/plex.ts`, `net/mediaServer.ts`, `net/nas.ts`, `screens/Libraries.tsx`, `MediaWizard.tsx`) : à vérifier, pas forcément à refaire.
3. Côté Android : la rubrique Replay n'existe que sur `origin/next` (`data/replay/ReplayRubric.kt`, `ui/replay/*`, `i18n/ReplayStrings.kt`) ; canal de MAJ dans `update/UpdateChannel.kt` ; Simple/Expert dans `ui/simple/SimpleModeSetting.kt` et `SimpleSettings.kt`.
4. Prochaine étape : finir l'inventaire exhaustif des réglages Android (SettingsScreen.kt, AdvancedSettings.kt, PersonalizationDialogs.kt…) et écrire ici le tableau présent / absent / partiel / sans objet.
5. Ensuite, dans l'ordre : Replay → canal Stable/Pré-version (electron/autoupdate.cjs, updates.cjs) → Simple/Expert (migration : existant = Expert) → écarts Plex/Emby/Jellyfin → autres écarts ; TDD vitest, un commit par fonction, push après chaque fonction verte.
