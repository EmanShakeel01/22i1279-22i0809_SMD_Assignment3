package com.FEdev.i221279_i220809.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.work.*
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_COMMENT_POST
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_CREATE_POST
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_CREATE_STORY
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_DELETE_MESSAGE
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_DELETE_POST
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_DELETE_STORY
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_EDIT_MESSAGE
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_EDIT_POST
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_FOLLOW_USER
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_LIKE_POST
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_SEND_MESSAGE
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.ACTION_UNFOLLOW_USER
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.STATUS_FAILED
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.STATUS_IN_PROGRESS
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.STATUS_PENDING
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase.Companion.STATUS_SUCCESS
import com.FEdev.i221279_i220809.database.QueuedAction
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Background Sync Manager - Handles offline queue synchronization
 * Monitors network connectivity and syncs queued actions when online
 */
class BackgroundSyncManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BackgroundSyncManager"
        private const val SYNC_WORK_NAME = "offline_sync_work"
        private const val MAX_RETRY_COUNT = 3
        
        @Volatile
        private var INSTANCE: BackgroundSyncManager? = null
        
        fun getInstance(context: Context): BackgroundSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackgroundSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val offlineQueueDb = OfflineQueueDatabase(context)
    private val sessionManager = SessionManager(context)
    private val workManager = WorkManager.getInstance(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = false

    /**
     * Initialize the sync manager and start monitoring network
     */
    fun initialize() {
        Log.d(TAG, "🚀 Initializing Background Sync Manager")
        
        checkNetworkStatus()
        registerNetworkCallback()
        schedulePeriodicSync()
        
        // Clean up old completed actions
        offlineQueueDb.cleanupOldActions()
        
        Log.d(TAG, "✅ Background Sync Manager initialized")
    }

    /**
     * Check current network status
     */
    private fun checkNetworkStatus() {
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        
        isNetworkAvailable = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        Log.d(TAG, "🌐 Network status: ${if (isNetworkAvailable) "ONLINE" else "OFFLINE"}")
        
        if (isNetworkAvailable) {
            triggerSync()
        }
    }

    /**
     * Register network callback to monitor connectivity changes
     */
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "🌐 Network became available")
                isNetworkAvailable = true
                triggerSync()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "📱 Network lost")
                isNetworkAvailable = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                
                if (hasInternet != isNetworkAvailable) {
                    isNetworkAvailable = hasInternet
                    Log.d(TAG, "🌐 Network capabilities changed: ${if (hasInternet) "ONLINE" else "OFFLINE"}")
                    
                    if (hasInternet) {
                        triggerSync()
                    }
                }
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        Log.d(TAG, "👂 Network callback registered")
    }

    /**
     * Schedule periodic sync work
     */
    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        Log.d(TAG, "⏰ Periodic sync scheduled")
    }

    /**
     * Trigger immediate sync
     */
    fun triggerSync() {
        if (!isNetworkAvailable) {
            Log.d(TAG, "❌ Cannot sync - network not available")
            return
        }

        Log.d(TAG, "🔄 Triggering immediate sync")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "immediate_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Queue an action for later sync
     */
    fun queueAction(actionType: String, data: JSONObject) {
        val actionId = offlineQueueDb.queueAction(actionType, data)
        Log.d(TAG, "📤 Action queued: $actionType (ID: $actionId)")
        
        // Try immediate sync if online
        if (isNetworkAvailable) {
            triggerSync()
        }
    }

    /**
     * Check if device is online
     */
    fun isOnline(): Boolean {
        return isNetworkAvailable
    }

    /**
     * Get queue statistics
     */
    fun getQueueStats() = offlineQueueDb.getQueueStats()

    /**
     * Clean up resources
     */
    fun cleanup() {
        networkCallback?.let { 
            connectivityManager.unregisterNetworkCallback(it)
        }
        Log.d(TAG, "🧹 Background Sync Manager cleaned up")
    }

    /**
     * Worker class for background synchronization
     */
    class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        private val offlineQueueDb = OfflineQueueDatabase(context)
        private val sessionManager = SessionManager(context)

        override suspend fun doWork(): Result {
            Log.d(TAG, "🔄 Starting background sync work")

            return try {
                val authToken = sessionManager.getAuthToken()
                if (authToken == null) {
                    Log.e(TAG, "❌ No auth token - cannot sync")
                    return Result.failure()
                }

                val pendingActions = offlineQueueDb.getPendingActions()
                Log.d(TAG, "📋 Found ${pendingActions.size} pending actions to sync")

                var successCount = 0
                var failureCount = 0

                for (action in pendingActions) {
                    try {
                        // Mark as in progress
                        offlineQueueDb.updateActionStatus(action.id, STATUS_IN_PROGRESS)

                        val success = processAction(action, authToken)

                        if (success) {
                            offlineQueueDb.updateActionStatus(action.id, STATUS_SUCCESS)
                            successCount++
                            Log.d(TAG, "✅ Action ${action.id} synced successfully")
                        } else {
                            handleActionFailure(action)
                            failureCount++
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing action ${action.id}: ${e.message}", e)
                        handleActionFailure(action, e.message)
                        failureCount++
                    }
                }

                Log.d(TAG, "📊 Sync completed - Success: $successCount, Failed: $failureCount")

                if (failureCount == 0) Result.success() else Result.retry()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync work failed: ${e.message}", e)
                Result.retry()
            }
        }

        /**
         * Process individual queued action
         */
        private suspend fun processAction(action: QueuedAction, authToken: String): Boolean {
            Log.d(TAG, "🔄 Processing action: ${action.actionType}")

            return when (action.actionType) {
                ACTION_SEND_MESSAGE -> processSendMessage(action.data, authToken)
                ACTION_EDIT_MESSAGE -> processEditMessage(action.data, authToken)
                ACTION_DELETE_MESSAGE -> processDeleteMessage(action.data, authToken)
                ACTION_CREATE_POST -> processCreatePost(action.data, authToken)
                ACTION_EDIT_POST -> processEditPost(action.data, authToken)
                ACTION_DELETE_POST -> processDeletePost(action.data, authToken)
                ACTION_CREATE_STORY -> processCreateStory(action.data, authToken)
                ACTION_DELETE_STORY -> processDeleteStory(action.data, authToken)
                ACTION_LIKE_POST -> processLikePost(action.data, authToken)
                ACTION_COMMENT_POST -> processCommentPost(action.data, authToken)
                ACTION_FOLLOW_USER -> processFollowUser(action.data, authToken)
                ACTION_UNFOLLOW_USER -> processUnfollowUser(action.data, authToken)
                else -> {
                    Log.e(TAG, "❌ Unknown action type: ${action.actionType}")
                    false
                }
            }
        }

        /**
         * Handle action failure
         */
        private fun handleActionFailure(action: QueuedAction, errorMessage: String? = null) {
            offlineQueueDb.incrementRetryCount(action.id)

            if (action.retryCount >= MAX_RETRY_COUNT) {
                offlineQueueDb.updateActionStatus(action.id, STATUS_FAILED, errorMessage)
                Log.e(TAG, "❌ Action ${action.id} failed permanently after ${MAX_RETRY_COUNT} retries")
            } else {
                offlineQueueDb.updateActionStatus(action.id, STATUS_PENDING, errorMessage)
                Log.w(TAG, "⚠️ Action ${action.id} failed, will retry (${action.retryCount + 1}/$MAX_RETRY_COUNT)")
            }
        }

        // Action processing methods
        private suspend fun processSendMessage(data: JSONObject, authToken: String): Boolean {
            return try {
                val request = SendMessageRequest(
                    auth_token = authToken,
                    receiver_id = data.getInt("receiver_id"),
                    message_text = data.getString("message_text"),
                    message_type = data.getString("message_type"),
                    media_base64 = data.optString("media_base64").takeIf { it.isNotEmpty() },
                    file_name = data.optString("file_name").takeIf { it.isNotEmpty() },
                    file_size = if (data.has("file_size")) data.getInt("file_size") else null,
                    vanish_mode = data.optBoolean("vanish_mode", false)
                )

                val response = RetrofitClient.apiService.sendMessage(request)
                response.isSuccessful && response.body()?.success == true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending message: ${e.message}", e)
                false
            }
        }

        private suspend fun processEditMessage(data: JSONObject, authToken: String): Boolean {
            return try {
                val request = EditMessageRequest(
                    auth_token = authToken,
                    message_id = data.getInt("message_id"),
                    new_text = data.getString("new_text")
                )

                val response = RetrofitClient.apiService.editMessage(request)
                response.isSuccessful && response.body()?.success == true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error editing message: ${e.message}", e)
                false
            }
        }

        private suspend fun processDeleteMessage(data: JSONObject, authToken: String): Boolean {
            return try {
                val request = DeleteMessageRequest(
                    auth_token = authToken,
                    message_id = data.getInt("message_id")
                )

                val response = RetrofitClient.apiService.deleteMessage(request)
                response.isSuccessful && response.body()?.success == true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting message: ${e.message}", e)
                false
            }
        }

        private suspend fun processCreatePost(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement post creation API call
                // This would depend on your post API structure
                Log.d(TAG, "📝 Processing create post action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creating post: ${e.message}", e)
                false
            }
        }

        private suspend fun processEditPost(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement post editing API call
                Log.d(TAG, "✏️ Processing edit post action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error editing post: ${e.message}", e)
                false
            }
        }

        private suspend fun processDeletePost(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement post deletion API call
                Log.d(TAG, "🗑️ Processing delete post action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting post: ${e.message}", e)
                false
            }
        }

        private suspend fun processCreateStory(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement story creation API call
                Log.d(TAG, "📖 Processing create story action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creating story: ${e.message}", e)
                false
            }
        }

        private suspend fun processDeleteStory(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement story deletion API call
                Log.d(TAG, "🗑️ Processing delete story action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting story: ${e.message}", e)
                false
            }
        }

        private suspend fun processLikePost(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement post like API call
                Log.d(TAG, "👍 Processing like post action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error liking post: ${e.message}", e)
                false
            }
        }

        private suspend fun processCommentPost(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement comment creation API call
                Log.d(TAG, "💬 Processing comment post action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error commenting on post: ${e.message}", e)
                false
            }
        }

        private suspend fun processFollowUser(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement follow user API call
                Log.d(TAG, "👤 Processing follow user action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error following user: ${e.message}", e)
                false
            }
        }

        private suspend fun processUnfollowUser(data: JSONObject, authToken: String): Boolean {
            return try {
                // Implement unfollow user API call
                Log.d(TAG, "👤 Processing unfollow user action")
                true // Placeholder
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error unfollowing user: ${e.message}", e)
                false
            }
        }
    }
}