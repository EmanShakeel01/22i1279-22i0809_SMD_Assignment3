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
     * Store message data in Firebase so Cloud Function can trigger
     */
    fun sendMessageNotification(
        senderId: String,
        senderName: String,
        receiverId: String,
        messageText: String?
    ) {
        // Create chat ID (consistent format for both users)
        val chatId = if (senderId < receiverId) {
            "${senderId}_${receiverId}"
        } else {
            "${receiverId}_${senderId}"
        }

        // Store message in Firebase for Cloud Function to trigger
        val messagesRef = database.getReference("chats").child(chatId).child("messages")
        val messageId = messagesRef.push().key ?: return

        val messageData = hashMapOf(
            "senderId" to senderId,
            "senderName" to senderName,
            "receiverId" to receiverId,
            "messageText" to (messageText ?: ""),
            "messageType" to if (messageText != null) "text" else "media",
            "timestamp" to System.currentTimeMillis()
        )

        messagesRef.child(messageId).setValue(messageData)
            .addOnSuccessListener {
                Log.d(TAG, "Message stored in Firebase, Cloud Function will send notification")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to store message in Firebase: ${e.message}")
            }
    }

    /**
     * Send notification for follow request
     * The follow request is already stored in Firebase by FollowRequestManager
     * Cloud Function will automatically trigger when data is written
     */
    fun sendFollowRequestNotification(
        fromUserId: String,
        fromUsername: String,
        toUserId: String
    ) {
        // FollowRequestManager already stores the request in Firebase
        // Cloud Function will automatically send notification
        Log.d(TAG, "Follow request stored by FollowRequestManager, Cloud Function will handle notification")
    }

    /**
     * Send notification when follow request is accepted
     * Cloud Function will automatically trigger when following relationship is created
     */
    fun sendFollowAcceptedNotification(
        accepterId: String,
        accepterName: String,
        requesterId: String
    ) {
        // FollowRequestManager already creates following/followers relationships
        // Cloud Function will automatically send notification
        Log.d(TAG, "Follow acceptance handled by FollowRequestManager, Cloud Function will handle notification")
    }

    /**
     * Send notification for new comment
     * Store comment data in Firebase so Cloud Function can trigger
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

        // Store comment in Firebase for Cloud Function to trigger
        val commentsRef = database.getReference("posts").child(postId).child("comments")
        val commentId = commentsRef.push().key ?: return

        val commentData = hashMapOf(
            "userId" to commenterId,
            "username" to commenterName,
            "text" to commentText,
            "timestamp" to System.currentTimeMillis().toString()
        )

        commentsRef.child(commentId).setValue(commentData)
            .addOnSuccessListener {
                Log.d(TAG, "Comment stored in Firebase, Cloud Function will send notification")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to store comment in Firebase: ${e.message}")
            }
    }

    /**
     * Send notification for post like
     * Store like data in Firebase so Cloud Function can trigger
     */
    fun sendLikeNotification(
        likerId: String,
        likerName: String,
        postOwnerId: String,
        postId: String
    ) {
        // Don't send notification if liking own post
        if (likerId == postOwnerId) return

        // Store like in Firebase for Cloud Function to trigger
        val likesRef = database.getReference("posts").child(postId).child("likes")

        likesRef.child(likerId).setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "Like stored in Firebase, Cloud Function will send notification")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to store like in Firebase: ${e.message}")
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
        // Store screenshot data in Firebase, Cloud Function will handle notification
        val screenshotsRef = database.getReference("screenshots")
        val screenshotId = screenshotsRef.push().key ?: return

        val screenshotData = hashMapOf(
            "takerId" to screenshotTakerId,
            "takerName" to screenshotTakerName,
            "timestamp" to System.currentTimeMillis()
        )

        screenshotsRef.child(otherUserId).child(screenshotId).setValue(screenshotData)
            .addOnSuccessListener {
                Log.d(TAG, "Screenshot data saved, Cloud Function will send notification")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save screenshot data: ${e.message}")
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