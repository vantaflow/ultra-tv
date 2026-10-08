package com.ultratv.tv.nativeapp.ui.mobile

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bouton Accueil du menu : doit revenir à l'accueil, pas restaurer la page quittée. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TopLevelNavTest {

    private fun nav(): NavHostController {
        val owner = object : LifecycleOwner {
            val reg = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = reg
        }
        return NavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setLifecycleOwner(owner)
            setViewModelStore(ViewModelStore())
            graph = createGraph(startDestination = Routes.HOME) {
                composable(Routes.HOME) {}
                composable(Routes.MOVIES) {}
                composable(Routes.SETTINGS) {}
                composable("movies/{id}") {}
            }
        }
    }

    @Test
    fun accueil_depuisUneFicheOuverteDepuisLAccueil_revientALAccueil() {
        val n = nav()
        n.navigate("movies/1")
        n.navigateTopLevel(Routes.HOME)
        assertEquals(Routes.HOME, n.currentDestination?.route)
    }

    @Test
    fun accueil_depuisUnOngletEtSaFiche_revientALAccueil() {
        val n = nav()
        n.navigateTopLevel(Routes.MOVIES)
        n.navigate("movies/1")
        n.navigateTopLevel(Routes.HOME)
        assertEquals(Routes.HOME, n.currentDestination?.route)
        // Puis de nouveau Films : l'onglet retrouve sa pile (fiche comprise).
        n.navigateTopLevel(Routes.MOVIES)
        assertEquals("movies/{id}", n.currentDestination?.route)
    }

    @Test
    fun accueil_dejaAffiche_resteSurLAccueil() {
        val n = nav()
        n.navigateTopLevel(Routes.HOME)
        assertEquals(Routes.HOME, n.currentDestination?.route)
        assertEquals(1, n.currentBackStack.value.count { it.destination.route == Routes.HOME })
    }
}
