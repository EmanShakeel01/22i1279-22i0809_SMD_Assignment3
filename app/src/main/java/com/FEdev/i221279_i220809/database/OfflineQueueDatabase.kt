package com.FEdev.i221279_i220809.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject

/**
 * Database helper for managing offline queue of actions
 * Stores all user actions when offline for later synchronization
 */
class OfflineQueueDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "offline_queue.db"
        private const val DATABASE_VERSION = 1
        private const val TAG = "OfflineQueueDB"

        // Table names
        const val TABLE_QUEUE = "offline_queue"

        // Queue table columns
        const val COLUMN_ID = "id"
        const val COLUMN_ACTION_TYPE = "action_type"
        const val COLUMN_DATA = "data"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_RETRY_COUNT = "retry_count"
        const val COLUMN_STATUS = "status"
        const val COLUMN_ERROR_MESSAGE = "error_message"

        // Action types
        const val ACTION_SEND_MESSAGE = "send_message"
        const val ACTION_EDIT_MESSAGE = "edit_message"
        const val ACTION_DELETE_MESSAGE = "delete_message"
        const val ACTION_CREATE_POST = "create_post"
        const val ACTION_EDIT_POST = "edit_post"
        const val ACTION_DELETE_POST = "delete_post"
        const val ACTION_CREATE_STORY = "create_story"
        const val ACTION_DELETE_STORY = "delete_story"
        const val ACTION_LIKE_POST = "like_post"
        const val ACTION_COMMENT_POST = "comment_post"
        const val ACTION_FOLLOW_USER = "follow_user"
        const val ACTION_UNFOLLOW_USER = "unfollow_user"

        // Status values
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQueueTable = """
            CREATE TABLE $TABLE_QUEUE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_ACTION_TYPE TEXT NOT NULL,
                $COLUMN_DATA TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_RETRY_COUNT INTEGER DEFAULT 0,
                $COLUMN_STATUS TEXT DEFAULT '$STATUS_PENDING',
                $COLUMN_ERROR_MESSAGE TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        db.execSQL(createQueueTable)
        Log.d(TAG, "✅ Offline queue database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_QUEUE")
        onCreate(db)
        Log.d(TAG, "🔄 Offline queue database upgraded from $oldVersion to $newVersion")
    }

    /**
     * Add an action to the offline queue
     */
    fun queueAction(actionType: String, data: JSONObject): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ACTION_TYPE, actionType)
            put(COLUMN_DATA, data.toString())
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_STATUS, STATUS_PENDING)
        }

        val id = db.insert(TABLE_QUEUE, null, values)
        Log.d(TAG, "📤 Queued action: $actionType with ID: $id")
        return id
    }

    /**
     * Get all pending actions from the queue
     */
    fun getPendingActions(): List<QueuedAction> {
        val actions = mutableListOf<QueuedAction>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_QUEUE,
            null,
            "$COLUMN_STATUS = ?",
            arrayOf(STATUS_PENDING),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val action = QueuedAction(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    actionType = it.getString(it.getColumnIndexOrThrow(COLUMN_ACTION_TYPE)),
                    data = JSONObject(it.getString(it.getColumnIndexOrThrow(COLUMN_DATA))),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    retryCount = it.getInt(it.getColumnIndexOrThrow(COLUMN_RETRY_COUNT)),
                    status = it.getString(it.getColumnIndexOrThrow(COLUMN_STATUS)),
                    errorMessage = it.getString(it.getColumnIndexOrThrow(COLUMN_ERROR_MESSAGE))
                )
                actions.add(action)
            }
        }

        Log.d(TAG, "📋 Found ${actions.size} pending actions")
        return actions
    }

    /**
     * Update action status
     */
    fun updateActionStatus(id: Long, status: String, errorMessage: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
            errorMessage?.let { put(COLUMN_ERROR_MESSAGE, it) }
        }

        val rowsUpdated = db.update(
            TABLE_QUEUE,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )

        Log.d(TAG, "🔄 Updated action $id status to: $status")
    }

    /**
     * Increment retry count for an action
     */
    fun incrementRetryCount(id: Long) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE $TABLE_QUEUE SET $COLUMN_RETRY_COUNT = $COLUMN_RETRY_COUNT + 1 WHERE $COLUMN_ID = ?",
            arrayOf(id.toString())
        )
        Log.d(TAG, "🔄 Incremented retry count for action $id")
    }

    /**
     * Remove completed actions older than specified days
     */
    fun cleanupOldActions(daysOld: Int = 7) {
        val db = writableDatabase
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)

        val rowsDeleted = db.delete(
            TABLE_QUEUE,
            "$COLUMN_STATUS = ? AND $COLUMN_TIMESTAMP < ?",
            arrayOf(STATUS_SUCCESS, cutoffTime.toString())
        )

        Log.d(TAG, "🧹 Cleaned up $rowsDeleted old completed actions")
    }

    /**
     * Get queue statistics
     */
    fun getQueueStats(): QueueStats {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 
                $COLUMN_STATUS,
                COUNT(*) as count
            FROM $TABLE_QUEUE 
            GROUP BY $COLUMN_STATUS
            """.trimIndent(),
            null
        )

        val stats = QueueStats()
        cursor.use {
            while (it.moveToNext()) {
                val status = it.getString(0)
                val count = it.getInt(1)
                
                when (status) {
                    STATUS_PENDING -> stats.pendingCount = count
                    STATUS_IN_PROGRESS -> stats.inProgressCount = count
                    STATUS_SUCCESS -> stats.successCount = count
                    STATUS_FAILED -> stats.failedCount = count
                }
            }
        }

        return stats
    }
}

/**
 * Data class representing a queued action
 */
data class QueuedAction(
    val id: Long,
    val actionType: String,
    val data: JSONObject,
    val timestamp: Long,
    val retryCount: Int,
    val status: String,
    val errorMessage: String?
)

/**
 * Data class for queue statistics
 */
data class QueueStats(
    var pendingCount: Int = 0,
    var inProgressCount: Int = 0,
    var successCount: Int = 0,
    var failedCount: Int = 0
) {
    val totalCount: Int get() = pendingCount + inProgressCount + successCount + failedCount
}