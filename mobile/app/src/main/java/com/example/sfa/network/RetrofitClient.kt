package com.example.sfa.network

import com.example.sfa.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit/OkHttp client.
 *
 * Call [setUserId] after login to inject the X-User-Id header globally.
 * The [okHttpClient] is also exposed so the sync-queue flush can reuse
 * the same client (with auth headers) for raw OkHttp calls.
 */
object RetrofitClient {

    // ── Auth state ────────────────────────────────────────────────────────────

    @Volatile private var currentUserId: Int = 0

    fun setUserId(id: Int) { currentUserId = id }
    fun clearUserId() { currentUserId = 0 }

    // ── Interceptors ──────────────────────────────────────────────────────────

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .addHeader("X-Source", "MobileApp")
        if (currentUserId != 0) {
            builder.addHeader("X-User-Id", currentUserId.toString())
        }
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
    }

    // ── OkHttp client (shared for both Retrofit and raw sync-queue writes) ────

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── Retrofit factory ──────────────────────────────────────────────────────

    fun createApi(baseUrl: String): ApiService = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + '/')
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
