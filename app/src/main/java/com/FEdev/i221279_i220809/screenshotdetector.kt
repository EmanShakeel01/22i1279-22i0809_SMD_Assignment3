package com.FEdev.i221279_i220809

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ScreenshotDetector(
    private val context: Context,
    private val chatWithUserId: String, // ID of user we're chatting with
    private val onScreenshotDetected: () -> Unit
) {
    private var contentObserver: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastDetectedTime = 0L

    private val SCREENSHOT_KEYWORDS = arrayOf(
        "screenshot", "screen_shot", "screen-shot", "screen shot",
        "screencapture", "screen_capture", "screen-capture", "screen capture"
    )

    fun startWatching() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    if (isScreenshotPath(it)) {
                        Log.d("ScreenshotDetector", "Screenshot detected!")
                        onScreenshotDetected()
                        sendScreenshotNotificationToUser()
                    }
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
            Log.d("ScreenshotDetector", "Started watching for screenshots")
        } catch (e: Exception) {
            Log.e("ScreenshotDetector", "Error starting observer: ${e.message}")
        }
    }

    fun stopWatching() {
        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
                Log.d("ScreenshotDetector", "Stopped watching for screenshots")
            } catch (e: Exception) {
                Log.e("ScreenshotDetector", "Error stopping observer: ${e.message}")
            }
            contentObserver = null
        }
    }

    private fun sendScreenshotNotificationToUser() {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return
            val currentUserId = currentUser.uid
            val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown User"

            val database = FirebaseDatabase.getInstance(
                "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
            )

            val notificationData = mapOf(
                "type" to "screenshot",
                "fromUserId" to currentUserId,
                "fromUserName" to currentUserName,
                "timestamp" to System.currentTimeMillis()
            )

            // Send notification to the user we're chatting with
            database.reference
                .child("notifications")
                .child(chatWithUserId)
                .push()
                .setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("ScreenshotDetector", "Screenshot notification sent to $chatWithUserId")
                }
                .addOnFailureListener { e ->
                    Log.e("ScreenshotDetector", "Failed to send notification: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("ScreenshotDetector", "Error in sendScreenshotNotificationToUser: ${e.message}")
        }
    }

    private fun isScreenshotPath(uri: Uri): Boolean {
        try {
            val currentTime = System.currentTimeMillis()

            // Debounce: prevent duplicate detections within 2 seconds
            if (currentTime - lastDetectedTime < 2000) {
                return false
            }

            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
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

                    val data = it.getString(
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    ).lowercase()

                    val dateAdded = it.getLong(
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    )

                    // Check if image was added in the last 3 seconds
                    val currentTimeSeconds = System.currentTimeMillis() / 1000
                    if (currentTimeSeconds - dateAdded > 3) {
                        return false
                    }

                    // Check if filename or path contains screenshot keywords
                    for (keyword in SCREENSHOT_KEYWORDS) {
                        if (displayName.contains(keyword) || data.contains(keyword)) {
                            lastDetectedTime = currentTime
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotDetector", "Error checking screenshot: ${e.message}")
        }

        return false
    }
}