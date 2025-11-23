package com.FEdev.i221279_i220809

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase
import com.FEdev.i221279_i220809.database.EnhancedMessageDatabase
import com.FEdev.i221279_i220809.sync.BackgroundSyncManager
import com.FEdev.i221279_i220809.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity to monitor offline queue and sync status
 * Useful for testing and debugging offline functionality
 */
class OfflineStatusActivity : AppCompatActivity() {
    
    private lateinit var networkStatusText: TextView
    private lateinit var queueStatsText: TextView
    private lateinit var syncStatsText: TextView
    private lateinit var refreshButton: Button
    private lateinit var forceSyncButton: Button
    
    private val offlineQueueDb by lazy { OfflineQueueDatabase(this) }
    private val enhancedMessageDb by lazy { EnhancedMessageDatabase(this) }
    private val syncManager by lazy { BackgroundSyncManager.getInstance(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_status)
        
        bindViews()
        setupClickListeners()
        
        // Auto-refresh every 5 seconds
        startAutoRefresh()
        
        // Initial load
        refreshStatus()
    }
    
    private fun bindViews() {
        networkStatusText = findViewById(R.id.networkStatusText)
        queueStatsText = findViewById(R.id.queueStatsText)
        syncStatsText = findViewById(R.id.syncStatsText)
        refreshButton = findViewById(R.id.refreshButton)
        forceSyncButton = findViewById(R.id.forceSyncButton)
    }
    
    private fun setupClickListeners() {
        refreshButton.setOnClickListener {
            refreshStatus()
        }
        
        forceSyncButton.setOnClickListener {
            if (NetworkUtils.isNetworkAvailable(this)) {
                syncManager.triggerSync()
                android.widget.Toast.makeText(this, "Sync triggered", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "No network connection", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
    
    private fun refreshStatus() {
        lifecycleScope.launch {
            updateNetworkStatus()
            updateQueueStats()
            updateSyncStats()
        }
    }
    
    private fun updateNetworkStatus() {
        val isOnline = NetworkUtils.isNetworkAvailable(this)
        val networkType = NetworkUtils.getNetworkType(this)
        
        networkStatusText.text = buildString {
            appendLine("🌐 Network Status: ${if (isOnline) "ONLINE" else "OFFLINE"}")
            appendLine("📡 Connection Type: $networkType")
            appendLine("📊 Sync Manager Online: ${syncManager.isOnline()}")
        }
        
        // Update button state
        forceSyncButton.isEnabled = isOnline
    }
    
    private fun updateQueueStats() {
        val queueStats = offlineQueueDb.getQueueStats()
        
        queueStatsText.text = buildString {
            appendLine("📤 Offline Queue Status:")
            appendLine("⏳ Pending: ${queueStats.pendingCount}")
            appendLine("🔄 In Progress: ${queueStats.inProgressCount}")
            appendLine("✅ Success: ${queueStats.successCount}")
            appendLine("❌ Failed: ${queueStats.failedCount}")
            appendLine("📊 Total: ${queueStats.totalCount}")
        }
    }
    
    private fun updateSyncStats() {
        val syncStats = enhancedMessageDb.getSyncStats()
        
        syncStatsText.text = buildString {
            appendLine("💾 Database Sync Status:")
            appendLine("⏳ Pending Messages: ${syncStats.pendingMessages}")
            appendLine("✅ Synced Messages: ${syncStats.syncedMessages}")
            appendLine("❌ Failed Messages: ${syncStats.failedMessages}")
            appendLine("📊 Total Pending: ${syncStats.totalPendingItems}")
        }
    }
    
    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (true) {
                delay(5000) // Refresh every 5 seconds
                refreshStatus()
            }
        }
    }
}