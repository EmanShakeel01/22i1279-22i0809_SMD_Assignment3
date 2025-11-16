package com.FEdev.i221279_i220809.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class MessageDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "socially_messages.db"
        private const val DATABASE_VERSION = 1

        // Messages table
        const val TABLE_MESSAGES = "messages"
        const val COL_ID = "id"
        const val COL_MESSAGE_ID = "message_id"
        const val COL_THREAD_ID = "thread_id"
        const val COL_SENDER_ID = "sender_id"
        const val COL_RECEIVER_ID = "receiver_id"
        const val COL_MESSAGE_TEXT = "message_text"
        const val COL_MESSAGE_TYPE = "message_type"
        const val COL_MEDIA_BASE64 = "media_base64"
        const val COL_FILE_NAME = "file_name"
        const val COL_FILE_SIZE = "file_size"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_EDITED = "edited"
        const val COL_EDITED_AT = "edited_at"
        const val COL_IS_DELETED = "is_deleted"
        const val COL_DELETED_AT = "deleted_at"
        const val COL_VANISH_MODE = "vanish_mode"
        const val COL_SEEN = "seen"
        const val COL_SEEN_AT = "seen_at"
        const val COL_SYNCED = "synced"
        const val COL_PENDING_UPLOAD = "pending_upload"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMessagesTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MESSAGE_ID INTEGER DEFAULT 0,
                $COL_THREAD_ID TEXT NOT NULL,
                $COL_SENDER_ID INTEGER NOT NULL,
                $COL_RECEIVER_ID INTEGER NOT NULL,
                $COL_MESSAGE_TEXT TEXT,
                $COL_MESSAGE_TYPE TEXT DEFAULT 'text',
                $COL_MEDIA_BASE64 TEXT,
                $COL_FILE_NAME TEXT,
                $COL_FILE_SIZE INTEGER,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_EDITED INTEGER DEFAULT 0,
                $COL_EDITED_AT INTEGER,
                $COL_IS_DELETED INTEGER DEFAULT 0,
                $COL_DELETED_AT INTEGER,
                $COL_VANISH_MODE INTEGER DEFAULT 0,
                $COL_SEEN INTEGER DEFAULT 0,
                $COL_SEEN_AT INTEGER,
                $COL_SYNCED INTEGER DEFAULT 0,
                $COL_PENDING_UPLOAD INTEGER DEFAULT 0
            )
        """.trimIndent()

        db.execSQL(createMessagesTable)

        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_thread_id ON $TABLE_MESSAGES($COL_THREAD_ID)")
        db.execSQL("CREATE INDEX idx_timestamp ON $TABLE_MESSAGES($COL_TIMESTAMP)")
        db.execSQL("CREATE INDEX idx_message_id ON $TABLE_MESSAGES($COL_MESSAGE_ID)")

        Log.d("MessageDB", "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
    }

    // Insert message
    fun insertMessage(
        messageId: Int,
        threadId: String,
        senderId: Int,
        receiverId: Int,
        messageText: String?,
        messageType: String,
        mediaBase64: String?,
        fileName: String?,
        fileSize: Int?,
        timestamp: Long,
        vanishMode: Boolean,
        synced: Boolean = true,
        pendingUpload: Boolean = false
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MESSAGE_ID, messageId)
            put(COL_THREAD_ID, threadId)
            put(COL_SENDER_ID, senderId)
            put(COL_RECEIVER_ID, receiverId)
            put(COL_MESSAGE_TEXT, messageText)
            put(COL_MESSAGE_TYPE, messageType)
            put(COL_MEDIA_BASE64, mediaBase64)
            put(COL_FILE_NAME, fileName)
            put(COL_FILE_SIZE, fileSize)
            put(COL_TIMESTAMP, timestamp)
            put(COL_VANISH_MODE, if (vanishMode) 1 else 0)
            put(COL_SYNCED, if (synced) 1 else 0)
            put(COL_PENDING_UPLOAD, if (pendingUpload) 1 else 0)
        }

        return db.insert(TABLE_MESSAGES, null, values)
    }

    // Get messages for a thread
    fun getMessages(threadId: String, limit: Int = 100): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COL_THREAD_ID = ? AND $COL_IS_DELETED = 0",
            arrayOf(threadId),
            null,
            null,
            "$COL_TIMESTAMP ASC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                messages.add(MessageData(
                    id = it.getInt(it.getColumnIndexOrThrow(COL_ID)),
                    messageId = it.getInt(it.getColumnIndexOrThrow(COL_MESSAGE_ID)),
                    threadId = it.getString(it.getColumnIndexOrThrow(COL_THREAD_ID)),
                    senderId = it.getInt(it.getColumnIndexOrThrow(COL_SENDER_ID)),
                    receiverId = it.getInt(it.getColumnIndexOrThrow(COL_RECEIVER_ID)),
                    messageText = it.getString(it.getColumnIndexOrThrow(COL_MESSAGE_TEXT)),
                    messageType = it.getString(it.getColumnIndexOrThrow(COL_MESSAGE_TYPE)),
                    mediaBase64 = it.getString(it.getColumnIndexOrThrow(COL_MEDIA_BASE64)),
                    fileName = it.getString(it.getColumnIndexOrThrow(COL_FILE_NAME)),
                    fileSize = if (it.isNull(it.getColumnIndexOrThrow(COL_FILE_SIZE))) null
                    else it.getInt(it.getColumnIndexOrThrow(COL_FILE_SIZE)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    edited = it.getInt(it.getColumnIndexOrThrow(COL_EDITED)) == 1,
                    editedAt = if (it.isNull(it.getColumnIndexOrThrow(COL_EDITED_AT))) null
                    else it.getLong(it.getColumnIndexOrThrow(COL_EDITED_AT)),
                    isDeleted = it.getInt(it.getColumnIndexOrThrow(COL_IS_DELETED)) == 1,
                    vanishMode = it.getInt(it.getColumnIndexOrThrow(COL_VANISH_MODE)) == 1,
                    seen = it.getInt(it.getColumnIndexOrThrow(COL_SEEN)) == 1,
                    seenAt = if (it.isNull(it.getColumnIndexOrThrow(COL_SEEN_AT))) null
                    else it.getLong(it.getColumnIndexOrThrow(COL_SEEN_AT)),
                    synced = it.getInt(it.getColumnIndexOrThrow(COL_SYNCED)) == 1,
                    pendingUpload = it.getInt(it.getColumnIndexOrThrow(COL_PENDING_UPLOAD)) == 1
                ))
            }
        }

        return messages
    }

    // Update message text (for edit)
    fun updateMessageText(messageId: Int, newText: String, editedAt: Long): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MESSAGE_TEXT, newText)
            put(COL_EDITED, 1)
            put(COL_EDITED_AT, editedAt)
        }

        return db.update(
            TABLE_MESSAGES,
            values,
            "$COL_MESSAGE_ID = ?",
            arrayOf(messageId.toString())
        )
    }

    // Delete message (soft delete)
    fun deleteMessage(messageId: Int, deletedAt: Long): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_IS_DELETED, 1)
            put(COL_DELETED_AT, deletedAt)
        }

        return db.update(
            TABLE_MESSAGES,
            values,
            "$COL_MESSAGE_ID = ?",
            arrayOf(messageId.toString())
        )
    }

    // Mark message as seen
    fun markMessageAsSeen(messageId: Int, seenAt: Long): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SEEN, 1)
            put(COL_SEEN_AT, seenAt)
        }

        return db.update(
            TABLE_MESSAGES,
            values,
            "$COL_MESSAGE_ID = ?",
            arrayOf(messageId.toString())
        )
    }

    // Clear vanish mode messages (delete seen messages)
    fun clearVanishMessages(threadId: String): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_MESSAGES,
            "$COL_THREAD_ID = ? AND $COL_VANISH_MODE = 1 AND $COL_SEEN = 1",
            arrayOf(threadId)
        )
    }

    // Get pending upload messages
    fun getPendingUploadMessages(): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COL_PENDING_UPLOAD = 1",
            null,
            null,
            null,
            "$COL_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                messages.add(MessageData(
                    id = it.getInt(it.getColumnIndexOrThrow(COL_ID)),
                    messageId = it.getInt(it.getColumnIndexOrThrow(COL_MESSAGE_ID)),
                    threadId = it.getString(it.getColumnIndexOrThrow(COL_THREAD_ID)),
                    senderId = it.getInt(it.getColumnIndexOrThrow(COL_SENDER_ID)),
                    receiverId = it.getInt(it.getColumnIndexOrThrow(COL_RECEIVER_ID)),
                    messageText = it.getString(it.getColumnIndexOrThrow(COL_MESSAGE_TEXT)),
                    messageType = it.getString(it.getColumnIndexOrThrow(COL_MESSAGE_TYPE)),
                    mediaBase64 = it.getString(it.getColumnIndexOrThrow(COL_MEDIA_BASE64)),
                    fileName = it.getString(it.getColumnIndexOrThrow(COL_FILE_NAME)),
                    fileSize = if (it.isNull(it.getColumnIndexOrThrow(COL_FILE_SIZE))) null
                    else it.getInt(it.getColumnIndexOrThrow(COL_FILE_SIZE)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    edited = it.getInt(it.getColumnIndexOrThrow(COL_EDITED)) == 1,
                    editedAt = if (it.isNull(it.getColumnIndexOrThrow(COL_EDITED_AT))) null
                    else it.getLong(it.getColumnIndexOrThrow(COL_EDITED_AT)),
                    isDeleted = it.getInt(it.getColumnIndexOrThrow(COL_IS_DELETED)) == 1,
                    vanishMode = it.getInt(it.getColumnIndexOrThrow(COL_VANISH_MODE)) == 1,
                    seen = it.getInt(it.getColumnIndexOrThrow(COL_SEEN)) == 1,
                    seenAt = if (it.isNull(it.getColumnIndexOrThrow(COL_SEEN_AT))) null
                    else it.getLong(it.getColumnIndexOrThrow(COL_SEEN_AT)),
                    synced = it.getInt(it.getColumnIndexOrThrow(COL_SYNCED)) == 1,
                    pendingUpload = it.getInt(it.getColumnIndexOrThrow(COL_PENDING_UPLOAD)) == 1
                ))
            }
        }

        return messages
    }

    // Mark message as synced
    fun markMessageAsSynced(localId: Int, serverMessageId: Int): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MESSAGE_ID, serverMessageId)
            put(COL_SYNCED, 1)
            put(COL_PENDING_UPLOAD, 0)
        }

        return db.update(
            TABLE_MESSAGES,
            values,
            "$COL_ID = ?",
            arrayOf(localId.toString())
        )
    }

    // Clear all messages (for logout)
    fun clearAllMessages() {
        val db = writableDatabase
        db.delete(TABLE_MESSAGES, null, null)
        Log.d("MessageDB", "All messages cleared")
    }
}

// Data class for messages
data class MessageData(
    val id: Int,
    val messageId: Int,
    val threadId: String,
    val senderId: Int,
    val receiverId: Int,
    val messageText: String?,
    val messageType: String,
    val mediaBase64: String?,
    val fileName: String?,
    val fileSize: Int?,
    val timestamp: Long,
    val edited: Boolean,
    val editedAt: Long?,
    val isDeleted: Boolean,
    val vanishMode: Boolean,
    val seen: Boolean,
    val seenAt: Long?,
    val synced: Boolean,
    val pendingUpload: Boolean
)