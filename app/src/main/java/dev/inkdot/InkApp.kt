package dev.inkdot

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.inkdot.data.Http
import dev.inkdot.data.Library
import dev.inkdot.data.Prefs
import dev.inkdot.source.SourceManager
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class InkApp : Application(), SingletonImageLoader.Factory {
    lateinit var http: OkHttpClient
    lateinit var prefs: Prefs
    lateinit var library: Library
    lateinit var sources: SourceManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = Http.client(this)
        prefs = Prefs(this)
        library = Library(this)
        sources = SourceManager(this, http)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: InkApp
            private set
    }
}
