package dev.inkdot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "Inkdot/0.1 (Android; open-source manga reader)"

    fun client(context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") == null) request.newBuilder().header("User-Agent", USER_AGENT).build()
                else request
            )
        }
        .build()
}

suspend fun OkHttpClient.getText(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} · ${request.url.host}")
            response.body?.string() ?: throw IOException("Empty response · ${request.url.host}")
        }
    }
