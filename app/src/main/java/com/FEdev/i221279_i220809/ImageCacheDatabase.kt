package com.FEdev.i221279_i220809.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQLite Database for caching images offline
 * Works with Picasso for image management
 */
class ImageCacheDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "socially_image_cache.db"
        private const val DATABASE_VERSION = 1
        private const val TAG = "ImageCacheDB"

        // Table: cached_images
        const val TABLE_CACHED_IMAGES = "cached_images"
        const val COL_ID = "id"
        const val COL_IMAGE_KEY = "image_key" // URL or unique identifier
        const val COL_IMAGE_BASE64 = "image_base64"
        const val COL_CACHED_AT = "cached_at"
        const val COL_FILE_SIZE = "file_size" // in bytes
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_CACHED_IMAGES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_IMAGE_KEY TEXT UNIQUE NOT NULL,
                $COL_IMAGE_BASE64 TEXT NOT NULL,
                $COL_CACHED_AT INTEGER NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createTableQuery)

        // Create index for faster lookups
        db.execSQL("CREATE INDEX idx_image_key ON $TABLE_CACHED_IMAGES($COL_IMAGE_KEY)")
        db.execSQL("CREATE INDEX idx_cached_at ON $TABLE_CACHED_IMAGES($COL_CACHED_AT)")

        Log.d(TAG, "✅ Image cache database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHED_IMAGES")
        onCreate(db)
        Log.d(TAG, "Database upgraded from $oldVersion to $newVersion")
    }

    /**
     * Insert or update cached image
     */
    fun insertCachedImage(imageKey: String, imageBase64: String): Long {
        val db = writableDatabase
        val fileSize = imageBase64.length

        val values = ContentValues().apply {
            put(COL_IMAGE_KEY, imageKey)
            put(COL_IMAGE_BASE64, imageBase64)
            put(COL_CACHED_AT, System.currentTimeMillis())
            put(COL_FILE_SIZE, fileSize)
        }

        return try {
            // Try to insert, if key exists it will fail
            val id = db.insertWithOnConflict(
                TABLE_CACHED_IMAGES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            if (id != -1L) {
                Log.d(TAG, "✅ Cached image: $imageKey (${fileSize / 1024}KB)")
            } else {
                Log.e(TAG, "❌ Failed to cache image: $imageKey")
            }

            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error caching image: ${e.message}", e)
            -1
        }
    }

    /**
     * Get cached image by key
     */
    fun getCachedImage(imageKey: String): String? {
        val db = readableDatabase
        var imageBase64: String? = null

        try {
            val cursor = db.query(
                TABLE_CACHED_IMAGES,
                arrayOf(COL_IMAGE_BASE64),
                "$COL_IMAGE_KEY = ?",
                arrayOf(imageKey),
                null,
                null,
                null
            )

            cursor.use {
                if (it.moveToFirst()) {
                    imageBase64 = it.getString(it.getColumnIndexOrThrow(COL_IMAGE_BASE64))
                    Log.d(TAG, "✅ Retrieved cached image: $imageKey")
                } else {
                    Log.d(TAG, "⚠️ No cached image found for: $imageKey")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrieving cached image: ${e.message}", e)
        }

        return imageBase64
    }

    /**
     * Check if image is cached
     */
    fun isImageCached(imageKey: String): Boolean {
        val db = readableDatabase
        var exists = false

        try {
            val cursor = db.query(
                TABLE_CACHED_IMAGES,
                arrayOf(COL_ID),
                "$COL_IMAGE_KEY = ?",
                arrayOf(imageKey),
                null,
                null,
                null
            )

            cursor.use {
                exists = it.count > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking cache: ${e.message}", e)
        }

        return exists
    }

    /**
     * Delete cached image
     */
    fun deleteCachedImage(imageKey: String): Int {
        val db = writableDatabase

        return try {
            val deleted = db.delete(
                TABLE_CACHED_IMAGES,
                "$COL_IMAGE_KEY = ?",
                arrayOf(imageKey)
            )

            if (deleted > 0) {
                Log.d(TAG, "✅ Deleted cached image: $imageKey")
            }

            deleted
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting cached image: ${e.message}", e)
            0
        }
    }

    /**
     * Clear all cached images
     */
    fun clearAllCachedImages() {
        val db = writableDatabase

        try {
            val deleted = db.delete(TABLE_CACHED_IMAGES, null, null)
            Log.d(TAG, "✅ Cleared $deleted cached images")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing cache: ${e.message}", e)
        }
    }

    /**
     * Delete old cached images (older than specified days)
     */
    fun deleteOldCachedImages(daysOld: Int) {
        val db = writableDatabase
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)

        try {
            val deleted = db.delete(
                TABLE_CACHED_IMAGES,
                "$COL_CACHED_AT < ?",
                arrayOf(cutoffTime.toString())
            )

            if (deleted > 0) {
                Log.d(TAG, "✅ Deleted $deleted old cached images (>$daysOld days)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting old cache: ${e.message}", e)
        }
    }

    /**
     * Get total cache size in bytes
     */
    fun getCacheSize(): Long {
        val db = readableDatabase
        var totalSize = 0L

        try {
            val cursor = db.query(
                TABLE_CACHED_IMAGES,
                arrayOf("SUM($COL_FILE_SIZE) as total"),
                null,
                null,
                null,
                null,
                null
            )

            cursor.use {
                if (it.moveToFirst()) {
                    totalSize = it.getLong(it.getColumnIndexOrThrow("total"))
                }
            }

            Log.d(TAG, "Cache size: ${totalSize / (1024 * 1024)}MB")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating cache size: ${e.message}", e)
        }

        return totalSize
    }

    /**
     * Get number of cached images
     */
    fun getCachedImageCount(): Int {
        val db = readableDatabase
        var count = 0

        try {
            val cursor = db.query(
                TABLE_CACHED_IMAGES,
                arrayOf("COUNT(*) as count"),
                null,
                null,
                null,
                null,
                null
            )

            cursor.use {
                if (it.moveToFirst()) {
                    count = it.getInt(it.getColumnIndexOrThrow("count"))
                }
            }

            Log.d(TAG, "Cached images count: $count")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error counting cached images: ${e.message}", e)
        }

        return count
    }

    /**
     * Get all cached image keys
     */
    fun getAllCachedImageKeys(): List<String> {
        val db = readableDatabase
        val keys = mutableListOf<String>()

        try {
            val cursor = db.query(
                TABLE_CACHED_IMAGES,
                arrayOf(COL_IMAGE_KEY),
                null,
                null,
                null,
                null,
                "$COL_CACHED_AT DESC"
            )

            cursor.use {
                while (it.moveToNext()) {
                    keys.add(it.getString(it.getColumnIndexOrThrow(COL_IMAGE_KEY)))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting cache keys: ${e.message}", e)
        }

        return keys
    }
}