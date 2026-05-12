package com.ultratv.tv.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp

/**
 * Global Coil [ImageLoader] with both memory and disk caches. Without disk
 * cache, every scroll of a poster grid re-downloads every image — that's the
 * main source of lag on large catalogs. With caches sized below, repeated
 * scrolls of a 5000-poster grid stay smooth.
 */
@HiltAndroidApp
class UltraTvApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)        // 25% of available heap
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024) // 256 MB
                .build()
        }
        .respectCacheHeaders(false)         // many IPTV CDNs send no-cache; we override
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()
}
