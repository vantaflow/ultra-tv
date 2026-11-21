package com.ultratv.tv.nativeapp.ui.home

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult

/**
 * Chargeur d'images factice des tests de l'accueil : toute image est un petit bitmap rendu aussitôt, sans réseau
 * ni coroutine laissée vivante (une requête réelle vers exemple.invalid pouvait échouer pendant le test suivant).
 */
@OptIn(DelicateCoilApi::class)
internal fun installFakeImageLoader() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val loader = ImageLoader.Builder(ctx).components {
        add(Interceptor { chain ->
            SuccessResult(image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImage(), request = chain.request, dataSource = coil3.decode.DataSource.MEMORY) as ImageResult
        })
    }.build()
    SingletonImageLoader.setUnsafe(loader)
}

@OptIn(DelicateCoilApi::class)
internal fun resetImageLoader() { SingletonImageLoader.reset() }
