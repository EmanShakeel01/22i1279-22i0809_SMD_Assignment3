package com.FEdev.i221279_i220809

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID_MESSAGES = "messages_channel"
        private const val CHANNEL_ID_SOCIAL = "social_channel"
        private const val CHANNEL_ID_ALERTS = "alerts_channel"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataPayload(remoteMessage.data)
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(
                title = it.title ?: "New Notification",
                body = it.body ?: "",
                type = remoteMessage.data["type"] ?: "general",
                data = remoteMessage.data
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Send token to your server or save to Firebase
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token: ${e.message}")
            }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"] ?: return
        val title = data["title"] ?: "Notification"
        val body = data["body"] ?: ""

        when (type) {
            "message" -> {
                sendNotification(title, body, "message", data)
            }
            "follow_request" -> {
                sendNotification(title, body, "follow_request", data)
            }
            "comment" -> {
                sendNotification(title, body, "comment", data)
            }
            "like" -> {
                sendNotification(title, body, "like", data)
            }
            "screenshot" -> {
                sendNotification(title, body, "screenshot", data)
            }
        }
    }

    private fun sendNotification(
        title: String,
        body: String,
        type: String,
        data: Map<String, String>
    ) {
        val intent = getIntentForType(type, data)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getChannelIdForType(type)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Add your notification icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // Add custom color based on type
        when (type) {
            "message" -> notificationBuilder.setColor(0xFF2196F3.toInt())
            "follow_request" -> notificationBuilder.setColor(0xFF4CAF50.toInt())
            "screenshot" -> notificationBuilder.setColor(0xFFFF5722.toInt())
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channels for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels(notificationManager)
        }

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notificationBuilder.build()
        )
    }

    private fun getIntentForType(type: String, data: Map<String, String>): Intent {
        return when (type) {
            "message" -> {
                Intent(this, ChatInboxActivity::class.java).apply {
                    putExtra("targetUid", data["senderId"])
                    putExtra("targetName", data["senderName"])
                }
            }
            "follow_request" -> {
                Intent(this, FollowRequestsActivity::class.java)
            }
            "comment", "like" -> {
                Intent(this, CommentsActivity::class.java).apply {
                    putExtra("postId", data["postId"])
                }
            }
            "screenshot" -> {
                Intent(this, ChatInboxActivity::class.java).apply {
                    putExtra("targetUid", data["userId"])
                    putExtra("targetName", data["userName"])
                }
            }
            else -> Intent(this, MainActivity::class.java)
        }
    }

    private fun getChannelIdForType(type: String): String {
        return when (type) {
            "message" -> CHANNEL_ID_MESSAGES
            "screenshot" -> CHANNEL_ID_ALERTS
            else -> CHANNEL_ID_SOCIAL
        }
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Messages Channel
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages"
                enableLights(true)
                enableVibration(true)
            }

            // Social Channel (follows, likes, comments)
            val socialChannel = NotificationChannel(
                CHANNEL_ID_SOCIAL,
                "Social",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for follows, likes, and comments"
                enableLights(true)
            }

            // Alerts Channel (screenshots)
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important security alerts"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(socialChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }
}