package com.FEdev.i221279_i220809.utils

import android.content.Context
import android.util.Log
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase
import com.FEdev.i221279_i220809.database.EnhancedMessageDatabase
import com.FEdev.i221279_i220809.sync.BackgroundSyncManager
import org.json.JSONObject

/**
 * Test helper for demonstrating offline functionality
 * Use this class to simulate offline scenarios and test sync
 */
object OfflineTestHelper {
    
    private const val TAG = "OfflineTestHelper"
    
    /**
     * Simulate sending a message while offline
     */
    fun simulateOfflineMessage(context: Context, receiverId: Int, messageText: String) {
        Log.d(TAG, "🧪 Simulating offline message send")
        
        val enhancedDb = EnhancedMessageDatabase(context)
        val syncManager = BackgroundSyncManager.getInstance(context)
        
        // Insert message as if sent offline
        val localId = enhancedDb.insertMessage(
            messageId = null,
            threadId = "test_offline_thread",
            senderId = 1, // Mock current user ID
            receiverId = receiverId,
            messageText = messageText,
            messageType = "text",
            mediaBase64 = null,
            fileName = null,
            fileSize = null,
            timestamp = System.currentTimeMillis(),
            vanishMode = false,
            synced = false,
            pendingUpload = true,
            createdOffline = true
        )
        
        // Queue for sync
        val queueData = JSONObject().apply {
            put("local_id", localId)
            put("receiver_id", receiverId)
            put("message_text", messageText)
            put("message_type", "text")
            put("vanish_mode", false)
            put("timestamp", System.currentTimeMillis())
        }
        
        syncManager.queueAction(OfflineQueueDatabase.ACTION_SEND_MESSAGE, queueData)
        
        Log.d(TAG, "✅ Offline message simulation complete (Local ID: $localId)")
    }
    
    /**
     * Get current offline status summary
     */
    fun getOfflineStatusSummary(context: Context): String {
        val offlineDb = OfflineQueueDatabase(context)
        val enhancedDb = EnhancedMessageDatabase(context)
        val syncManager = BackgroundSyncManager.getInstance(context)
        
        val queueStats = offlineDb.getQueueStats()
        val syncStats = enhancedDb.getSyncStats()
        val isOnline = NetworkUtils.isNetworkAvailable(context)
        
        return buildString {
            appendLine("📊 Offline Status Summary")
            appendLine("🌐 Network: ${if (isOnline) "ONLINE" else "OFFLINE"}")
            appendLine("📤 Queue - Pending: ${queueStats.pendingCount}, Failed: ${queueStats.failedCount}")
            appendLine("💾 Messages - Pending: ${syncStats.pendingMessages}, Synced: ${syncStats.syncedMessages}")
            appendLine("⚡ Sync Manager Active: ${syncManager.isOnline()}")
        }
    }
    
    /**
     * Force cleanup of test data
     */
    fun cleanupTestData(context: Context) {
        Log.d(TAG, "🧹 Cleaning up test data")
        
        val offlineDb = OfflineQueueDatabase(context)
        offlineDb.cleanupOldActions(0) // Clean all completed actions
        
        Log.d(TAG, "✅ Test data cleanup complete")
    }
    
    /**
     * Simulate network connectivity changes for testing
     */
    fun logNetworkSimulation(context: Context, simulatedState: String) {
        Log.d(TAG, "🌐 Network Simulation: $simulatedState")
        Log.d(TAG, "📱 Actual Network: ${NetworkUtils.getNetworkType(context)}")
        Log.d(TAG, getOfflineStatusSummary(context))
    }
}