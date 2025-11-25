package com.FEdev.i221279_i220809

import android.content.Context
import android.util.Log
import com.FEdev.i221279_i220809.utils.SessionManager

/**
 * Test helper for FCM notifications
 * Use this to quickly test notification functionality
 */
object NotificationTester {

    private const val TAG = "NotificationTester"

    /**
     * Test message notification
     * Call this to simulate receiving a message
     */
    fun testMessageNotification(context: Context, targetUserId: String) {
        val sessionManager = SessionManager(context)
        val currentUserId = sessionManager.getUserId()?.toString() ?: "123"
        val currentUsername = sessionManager.getUsername() ?: "TestUser"

        Log.d(TAG, "Testing message notification from $currentUsername to $targetUserId")

        Notificationhelperfcm.sendMessageNotification(
            senderId = currentUserId,
            senderName = currentUsername,
            receiverId = targetUserId,
            messageText = "This is a test message! 📱"
        )
    }

    /**
     * Test follow request notification
     */
    fun testFollowRequestNotification(context: Context, targetUserId: String) {
        val sessionManager = SessionManager(context)
        val currentUserId = sessionManager.getUserId()?.toString() ?: "123"
        val currentUsername = sessionManager.getUsername() ?: "TestUser"

        Log.d(TAG, "Testing follow request notification from $currentUsername to $targetUserId")

        Notificationhelperfcm.sendFollowRequestNotification(
            fromUserId = currentUserId,
            fromUsername = currentUsername,
            toUserId = targetUserId
        )
    }

    /**
     * Test screenshot alert notification
     */
    fun testScreenshotNotification(context: Context, targetUserId: String) {
        val sessionManager = SessionManager(context)
        val currentUserId = sessionManager.getUserId()?.toString() ?: "123"
        val currentUsername = sessionManager.getUsername() ?: "TestUser"

        Log.d(TAG, "Testing screenshot notification from $currentUsername to $targetUserId")

        Notificationhelperfcm.sendScreenshotNotification(
            screenshotTakerId = currentUserId,
            screenshotTakerName = currentUsername,
            otherUserId = targetUserId
        )
    }

    /**
     * Test comment notification
     */
    fun testCommentNotification(context: Context, postId: String, postOwnerId: String) {
        val sessionManager = SessionManager(context)
        val currentUserId = sessionManager.getUserId()?.toString() ?: "123"
        val currentUsername = sessionManager.getUsername() ?: "TestUser"

        Log.d(TAG, "Testing comment notification from $currentUsername on post $postId")

        Notificationhelperfcm.sendCommentNotification(
            commenterId = currentUserId,
            commenterName = currentUsername,
            postOwnerId = postOwnerId,
            postId = postId,
            commentText = "This is a test comment! 💬"
        )
    }

    /**
     * Test like notification
     */
    fun testLikeNotification(context: Context, postId: String, postOwnerId: String) {
        val sessionManager = SessionManager(context)
        val currentUserId = sessionManager.getUserId()?.toString() ?: "123"
        val currentUsername = sessionManager.getUsername() ?: "TestUser"

        Log.d(TAG, "Testing like notification from $currentUsername on post $postId")

        Notificationhelperfcm.sendLikeNotification(
            likerId = currentUserId,
            likerName = currentUsername,
            postOwnerId = postOwnerId,
            postId = postId
        )
    }

    /**
     * Test all notification types at once
     */
    fun testAllNotifications(
        context: Context, 
        targetUserId: String, 
        postId: String = "test_post_123"
    ) {
        Log.d(TAG, "Testing all notification types...")

        // Test message
        testMessageNotification(context, targetUserId)

        // Wait a bit between notifications
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            testFollowRequestNotification(context, targetUserId)
        }, 2000)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            testScreenshotNotification(context, targetUserId)
        }, 4000)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            testCommentNotification(context, postId, targetUserId)
        }, 6000)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            testLikeNotification(context, postId, targetUserId)
        }, 8000)

        Log.d(TAG, "All test notifications scheduled!")
    }

    /**
     * Check if FCM token is properly saved for current user
     */
    fun checkFCMTokenStatus(context: Context) {
        val sessionManager = SessionManager(context)
        val userId = sessionManager.getUserId()

        if (userId != null) {
            Notificationhelperfcm.getUserFCMToken(userId.toString()) { token ->
                if (token != null) {
                    Log.d(TAG, "✅ FCM Token found for user $userId: ${token.take(20)}...")
                } else {
                    Log.e(TAG, "❌ No FCM Token found for user $userId")
                }
            }
        } else {
            Log.e(TAG, "❌ No user logged in")
        }
    }
}