package com.FEdev.i221279_i220809

import android.util.Log
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages user online/offline status using web service (PHP + MySQL)
 * Replaces Firebase-based status management
 */
object OnlineStatusManager {

    private const val TAG = "OnlineStatusManager"
    private const val STATUS_UPDATE_INTERVAL = 30000L // 30 seconds
    private const val STATUS_CHECK_INTERVAL = 15000L // 15 seconds

    private var statusUpdateJob: Job? = null
    private var authToken: String? = null
    private var isInitialized = false

    // Cache for user statuses to reduce API calls
    private val statusCache = ConcurrentHashMap<Int, UserStatusData>()

    /**
     * Initialize status tracking for current user
     * Call this in your main activity (homepage) or Application class
     */
    fun initializeStatus(sessionManager: SessionManager) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.w(TAG, "No auth token - user not logged in")
            return
        }

        Log.d(TAG, "Initializing online status tracking")
        isInitialized = true

        // Set initial status to online
        setOnline()

        // Start periodic status updates
        startPeriodicStatusUpdates()
    }

    /**
     * Start background job to periodically update status to "online"
     */
    private fun startPeriodicStatusUpdates() {
        statusUpdateJob?.cancel()

        statusUpdateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    updateStatus("online")
                    delay(STATUS_UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic update: ${e.message}")
                    delay(STATUS_UPDATE_INTERVAL)
                }
            }
        }

        Log.d(TAG, "Periodic status updates started")
    }

    /**
     * Set user status to online
     */
    fun setOnline() {
        CoroutineScope(Dispatchers.IO).launch {
            updateStatus("online")
        }
    }

    /**
     * Set user status to offline
     */
    fun setOffline() {
        CoroutineScope(Dispatchers.IO).launch {
            updateStatus("offline")
        }
    }

    /**
     * Update user status on server
     */
    private suspend fun updateStatus(status: String) {
        val token = authToken ?: return

        try {
            val request = UpdateStatusRequest(
                auth_token = token,
                status = status
            )

            val response = RetrofitClient.apiService.updateStatus(request)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Status updated to: $status")
            } else {
                Log.e(TAG, "Failed to update status: ${response.body()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status: ${e.message}")
        }
    }

    /**
     * Get status of a specific user
     * @param userId The user ID to check
     * @param callback Called with (isOnline, lastSeen)
     */
    fun getUserStatus(userId: Int, callback: (Boolean, Long?) -> Unit) {
        val token = authToken ?: run {
            callback(false, null)
            return
        }

        // Check cache first
        statusCache[userId]?.let { cached ->
            val cacheAge = System.currentTimeMillis() - (cached.last_seen ?: 0)
            if (cacheAge < STATUS_CHECK_INTERVAL) {
                callback(cached.is_online, cached.last_seen)
                return
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = GetUserStatusRequest(
                    auth_token = token,
                    target_user_id = userId
                )

                val response = RetrofitClient.apiService.getUserStatus(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        // Update cache
                        statusCache[userId] = data

                        withContext(Dispatchers.Main) {
                            callback(data.is_online, data.last_seen)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            callback(false, null)
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get user status: ${response.body()?.message}")
                    withContext(Dispatchers.Main) {
                        callback(false, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user status: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(false, null)
                }
            }
        }
    }

    /**
     * Listen to a user's status with periodic updates
     * @param userId The user ID to monitor
     * @param callback Called whenever status changes
     * @return Job that can be cancelled to stop listening
     */
    fun listenToUserStatus(
        userId: Int,
        callback: (Boolean, Long?) -> Unit
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                getUserStatus(userId) { isOnline, lastSeen ->
                    callback(isOnline, lastSeen)
                }
                delay(STATUS_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Get statuses of multiple users at once (more efficient)
     * @param userIds List of user IDs
     * @param callback Called with map of userId -> (isOnline, lastSeen)
     */
    fun getMultipleUserStatuses(
        userIds: List<Int>,
        callback: (Map<Int, Pair<Boolean, Long?>>) -> Unit
    ) {
        val token = authToken ?: run {
            callback(emptyMap())
            return
        }

        if (userIds.isEmpty()) {
            callback(emptyMap())
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = GetMultipleStatusesRequest(
                    auth_token = token,
                    user_ids = userIds
                )

                val response = RetrofitClient.apiService.getMultipleUserStatuses(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        // Update cache
                        data.statuses.forEach { status ->
                            statusCache[status.user_id] = status
                        }

                        val statusMap = data.statuses.associate { status ->
                            status.user_id to Pair(status.is_online, status.last_seen)
                        }

                        withContext(Dispatchers.Main) {
                            callback(statusMap)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            callback(emptyMap())
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get multiple statuses: ${response.body()?.message}")
                    withContext(Dispatchers.Main) {
                        callback(emptyMap())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting multiple statuses: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyMap())
                }
            }
        }
    }

    /**
     * Clear cached status for a user (force refresh on next check)
     */
    fun clearCache(userId: Int) {
        statusCache.remove(userId)
    }

    /**
     * Clear all cached statuses
     */
    fun clearAllCache() {
        statusCache.clear()
    }

    /**
     * Clean up resources when app is destroyed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up OnlineStatusManager")

        // Set status to offline before cleanup
        setOffline()

        // Cancel periodic updates
        statusUpdateJob?.cancel()
        statusUpdateJob = null

        // Clear cache
        statusCache.clear()

        isInitialized = false
        authToken = null

        Log.d(TAG, "Cleanup complete")
    }
}