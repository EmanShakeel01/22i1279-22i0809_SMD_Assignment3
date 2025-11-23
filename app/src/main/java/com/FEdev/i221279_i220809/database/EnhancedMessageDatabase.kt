package com.FEdev.i221279_i220809.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.FEdev.i221279_i220809.models.MessageItem

/**
 * Enhanced Message Database with improved offline support
 * Stores messages locally with sync status tracking
 */
class EnhancedMessageDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "enhanced_messages.db"
        private const val DATABASE_VERSION = 2
        private const val TAG = "EnhancedMessageDB"

        // Table names
        const val TABLE_MESSAGES = "messages"
        const val TABLE_POSTS = "posts"
        const val TABLE_STORIES = "stories"

        // Messages table columns
        const val COLUMN_MESSAGE_ID = "message_id"
        const val COLUMN_LOCAL_ID = "local_id"
        const val COLUMN_THREAD_ID = "thread_id"
        const val COLUMN_SENDER_ID = "sender_id"
        const val COLUMN_RECEIVER_ID = "receiver_id"
        const val COLUMN_MESSAGE_TEXT = "message_text"
        const val COLUMN_MESSAGE_TYPE = "message_type"
        const val COLUMN_MEDIA_BASE64 = "media_base64"
        const val COLUMN_FILE_NAME = "file_name"
        const val COLUMN_FILE_SIZE = "file_size"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_EDITED = "edited"
        const val COLUMN_EDITED_AT = "edited_at"
        const val COLUMN_IS_DELETED = "is_deleted"
        const val COLUMN_VANISH_MODE = "vanish_mode"
        const val COLUMN_SEEN = "seen"
        const val COLUMN_SEEN_AT = "seen_at"
        const val COLUMN_SYNC_STATUS = "sync_status"
        const val COLUMN_PENDING_UPLOAD = "pending_upload"
        const val COLUMN_CREATED_OFFLINE = "created_offline"

        // Posts table columns (for offline support)
        const val COLUMN_POST_ID = "post_id"
        const val COLUMN_POST_LOCAL_ID = "post_local_id"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_POST_TEXT = "post_text"
        const val COLUMN_POST_IMAGE = "post_image"
        const val COLUMN_POST_TIMESTAMP = "post_timestamp"
        const val COLUMN_LIKES_COUNT = "likes_count"
        const val COLUMN_COMMENTS_COUNT = "comments_count"

        // Stories table columns (for offline support)
        const val COLUMN_STORY_ID = "story_id"
        const val COLUMN_STORY_LOCAL_ID = "story_local_id"
        const val COLUMN_STORY_USER_ID = "story_user_id"
        const val COLUMN_STORY_MEDIA = "story_media"
        const val COLUMN_STORY_TIMESTAMP = "story_timestamp"
        const val COLUMN_STORY_EXPIRES_AT = "story_expires_at"

        // Sync status values
        const val SYNC_STATUS_PENDING = "pending"
        const val SYNC_STATUS_SYNCED = "synced"
        const val SYNC_STATUS_FAILED = "failed"
    }

    override fun onCreate(db: SQLiteDatabase) {
        createMessagesTable(db)
        createPostsTable(db)
        createStoriesTable(db)
        Log.d(TAG, "✅ Enhanced database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add new columns for offline support
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_SYNC_STATUS TEXT DEFAULT '$SYNC_STATUS_PENDING'")
            db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_CREATED_OFFLINE INTEGER DEFAULT 0")
            
            createPostsTable(db)
            createStoriesTable(db)
        }
        Log.d(TAG, "🔄 Database upgraded from $oldVersion to $newVersion")
    }

    private fun createMessagesTable(db: SQLiteDatabase) {
        val createMessagesTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COLUMN_LOCAL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MESSAGE_ID INTEGER,
                $COLUMN_THREAD_ID TEXT NOT NULL,
                $COLUMN_SENDER_ID INTEGER NOT NULL,
                $COLUMN_RECEIVER_ID INTEGER NOT NULL,
                $COLUMN_MESSAGE_TEXT TEXT,
                $COLUMN_MESSAGE_TYPE TEXT NOT NULL,
                $COLUMN_MEDIA_BASE64 TEXT,
                $COLUMN_FILE_NAME TEXT,
                $COLUMN_FILE_SIZE INTEGER,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_EDITED INTEGER DEFAULT 0,
                $COLUMN_EDITED_AT INTEGER,
                $COLUMN_IS_DELETED INTEGER DEFAULT 0,
                $COLUMN_VANISH_MODE INTEGER DEFAULT 0,
                $COLUMN_SEEN INTEGER DEFAULT 0,
                $COLUMN_SEEN_AT INTEGER,
                $COLUMN_SYNC_STATUS TEXT DEFAULT '$SYNC_STATUS_PENDING',
                $COLUMN_PENDING_UPLOAD INTEGER DEFAULT 0,
                $COLUMN_CREATED_OFFLINE INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createMessagesTable)
    }

    private fun createPostsTable(db: SQLiteDatabase) {
        val createPostsTable = """
            CREATE TABLE $TABLE_POSTS (
                $COLUMN_POST_LOCAL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_POST_ID INTEGER,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_POST_TEXT TEXT,
                $COLUMN_POST_IMAGE TEXT,
                $COLUMN_POST_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_LIKES_COUNT INTEGER DEFAULT 0,
                $COLUMN_COMMENTS_COUNT INTEGER DEFAULT 0,
                $COLUMN_SYNC_STATUS TEXT DEFAULT '$SYNC_STATUS_PENDING',
                $COLUMN_CREATED_OFFLINE INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createPostsTable)
    }

    private fun createStoriesTable(db: SQLiteDatabase) {
        val createStoriesTable = """
            CREATE TABLE $TABLE_STORIES (
                $COLUMN_STORY_LOCAL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_STORY_ID INTEGER,
                $COLUMN_STORY_USER_ID INTEGER NOT NULL,
                $COLUMN_STORY_MEDIA TEXT,
                $COLUMN_STORY_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STORY_EXPIRES_AT INTEGER NOT NULL,
                $COLUMN_SYNC_STATUS TEXT DEFAULT '$SYNC_STATUS_PENDING',
                $COLUMN_CREATED_OFFLINE INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createStoriesTable)
    }

    /**
     * Insert message with offline support
     */
    fun insertMessage(
        messageId: Int?,
        threadId: String,
        senderId: Int,
        receiverId: Int,
        messageText: String?,
        messageType: String,
        mediaBase64: String?,
        fileName: String?,
        fileSize: Int?,
        timestamp: Long,
        vanishMode: Boolean = false,
        synced: Boolean = false,
        pendingUpload: Boolean = false,
        createdOffline: Boolean = false
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            messageId?.let { put(COLUMN_MESSAGE_ID, it) }
            put(COLUMN_THREAD_ID, threadId)
            put(COLUMN_SENDER_ID, senderId)
            put(COLUMN_RECEIVER_ID, receiverId)
            put(COLUMN_MESSAGE_TEXT, messageText)
            put(COLUMN_MESSAGE_TYPE, messageType)
            put(COLUMN_MEDIA_BASE64, mediaBase64)
            put(COLUMN_FILE_NAME, fileName)
            put(COLUMN_FILE_SIZE, fileSize)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_VANISH_MODE, if (vanishMode) 1 else 0)
            put(COLUMN_SYNC_STATUS, if (synced) SYNC_STATUS_SYNCED else SYNC_STATUS_PENDING)
            put(COLUMN_PENDING_UPLOAD, if (pendingUpload) 1 else 0)
            put(COLUMN_CREATED_OFFLINE, if (createdOffline) 1 else 0)
        }

        val localId = db.insert(TABLE_MESSAGES, null, values)
        Log.d(TAG, "💾 Message inserted with local ID: $localId, offline: $createdOffline")
        return localId
    }

    /**
     * Get all messages for a thread
     */
    fun getMessagesForThread(threadId: String): List<MessageItem> {
        val messages = mutableListOf<MessageItem>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_THREAD_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(threadId),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val message = MessageItem(
                    message_id = it.getInt(it.getColumnIndexOrThrow(COLUMN_MESSAGE_ID)),
                    sender_id = it.getInt(it.getColumnIndexOrThrow(COLUMN_SENDER_ID)),
                    receiver_id = it.getInt(it.getColumnIndexOrThrow(COLUMN_RECEIVER_ID)),
                    message_text = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_TEXT)),
                    message_type = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_TYPE)),
                    media_base64 = it.getString(it.getColumnIndexOrThrow(COLUMN_MEDIA_BASE64)),
                    file_name = it.getString(it.getColumnIndexOrThrow(COLUMN_FILE_NAME)),
                    file_size = it.getInt(it.getColumnIndexOrThrow(COLUMN_FILE_SIZE)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    edited = it.getInt(it.getColumnIndexOrThrow(COLUMN_EDITED)) == 1,
                    edited_at = it.getLong(it.getColumnIndexOrThrow(COLUMN_EDITED_AT)),
                    is_deleted = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
                    vanish_mode = it.getInt(it.getColumnIndexOrThrow(COLUMN_VANISH_MODE)) == 1,
                    seen = it.getInt(it.getColumnIndexOrThrow(COLUMN_SEEN)) == 1,
                    seen_at = it.getLong(it.getColumnIndexOrThrow(COLUMN_SEEN_AT))
                )
                messages.add(message)
            }
        }

        Log.d(TAG, "📋 Retrieved ${messages.size} messages for thread: $threadId")
        return messages
    }

    /**
     * Get pending messages for sync
     */
    fun getPendingMessages(): List<MessageItem> {
        val messages = mutableListOf<MessageItem>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_SYNC_STATUS = ?",
            arrayOf(SYNC_STATUS_PENDING),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val message = MessageItem(
                    message_id = it.getInt(it.getColumnIndexOrThrow(COLUMN_MESSAGE_ID)),
                    sender_id = it.getInt(it.getColumnIndexOrThrow(COLUMN_SENDER_ID)),
                    receiver_id = it.getInt(it.getColumnIndexOrThrow(COLUMN_RECEIVER_ID)),
                    message_text = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_TEXT)),
                    message_type = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_TYPE)),
                    media_base64 = it.getString(it.getColumnIndexOrThrow(COLUMN_MEDIA_BASE64)),
                    file_name = it.getString(it.getColumnIndexOrThrow(COLUMN_FILE_NAME)),
                    file_size = it.getInt(it.getColumnIndexOrThrow(COLUMN_FILE_SIZE)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    edited = it.getInt(it.getColumnIndexOrThrow(COLUMN_EDITED)) == 1,
                    edited_at = it.getLong(it.getColumnIndexOrThrow(COLUMN_EDITED_AT)),
                    is_deleted = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
                    vanish_mode = it.getInt(it.getColumnIndexOrThrow(COLUMN_VANISH_MODE)) == 1,
                    seen = it.getInt(it.getColumnIndexOrThrow(COLUMN_SEEN)) == 1,
                    seen_at = it.getLong(it.getColumnIndexOrThrow(COLUMN_SEEN_AT))
                )
                messages.add(message)
            }
        }

        Log.d(TAG, "📤 Found ${messages.size} pending messages for sync")
        return messages
    }

    /**
     * Update message sync status
     */
    fun updateMessageSyncStatus(localId: Long, syncStatus: String, serverId: Int? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SYNC_STATUS, syncStatus)
            serverId?.let { put(COLUMN_MESSAGE_ID, it) }
        }

        db.update(
            TABLE_MESSAGES,
            values,
            "$COLUMN_LOCAL_ID = ?",
            arrayOf(localId.toString())
        )

        Log.d(TAG, "🔄 Updated message $localId sync status to: $syncStatus")
    }

    /**
     * Insert post with offline support
     */
    fun insertPost(
        postId: Int?,
        userId: Int,
        postText: String?,
        postImage: String?,
        timestamp: Long,
        createdOffline: Boolean = false
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            postId?.let { put(COLUMN_POST_ID, it) }
            put(COLUMN_USER_ID, userId)
            put(COLUMN_POST_TEXT, postText)
            put(COLUMN_POST_IMAGE, postImage)
            put(COLUMN_POST_TIMESTAMP, timestamp)
            put(COLUMN_SYNC_STATUS, if (postId != null) SYNC_STATUS_SYNCED else SYNC_STATUS_PENDING)
            put(COLUMN_CREATED_OFFLINE, if (createdOffline) 1 else 0)
        }

        val localId = db.insert(TABLE_POSTS, null, values)
        Log.d(TAG, "📝 Post inserted with local ID: $localId, offline: $createdOffline")
        return localId
    }

    /**
     * Insert story with offline support
     */
    fun insertStory(
        storyId: Int?,
        userId: Int,
        storyMedia: String,
        timestamp: Long,
        expiresAt: Long,
        createdOffline: Boolean = false
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            storyId?.let { put(COLUMN_STORY_ID, it) }
            put(COLUMN_STORY_USER_ID, userId)
            put(COLUMN_STORY_MEDIA, storyMedia)
            put(COLUMN_STORY_TIMESTAMP, timestamp)
            put(COLUMN_STORY_EXPIRES_AT, expiresAt)
            put(COLUMN_SYNC_STATUS, if (storyId != null) SYNC_STATUS_SYNCED else SYNC_STATUS_PENDING)
            put(COLUMN_CREATED_OFFLINE, if (createdOffline) 1 else 0)
        }

        val localId = db.insert(TABLE_STORIES, null, values)
        Log.d(TAG, "📖 Story inserted with local ID: $localId, offline: $createdOffline")
        return localId
    }

    /**
     * Clean up expired stories
     */
    fun cleanupExpiredStories() {
        val db = writableDatabase
        val currentTime = System.currentTimeMillis()

        val rowsDeleted = db.delete(
            TABLE_STORIES,
            "$COLUMN_STORY_EXPIRES_AT < ?",
            arrayOf(currentTime.toString())
        )

        Log.d(TAG, "🧹 Cleaned up $rowsDeleted expired stories")
    }

    /**
     * Get sync statistics
     */
    fun getSyncStats(): SyncStats {
        val db = readableDatabase
        val stats = SyncStats()

        // Count messages by sync status
        val messageCursor = db.rawQuery(
            "SELECT $COLUMN_SYNC_STATUS, COUNT(*) FROM $TABLE_MESSAGES GROUP BY $COLUMN_SYNC_STATUS",
            null
        )

        messageCursor.use {
            while (it.moveToNext()) {
                val status = it.getString(0)
                val count = it.getInt(1)
                
                when (status) {
                    SYNC_STATUS_PENDING -> stats.pendingMessages = count
                    SYNC_STATUS_SYNCED -> stats.syncedMessages = count
                    SYNC_STATUS_FAILED -> stats.failedMessages = count
                }
            }
        }

        return stats
    }
}

/**
 * Data class for sync statistics
 */
data class SyncStats(
    var pendingMessages: Int = 0,
    var syncedMessages: Int = 0,
    var failedMessages: Int = 0,
    var pendingPosts: Int = 0,
    var syncedPosts: Int = 0,
    var failedPosts: Int = 0
) {
    val totalPendingItems: Int get() = pendingMessages + pendingPosts
}