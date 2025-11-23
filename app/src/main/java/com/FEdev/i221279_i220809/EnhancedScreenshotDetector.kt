package com.FEdev.i221279_i220809

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.FEdev.i221279_i220809.models.ScreenshotNotificationRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enhanced Screenshot Detector with Web Service Integration
 * Detects screenshots and sends notifications via PHP API
 */
class ScreenshotDetector(
    private val context: Context,
    private val targetUserId: Int, // User we're chatting with
    private val targetUsername: String
) {
    private var contentObserver: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastDetectedTime = 0L
    private val sessionManager = SessionManager(context)
    // Callback to notify chat screen when a system message is inserted
    var onSystemMessageInserted: ((message: String, timestamp: Long) -> Unit)? = null


    companion object {
        private const val TAG = "ScreenshotDetector"
        private const val DEBOUNCE_TIME = 2000L // 2 seconds
        private const val MAX_TIME_DIFF = 3000L // 3 seconds

        private val SCREENSHOT_KEYWORDS = arrayOf(
            "screenshot", "screen_shot", "screen-shot", "screen shot",
            "screencapture", "screen_capture", "screen-capture", "screen capture",
            "scrnshot", "scrn_shot"
        )
    }

    /**
     * Start monitoring for screenshots
     */
    fun startWatching() {
        Log.d(TAG, "📱 Starting screenshot detection...")
        
        if (contentObserver != null) {
            Log.d(TAG, "Already watching for screenshots")
            return
        }

        // Check if we have permission to read external storage
        if (!hasStoragePermission()) {
            Log.e(TAG, "❌ No storage permission - screenshot detection will not work")
            Log.e(TAG, "Please grant storage permission in app settings")
            return
        } else {
            Log.d(TAG, "✅ Storage permission granted")
        }

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    handleMediaChange(it)
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
            Log.d(TAG, "✅ Screenshot detection started for chat with: $targetUsername")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting observer: ${e.message}", e)
        }
    }

    /**
     * Stop monitoring for screenshots
     */
    fun stopWatching() {
        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
                Log.d(TAG, "✅ Screenshot detection stopped")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping observer: ${e.message}", e)
            }
            contentObserver = null
        }
    }

    /**
     * Handle media changes
     */
    private fun handleMediaChange(uri: Uri) {
        Log.d(TAG, "📷 Media change detected: $uri")
        if (isScreenshotPath(uri)) {
            Log.d(TAG, "📸 Screenshot confirmed! Creating system message...")
            onScreenshotDetected()
        } else {
            Log.d(TAG, "📷 Media change was not a screenshot")
        }
    }

    /**
     * Called when a screenshot is detected
     */
    private fun onScreenshotDetected() {
        // Insert system message directly into the chat
        insertScreenshotSystemMessage()
    }

    /**
     * Show local notification to current user
     */
    private fun showLocalNotification() {
        try {
            NotificationHelper.createNotificationChannel(context)

            // Only show if we have permission
            if (hasNotificationPermission()) {
                // Show a subtle notification to the person who took the screenshot
                // (Optional: you might want to skip this)
                Log.d(TAG, "Screenshot captured")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing local notification: ${e.message}", e)
        }
    }

    /**
     * Insert system message into the current chat
     */
    private fun insertScreenshotSystemMessage() {
        val currentUsername = sessionManager.getUsername()
        
        Log.d(TAG, "💾 Attempting to create system message...")
        Log.d(TAG, "👤 Current username: $currentUsername")
        
        if (currentUsername == null) {
            Log.e(TAG, "❌ Cannot create system message - not logged in")
            return
        }

        val systemMessage = "$currentUsername took a screenshot of this chat"
        val timestamp = System.currentTimeMillis()
        
        Log.d(TAG, "📝 System message: $systemMessage")
        Log.d(TAG, "⏰ Timestamp: $timestamp")
        Log.d(TAG, "🔄 Callback available: ${onSystemMessageInserted != null}")
        
        // Notify the chat activity to add this system message
        handler.post {
            Log.d(TAG, "📤 Invoking callback to add system message to chat")
            onSystemMessageInserted?.invoke(systemMessage, timestamp)
        }
    }

    /**
     * Check if the URI points to a screenshot
     */
    private fun isScreenshotPath(uri: Uri): Boolean {
        try {
            val currentTime = System.currentTimeMillis()
            Log.d(TAG, "🔍 Checking if URI is screenshot: $uri")

            // Debounce: prevent duplicate detections
            if (currentTime - lastDetectedTime < DEBOUNCE_TIME) {
                Log.d(TAG, "⏰ Debouncing - too soon since last detection")
                return false
            }

            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED
            )

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val displayName = it.getString(
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    ).lowercase()

                    val dataPath = it.getString(
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    ).lowercase()

                    val dateAdded = it.getLong(
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    ) * 1000 // Convert to milliseconds

                    Log.d(TAG, "📂 File: $displayName")
                    Log.d(TAG, "📁 Path: $dataPath")
                    Log.d(TAG, "⏰ Age: ${(currentTime - dateAdded) / 1000} seconds")

                    // Check if image was added recently (within last 3 seconds)
                    if (currentTime - dateAdded > MAX_TIME_DIFF) {
                        Log.d(TAG, "⏰ File too old - not a recent screenshot")
                        return false
                    }

                    // Check if path contains screenshot keywords
                    val isScreenshot = SCREENSHOT_KEYWORDS.any { keyword ->
                        val nameMatch = displayName.contains(keyword)
                        val pathMatch = dataPath.contains(keyword)
                        Log.d(TAG, "🔍 Checking keyword '$keyword': name=$nameMatch, path=$pathMatch")
                        nameMatch || pathMatch
                    }

                    if (isScreenshot) {
                        lastDetectedTime = currentTime
                        Log.d(TAG, "✅ Screenshot confirmed: $displayName")
                        return true
                    } else {
                        Log.d(TAG, "❌ No screenshot keywords found")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking screenshot: ${e.message}", e)
        }

        return false
    }

    /**
     * Check if we have storage permission
     */
    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if we have notification permission
     */
    private fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required below Android 13
        }
    }
}