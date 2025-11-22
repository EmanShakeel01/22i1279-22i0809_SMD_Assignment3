package com.FEdev.i221279_i220809.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Update this URL with your current ngrok or server URL
    // For local development on emulator: "http://10.0.2.2/socially_api/api/"
    // For local development on real device: "http://YOUR_COMPUTER_IP/socially_api/api/"
    // For ngrok: Get fresh URL from ngrok and update here
    private const val BASE_URL = "https://unmatted-idolisingly-derick.ngrok-free.dev/socially_api/api/"
    
    // Backup URLs in case primary fails (no network operations during init)
    private val BACKUP_URLS = arrayOf(
        "http://10.0.2.2/socially_api/api/", // Android Emulator
        "http://192.168.1.100/socially_api/api/", // Common local network (update IP as needed)
        BASE_URL // Fallback to primary
    )

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val dnsInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        try {
            chain.proceed(request)
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("RetrofitClient", "❌ DNS resolution failed for ${request.url.host}")
            android.util.Log.e("RetrofitClient", "❌ Error: ${e.message}")
            android.util.Log.e("RetrofitClient", "💡 SOLUTION: Update BASE_URL in RetrofitClient.kt")
            android.util.Log.e("RetrofitClient", "💡 Options:")
            android.util.Log.e("RetrofitClient", "   1. Get fresh ngrok URL: ngrok http 80")
            android.util.Log.e("RetrofitClient", "   2. Use local IP: http://YOUR_IP/socially_api/api/")
            android.util.Log.e("RetrofitClient", "   3. Use emulator: http://10.0.2.2/socially_api/api/")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "❌ Network error: ${e.message}")
            throw e
        }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(dnsInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)  // Increased from 30
        .readTimeout(90, TimeUnit.SECONDS)     // Increased from 30 
        .writeTimeout(60, TimeUnit.SECONDS)    // Increased from 30
        .retryOnConnectionFailure(true)        // Add retry on failure
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}