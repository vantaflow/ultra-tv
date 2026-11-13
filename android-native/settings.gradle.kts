pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SQLite embarqué (com.github.requery:sqlite-android) : publié seulement sur JitPack, limité à ce groupe.
        maven("https://jitpack.io") { content { includeGroup("com.github.requery") } }
    }
}

rootProject.name = "ultra-tv-native"
include(":app")
