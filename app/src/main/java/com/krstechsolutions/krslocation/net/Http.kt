package com.krstechsolutions.krslocation.net

import com.krstechsolutions.krslocation.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal GET helper; the app only talks to two public JSON endpoints. */
internal object Http {

    val userAgent = "KRS-Location/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    suspend fun getText(url: String, accept: String = "application/json"): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", accept)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
