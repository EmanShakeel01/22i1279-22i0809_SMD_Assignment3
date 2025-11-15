package com.FEdev.i221279_i220809

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

object Notificationhelperfcm {

    private const val TAG = "NotificationHelper"
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")

    /**
     * Get FCM token for a specific user
     */
    fun getUserFCMToken(userId: String, callback: (String?) -> Unit) {
        usersRef.child(userId).child("fcmToken")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val token = snapshot.getValue(String::class.java)
                    callback(token)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting FCM token: ${error.message}")
                    callback(null)
                }
            })
    }

    /**
     * Send notification when new message is received
     */
    fun sendMessageNotification(
        senderId: String,
        senderName: String,
        receiverId: String,
        messageText: String?
    ) {
        getUserFCMToken(receiverId) { token ->
            if (token != null) {
                val notificationData = mapOf(
                    "type" to "message",
                    "title" to senderName,
                    "body" to (messageText ?: "Sent an image"),
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                sendNotificationToToken(token, notificationData)
            }
        }
    }

    /**
     * Send notification for follow request
     */
    fun sendFollowRequestNotification(
        fromUserId: String,
        fromUsername: String,
        toUserId: String
    ) {
        getUserFCMToken(toUserId) { token ->
            if (token != null) {
                val notificationData = mapOf(
                    "type" to "follow_request",
                    "title" to "New Follow Request",
                    "body" to "$fromUsername wants to follow you",
                    "fromUserId" to fromUserId,
                    "fromUsername" to fromUsername,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                sendNotificationToToken(token, notificationData)
            }
        }
    }

    /**
     * Send notification when follow request is accepted
     */
    fun sendFollowAcceptedNotification(
        accepterId: String,
        accepterName: String,
        requesterId: String
    ) {
        getUserFCMToken(requesterId) { token ->
            if (token != null) {
                val notificationData = mapOf(
                    "type" to "follow_accepted",
                    "title" to "Follow Request Accepted",
                    "body" to "$accepterName accepted your follow request",
                    "userId" to accepterId,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                sendNotificationToToken(token, notificationData)
            }
        }
    }

    /**
     * Send notification for new comment
     */
    fun sendCommentNotification(
        commenterId: String,
        commenterName: String,
        postOwnerId: String,
        postId: String,
        commentText: String
    ) {
        // Don't send notification if commenting on own post
        if (commenterId == postOwnerId) return

        getUserFCMToken(postOwnerId) { token ->
            if (token != null) {
                val notificationData = mapOf(
                    "type" to "comment",
                    "title" to "New Comment",
                    "body" to "$commenterName commented: $commentText",
                    "commenterId" to commenterId,
                    "commenterName" to commenterName,
                    "postId" to postId,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                sendNotificationToToken(token, notificationData)
            }
        }
    }

    /**
     * Send notification for post like
     */
    fun sendLikeNotification(
        likerId: String,
        likerName: String,
        postOwnerId: String,
        postId: String
    ) {
        // Don't send notification if liking own post
        if (likerId == postOwnerId) return

        getUserFCMToken(postOwnerId) { token ->
            if (token != null) {
                val notificationData = mapOf(
                    "type" to "like",
                    "title" to "New Like",
                    "body" to "$likerName liked your post",
                    "likerId" to likerId,
                    "likerName" to likerName,
                    "postId" to postId,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                sendNotificationToToken(token, notificationData)
            }
        }
    }

    /**
     * Send notification for screenshot alert
     */
    fun sendScreenshotNotification(
        screenshotTakerId: String,
        screenshotTakerName: String,
        otherUserId: String
    ) {
        getUserFCMToken(otherUserId) { token ->
            if (token != null) {
                val notificationData = mapOf(
                    "type" to "screenshot",
                    "title" to "Screenshot Alert",
                    "body" to "⚠️ $screenshotTakerName took a screenshot",
                    "userId" to screenshotTakerId,
                    "userName" to screenshotTakerName,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                sendNotificationToToken(token, notificationData)
            }
        }
    }

    /**
     * Store notification data in database and send FCM
     */
    private fun sendNotificationToToken(token: String, data: Map<String, String>) {
        // Store notification in database
        val notificationsRef = database.getReference("notifications")
        val receiverId = when (data["type"]) {
            "message" -> data["receiverId"]
            "follow_request" -> data["toUserId"]
            "comment", "like" -> data["postOwnerId"]
            "screenshot" -> data["otherUserId"]
            else -> null
        }

        receiverId?.let { userId ->
            val notificationId = notificationsRef.push().key ?: return
            val notification = hashMapOf(
                "id" to notificationId,
                "data" to data,
                "read" to false,
                "timestamp" to System.currentTimeMillis()
            )

            notificationsRef.child(userId).child(notificationId).setValue(notification)
                .addOnSuccessListener {
                    Log.d(TAG, "Notification saved to database")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save notification: ${e.message}")
                }
        }

        // In a production app, you would send this to your backend server
        // which would then use Firebase Admin SDK to send the FCM notification
        Log.d(TAG, "Would send FCM notification to token: $token with data: $data")

        // For testing, you can use Firebase Cloud Functions or your backend
        sendFCMViaCloudFunction(token, data)
    }

    /**
     * Example: Send FCM via Cloud Function (you need to implement this)
     */
    private fun sendFCMViaCloudFunction(token: String, data: Map<String, String>) {
        // Call your Cloud Function endpoint
        val fcmData = hashMapOf(
            "token" to token,
            "notification" to hashMapOf(
                "title" to data["title"],
                "body" to data["body"]
            ),
            "data" to data
        )

        // You would make an HTTP call to your Cloud Function here
        // Example: RetrofitClient.sendNotification(fcmData)

        Log.d(TAG, "FCM data prepared: $fcmData")
    }

    /**
     * Get unread notification count
     */
    fun getUnreadNotificationCount(userId: String, callback: (Int) -> Unit) {
        database.getReference("notifications").child(userId)
            .orderByChild("read")
            .equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.childrenCount.toInt())
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting notification count: ${error.message}")
                    callback(0)
                }
            })
    }

    /**
     * Mark notification as read
     */
    fun markNotificationAsRead(userId: String, notificationId: String) {
        database.getReference("notifications")
            .child(userId)
            .child(notificationId)
            .child("read")
            .setValue(true)
    }

    /**
     * Clear all notifications for user
     */
    fun clearAllNotifications(userId: String, callback: () -> Unit) {
        database.getReference("notifications")
            .child(userId)
            .removeValue()
            .addOnSuccessListener {
                callback()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to clear notifications: ${e.message}")
            }
    }
}