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
        if (contentObserver != null) {
            Log.d(TAG, "Already watching for screenshots")
            return
        }

        // Check if we have permission to read external storage
        if (!hasStoragePermission()) {
            Log.e(TAG, "No storage permission")
            return
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
        if (isScreenshotPath(uri)) {
            Log.d(TAG, "📸 Screenshot detected!")
            onScreenshotDetected()
        }
    }

    /**
     * Called when a screenshot is detected
     */
    private fun onScreenshotDetected() {
        // Show local notification
        showLocalNotification()

        // Send notification to other user via web service
        sendScreenshotNotificationToServer()
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
     * Send screenshot notification to server
     * Server will handle notifying the other user
     */
    private fun sendScreenshotNotificationToServer() {
        val authToken = sessionManager.getAuthToken()
        val currentUserId = sessionManager.getUserId()
        val currentUsername = sessionManager.getUsername()

        if (authToken == null || currentUsername == null) {
            Log.e(TAG, "Cannot send notification - not logged in")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ScreenshotNotificationRequest(
                    auth_token = authToken,
                    target_user_id = targetUserId,
                    screenshot_taker_id = currentUserId,
                    screenshot_taker_username = currentUsername
                )

                Log.d(TAG, "Sending screenshot notification to server...")
                Log.d(TAG, "From: $currentUsername (ID: $currentUserId)")
                Log.d(TAG, "To: $targetUsername (ID: $targetUserId)")

                val response = RetrofitClient.apiService.sendScreenshotNotification(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "✅ Screenshot notification sent successfully")

                    // Show confirmation to the screenshot taker
                    handler.post {
                        android.widget.Toast.makeText(
                            context,
                            "$targetUsername will be notified",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e(TAG, "❌ Failed to send notification: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending notification: ${e.message}", e)
            }
        }
    }

    /**
     * Check if the URI points to a screenshot
     */
    private fun isScreenshotPath(uri: Uri): Boolean {
        try {
            val currentTime = System.currentTimeMillis()

            // Debounce: prevent duplicate detections
            if (currentTime - lastDetectedTime < DEBOUNCE_TIME) {
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

                    // Check if image was added recently (within last 3 seconds)
                    if (currentTime - dateAdded > MAX_TIME_DIFF) {
                        return false
                    }

                    // Check if path contains screenshot keywords
                    val isScreenshot = SCREENSHOT_KEYWORDS.any { keyword ->
                        displayName.contains(keyword) || dataPath.contains(keyword)
                    }

                    if (isScreenshot) {
                        lastDetectedTime = currentTime
                        Log.d(TAG, "✅ Screenshot detected: $displayName")
                        return true
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