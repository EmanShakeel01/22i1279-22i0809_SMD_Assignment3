package com.FEdev.i221279_i220809

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object TimeUtils {

    /**
     * Convert timestamp to "last seen" format
     * Examples: "Online", "Last seen 5 minutes ago", "Last seen today at 3:45 PM"
     */
    fun getLastSeenText(timestamp: Long?): String {
        if (timestamp == null) return "Offline"

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        // Less than 1 minute
        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return "Last seen just now"
        }

        // Less than 1 hour - show minutes
        if (diff < TimeUnit.HOURS.toMillis(1)) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            return "Last seen $minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        }

        // Less than 24 hours - show hours
        if (diff < TimeUnit.DAYS.toMillis(1)) {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            return "Last seen $hours ${if (hours == 1L) "hour" else "hours"} ago"
        }

        // Check if it's today
        val calendar = Calendar.getInstance()
        val todayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (timestamp >= todayStart) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "Last seen today at ${timeFormat.format(Date(timestamp))}"
        }

        // Check if it's yesterday
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
        if (timestamp >= yesterdayStart) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "Last seen yesterday at ${timeFormat.format(Date(timestamp))}"
        }

        // More than yesterday - show date
        val dateFormat = SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault())
        return "Last seen ${dateFormat.format(Date(timestamp))}"
    }

    /**
     * Simple format: just "Online" or "Offline"
     */
    fun getSimpleStatus(isOnline: Boolean): String {
        return if (isOnline) "Online" else "Offline"
    }

    /**
     * Get status with last seen
     */
    fun getStatusText(isOnline: Boolean, lastSeen: Long?): String {
        return if (isOnline) {
            "Online"
        } else {
            getLastSeenText(lastSeen)
        }
    }
}