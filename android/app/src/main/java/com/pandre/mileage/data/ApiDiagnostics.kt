package com.pandre.mileage.data

import android.util.Log
import com.google.gson.JsonParser
import com.pandre.mileage.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Metadata only: never logs authorization headers, query parameters, or payloads. */
class ApiDiagnostics : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val id = UUID.randomUUID().toString()
        val request = chain.request().newBuilder().header("X-Request-ID", id).build()
        val path = request.url.encodedPath
            .replace(Regex("/cars/[^/]+"), "/cars/{carId}")
            .replace(Regex("/fuel-entries/[^/]+"), "/fuel-entries/{entryId}")
        val label = "${request.method} ${request.url.host}$path requestId=$id"
        val started = System.nanoTime()
        if (BuildConfig.DEBUG) Log.d("MileageApi", "Request $label")
        try {
            val response = chain.proceed(request)
            if (BuildConfig.DEBUG) {
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                // peekBody leaves Retrofit's error body available to its caller.
                val code = if (!response.isSuccessful) runCatching {
                    JsonParser.parseString(response.peekBody(4096).string()).asJsonObject["code"]
                        ?.asString?.takeIf { it.matches(Regex("[A-Z0-9_]{1,80}")) }
                }.getOrNull() else null
                Log.d("MileageApi", "Response $label status=${response.code} durationMs=$elapsed apiCode=${code ?: "none"}")
            }
            return response
        } catch (error: java.io.IOException) {
            if (BuildConfig.DEBUG) Log.w("MileageApi", "Network failure $label type=${error.javaClass.simpleName}")
            throw error
        }
    }
}

fun logOperationFailure(operation: String, error: Exception) {
    if (!BuildConfig.DEBUG) return
    val status = (error as? retrofit2.HttpException)?.code()
    Log.w("MileageApi", "Operation failed operation=$operation type=${error.javaClass.simpleName} status=${status ?: "none"}")
}
