package com.example.tweakly.utils

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

object CoilConfig {
    fun buildLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25) // 25% of available RAM
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(300L * 1024 * 1024) // 300 MB
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
        .components {
            add(VideoFrameDecoder.Factory()) // video thumbnails
        }
        .respectCacheHeaders(false)
        .allowRgb565(true)   // 2x memory saving for thumbnails (no alpha needed in grid)
        .build()
}
