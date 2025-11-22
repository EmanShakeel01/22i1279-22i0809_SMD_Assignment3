package com.FEdev.i221279_i220809.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import android.widget.ImageView
import com.FEdev.i221279_i220809.R
import com.FEdev.i221279_i220809.database.ImageCacheDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Image Cache Manager using Picasso + SQLite
 * Handles online and offline image loading
 */
object ImageCacheManager {

    private const val TAG = "ImageCacheManager"
    private lateinit var picasso: Picasso
    private lateinit var appContext: Context

    private lateinit var cacheDb: ImageCacheDatabase
    private var isInitialized = false

    /**
     * Initialize the cache manager
     * Call this in Application class or MainActivity
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            appContext = context.applicationContext
            // Initialize Picasso with custom configuration
            picasso = Picasso.Builder(context)
                .indicatorsEnabled(false) // Disable debug indicators
                .loggingEnabled(true) // Enable logging for debugging
                .build()

            // Set as singleton
            Picasso.setSingletonInstance(picasso)

            // Initialize SQLite cache database
            cacheDb = ImageCacheDatabase(context)

            isInitialized = true
            Log.d(TAG, "✅ ImageCacheManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize: ${e.message}", e)
        }
    }

    /**
     * Load image from URL with caching
     * Falls back to cached version if offline
     */
    fun loadImage(
        imageUrl: String,
        imageView: ImageView,
        placeholder: Int = R.drawable.mystory,
        error: Int = R.drawable.ic_notification
    ) {
        if (!isInitialized) {
            Log.e(TAG, "❌ Not initialized!")
            return
        }

        // Try to load from Picasso (memory/disk cache)
        picasso.load(imageUrl)
            .placeholder(placeholder)
            .error(error)
            .into(imageView, object : com.squareup.picasso.Callback {
                override fun onSuccess() {
                    Log.d(TAG, "✅ Image loaded from Picasso: $imageUrl")
                    // Save to SQLite cache in background
                    cacheImageToDatabase(imageUrl, imageView.context)
                }

                override fun onError(e: Exception?) {
                    Log.e(TAG, "❌ Picasso failed, trying SQLite cache: ${e?.message}")
                    // Try to load from SQLite cache
                    loadFromSQLiteCache(imageUrl, imageView, error)
                }
            })
    }

    /**
     * Load Base64 image with caching
     * Useful for images from API responses
     */
    fun loadBase64Image(
        base64String: String,
        imageView: ImageView,
        cacheKey: String? = null
    ) {
        try {
            // Clean base64 string
            val cleanBase64 = base64String.replace("\n", "").replace("\r", "")

            // Decode to bitmap
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)

                // Cache to database if cache key provided
                if (cacheKey != null) {
                    cacheBase64ToDatabase(cacheKey, cleanBase64)
                }

                Log.d(TAG, "✅ Base64 image loaded and cached")
            } else {
                Log.e(TAG, "❌ Failed to decode Base64 image")
                imageView.setImageResource(R.drawable.ic_notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading Base64 image: ${e.message}", e)

            // Try to load from cache if available
            if (cacheKey != null) {
                loadBase64FromCache(cacheKey, imageView)
            } else {
                imageView.setImageResource(R.drawable.ic_notification)
            }
        }
    }

    /**
     * Load from SQLite cache
     */
    private fun loadFromSQLiteCache(
        imageUrl: String,
        imageView: ImageView,
        errorDrawable: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedImage = cacheDb.getCachedImage(imageUrl)

                withContext(Dispatchers.Main) {
                    if (cachedImage != null) {
                        val imageBytes = Base64.decode(cachedImage, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            Log.d(TAG, "✅ Image loaded from SQLite cache")
                        } else {
                            imageView.setImageResource(errorDrawable)
                            Log.e(TAG, "❌ Failed to decode cached image")
                        }
                    } else {
                        imageView.setImageResource(errorDrawable)
                        Log.d(TAG, "⚠️ No cached image found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading from cache: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(errorDrawable)
                }
            }
        }
    }

    /**
     * Cache image to database
     */
    private fun cacheImageToDatabase(imageUrl: String, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get bitmap from Picasso cache
                val bitmap = picasso.load(imageUrl).get()

                if (bitmap != null) {
                    // Convert to Base64
                    val base64 = bitmapToBase64(bitmap)

                    // Save to database
                    cacheDb.insertCachedImage(imageUrl, base64)

                    Log.d(TAG, "✅ Image cached to database: $imageUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error caching image: ${e.message}", e)
            }
        }
    }

    /**
     * Cache Base64 string directly
     */
    private fun cacheBase64ToDatabase(cacheKey: String, base64String: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                cacheDb.insertCachedImage(cacheKey, base64String)
                Log.d(TAG, "✅ Base64 cached to database: $cacheKey")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error caching Base64: ${e.message}", e)
            }
        }
    }

    /**
     * Load Base64 from cache
     */
    private fun loadBase64FromCache(cacheKey: String, imageView: ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedBase64 = cacheDb.getCachedImage(cacheKey)

                withContext(Dispatchers.Main) {
                    if (cachedBase64 != null) {
                        val imageBytes = Base64.decode(cachedBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            Log.d(TAG, "✅ Base64 loaded from cache")
                        } else {
                            imageView.setImageResource(R.drawable.ic_notification)
                        }
                    } else {
                        imageView.setImageResource(R.drawable.ic_notification)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading from cache: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(R.drawable.ic_notification)
                }
            }
        }
    }

    /**
     * Convert bitmap to Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Clear all cached images
     */
    fun clearCache() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clear SQLite cache
                cacheDb.clearAllCachedImages()

                // Clear Picasso memory cache by rebuilding singleton
                val newPicasso = Picasso.Builder(appContext).build()
                Picasso.setSingletonInstance(newPicasso)

                Log.d(TAG, "✅ Cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing cache: ${e.message}", e)
            }
        }
    }



    /**
     * Get cache size
     */
    suspend fun getCacheSize(): Long {
        return withContext(Dispatchers.IO) {
            cacheDb.getCacheSize()
        }
    }

    /**
     * Remove old cached images (older than specified days)
     */
    fun cleanOldCache(daysOld: Int = 7) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                cacheDb.deleteOldCachedImages(daysOld)
                Log.d(TAG, "✅ Old cache cleaned (>$daysOld days)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning old cache: ${e.message}", e)
            }
        }
    }
}