package com.FEdev.i221279_i220809.network

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://unmatted-idolisingly-derick.ngrok-free.dev/socially_api/api/"

    // Custom interceptor to log and handle HTML responses
    private val htmlResponseInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        val contentType = response.header("Content-Type")

        // Check if response is HTML instead of JSON
        if (contentType?.contains("text/html", ignoreCase = true) == true) {
            Log.e("RetrofitClient", "⚠️ Server returned HTML instead of JSON for: ${request.url}")
            Log.e("RetrofitClient", "⚠️ This usually means the endpoint doesn't exist (404)")

            // Return error response instead of trying to parse HTML
            response.newBuilder()
                .code(404)
                .message("Endpoint not found")
                .build()
        } else {
            response
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(htmlResponseInterceptor) // Add HTML handler first
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Configure Gson to be lenient (accepts malformed JSON)
    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}