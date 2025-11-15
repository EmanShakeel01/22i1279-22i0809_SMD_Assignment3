package com.FEdev.i221279_i220809

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import androidx.annotation.RequiresPermission

object NotificationHelper {

    private const val CHANNEL_ID = "screenshot_notifications"
    private const val CHANNEL_NAME = "Screenshot Alerts"
    private const val CHANNEL_DESC = "Notifications when someone takes a screenshot"
    private const val TAG = "NotificationHelper"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showScreenshotNotification(context: Context, userName: String) {
        try {
            // Create intent to open the app when notification is clicked
            val intent = Intent(context, homepage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Use a default icon if ic_notification doesn't exist
            val iconResId = try {
                context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
                    .takeIf { it != 0 } ?: R.drawable.ic_dialog_info
            } catch (e: Exception) {
                R.drawable.ic_dialog_info
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle("Screenshot Detected")
                .setContentText("$userName took a screenshot of your chat")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            NotificationManagerCompat.from(context).notify(
                System.currentTimeMillis().toInt(),
                notification
            )

            Log.d(TAG, "Screenshot notification shown for user: $userName")

        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }
}