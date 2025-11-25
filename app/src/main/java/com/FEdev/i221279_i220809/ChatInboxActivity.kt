package com.FEdev.i221279_i220809

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.database.MessageDatabaseHelper
import com.FEdev.i221279_i220809.database.EnhancedMessageDatabase
import com.FEdev.i221279_i220809.database.OfflineQueueDatabase
import com.FEdev.i221279_i220809.sync.BackgroundSyncManager
import com.FEdev.i221279_i220809.utils.NetworkUtils
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import org.json.JSONObject

class ChatInboxActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var photoBtn: ImageView
    private lateinit var videoBtn: ImageView
    private lateinit var fileBtn: ImageView
    private lateinit var vanishModeBtn: ImageView
    private lateinit var title: TextView
    private lateinit var loadingIndicator: ProgressBar

    private val sessionManager by lazy { SessionManager(this) }
    private val messageDB by lazy { MessageDatabaseHelper(this) }
    private val enhancedMessageDB by lazy { EnhancedMessageDatabase(this) }
    private val syncManager by lazy { BackgroundSyncManager.getInstance(this) }

    private val messages = mutableListOf<MessageItem>()
    private lateinit var adapter: NewChatAdapter
    private var screenshotDetector: ScreenshotDetector? = null

    private var currentUserId: Int = 0
    private var targetUserId: Int = 0
    private var targetName: String = ""
    private var threadId: String = ""
    private var vanishMode: Boolean = false
    private var lastMessageId: Int = 0

    private val PICK_IMAGE_REQUEST = 1
    private val PICK_VIDEO_REQUEST = 2
    private val PICK_FILE_REQUEST = 3
    private val STORAGE_PERMISSION_REQUEST = 100

    private var pollingJob: kotlinx.coroutines.Job? = null
    private var isSendingMessage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_inbox)

        currentUserId = sessionManager.getUserId()
        targetUserId = intent.getIntExtra("targetUserId", 0)
        targetName = intent.getStringExtra("targetName") ?: "User"

        Log.d("ChatInbox", "=== ChatInboxActivity onCreate ===")
        Log.d("ChatInbox", "Current User ID: $currentUserId")
        Log.d("ChatInbox", "Target User ID: $targetUserId")
        Log.d("ChatInbox", "Target Name: $targetName")

        if (currentUserId <= 0 || targetUserId <= 0) {
            Log.e(
                "ChatInbox",
                "❌ Invalid user data: currentUserId=$currentUserId, targetUserId=$targetUserId"
            )
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        threadId = if (currentUserId < targetUserId) {
            "${currentUserId}_${targetUserId}"
        } else {
            "${targetUserId}_${currentUserId}"
        }

        Log.d("ChatInbox", "Thread ID: $threadId")

        screenshotDetector = ScreenshotDetector(
            context = this,
            targetUserId = targetUserId,
            targetUsername = targetName
        )
        
        // Set callback for when screenshot system messages are created
        screenshotDetector?.onSystemMessageInserted = { message, timestamp ->
            addScreenshotSystemMessage(message, timestamp)
        }
        
        // Check and request storage permission for screenshot detection
        checkAndRequestStoragePermission()

        bindViews()
        setupRecycler()
        loadCachedMessages()

        // Delay server fetch to ensure UI is ready
        lifecycleScope.launch {
            delay(100) // Reduced delay for faster response
            fetchMessagesFromServer()
            delay(1000) // Wait a bit before starting polling
            startMessagePolling()
            syncPendingMessages()
        }

        listenForIncomingCalls()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }

        // Call button - long press for voice, single tap for video
        findViewById<ImageView>(R.id.btnCall).setOnClickListener {
            Log.d("ChatInbox", "📹 Video call button pressed")
            startCallToTarget(isVideo = true)
        }
        
        // Long press for voice call
        findViewById<ImageView>(R.id.btnCall).setOnLongClickListener {
            Log.d("ChatInbox", "📞 Voice call (long press) initiated")
            startCallToTarget(isVideo = false)
            true
        }

        sendBtn.setOnClickListener { sendTextMessage() }
        photoBtn.setOnClickListener { openImagePicker() }
        videoBtn.setOnClickListener { openVideoPicker() }
        fileBtn.setOnClickListener { openFilePicker() }
        vanishModeBtn.setOnClickListener { toggleVanishMode() }
    }

    private fun bindViews() {
        recycler = findViewById(R.id.recyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.send)
        photoBtn = findViewById(R.id.photoButton)
        videoBtn = findViewById(R.id.videoButton)
        fileBtn = findViewById(R.id.fileButton)
        vanishModeBtn = findViewById(R.id.vanishModeButton)
        title = findViewById(R.id.chatTitle)
        loadingIndicator = findViewById(R.id.loadingIndicator) ?: ProgressBar(this).apply {
            visibility = android.view.View.GONE
        }

        title.text = targetName
    }

    private fun setupRecycler() {
        adapter = NewChatAdapter(
            context = this,
            messages = messages,
            currentUserId = currentUserId,
            onEditClick = { msg -> editMessage(msg) },
            onDeleteClick = { msg -> deleteMessage(msg) }
        )
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter
    }

    // ==================== LOAD CACHED MESSAGES ====================

    private fun loadCachedMessages() {
        lifecycleScope.launch {
            try {
                val cachedMessages = messageDB.getMessages(threadId)

                val newMessages = cachedMessages.map { cached ->
                    MessageItem(
                        message_id = cached.messageId,
                        sender_id = cached.senderId,
                        receiver_id = cached.receiverId,
                        message_text = cached.messageText,
                        message_type = cached.messageType,
                        media_base64 = cached.mediaBase64,
                        file_name = cached.fileName,
                        file_size = cached.fileSize,
                        timestamp = cached.timestamp,
                        edited = cached.edited,
                        edited_at = cached.editedAt,
                        is_deleted = cached.isDeleted,
                        vanish_mode = cached.vanishMode,
                        seen = cached.seen,
                        seen_at = cached.seenAt
                    )
                }.sortedBy { it.timestamp }

                runOnUiThread {
                    Log.d("ChatInbox", "Current messages in UI: ${messages.size}")
                    Log.d("ChatInbox", "Messages from DB: ${newMessages.size}")

                    // Only update if the message lists are actually different
                    val shouldUpdate = messages.size != newMessages.size || 
                        !messages.zip(newMessages).all { (current, new) -> 
                            current.message_id == new.message_id && current.timestamp == new.timestamp
                        }

                    if (shouldUpdate) {
                        Log.d("ChatInbox", "Updating UI with fresh message list")
                        val scrollToEnd = recycler.canScrollVertically(1) == false || messages.isEmpty()
                        
                        messages.clear()
                        messages.addAll(newMessages)
                        adapter.notifyDataSetChanged()
                        
                        if (scrollToEnd) {
                            scrollToBottom()
                        }
                        Log.d("ChatInbox", "UI updated. Messages now showing: ${messages.size}")
                    } else {
                        Log.d("ChatInbox", "No UI update needed - messages unchanged")
                    }
                }

                Log.d("ChatInbox", "✅ Loaded ${newMessages.size} cached messages")
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error loading cached messages: ${e.message}", e)
            }
        }
    }

    // ==================== FETCH FROM SERVER ====================

    private fun fetchMessagesFromServer() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.e("ChatInbox", "❌ No auth token available")
            return
        }

        lifecycleScope.launch {
            try {
                Log.d("ChatInbox", "📡 Fetching messages from server...")
                Log.d("ChatInbox", "Auth token: ${authToken.take(10)}...")
                Log.d("ChatInbox", "Other user ID: $targetUserId")
                Log.d("ChatInbox", "Last message ID: $lastMessageId")

                val request = GetMessagesRequest(
                    auth_token = authToken,
                    other_user_id = targetUserId,
                    last_message_id = 0  // Always fetch all messages for simplicity
                )

                val response = RetrofitClient.apiService.getMessages(request)

                Log.d("ChatInbox", "Response code: ${response.code()}")
                Log.d("ChatInbox", "Response successful: ${response.isSuccessful}")

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ChatInbox", "❌ Server error: $errorBody")
                    return@launch
                }

                val body = response.body()
                Log.d("ChatInbox", "Response body success: ${body?.success}")
                Log.d("ChatInbox", "Response body message: ${body?.message}")

                if (body?.success == true) {
                    val data = body.data

                    if (data != null) {
                        Log.d("ChatInbox", "✅ Received ${data.messages.size} messages")
                        Log.d("ChatInbox", "Vanish mode: ${data.vanish_mode}")

                        vanishMode = data.vanish_mode
                        updateVanishModeUI()

                        // Cache new messages and check for duplicates
                        var hasNewMessages = false
                        val newServerMessages = mutableListOf<MessageItem>()
                        
                        data.messages.forEach { msg ->
                            // Check if message already exists in UI
                            val existsInUI = messages.any { it.message_id == msg.message_id && msg.message_id > 0 }
                            if (!existsInUI) {
                                cacheMessage(msg)
                                newServerMessages.add(msg)
                                hasNewMessages = true
                                Log.d("ChatInbox", "New message from server: ID=${msg.message_id}, from=${msg.sender_id}")
                            }

                            if (msg.message_id > lastMessageId) {
                                lastMessageId = msg.message_id
                            }
                        }

                        // If there are new messages, add them to UI immediately for better responsiveness
                        if (hasNewMessages && newServerMessages.isNotEmpty()) {
                            runOnUiThread {
                                newServerMessages.sortedBy { it.timestamp }.forEach { newMsg ->
                                    // Only add if it's truly new (double check)
                                    val alreadyExists = messages.any { 
                                        it.message_id == newMsg.message_id || 
                                        (it.timestamp == newMsg.timestamp && it.sender_id == newMsg.sender_id)
                                    }
                                    if (!alreadyExists) {
                                        messages.add(newMsg)
                                        adapter.notifyItemInserted(messages.size - 1)
                                        Log.d("ChatInbox", "Added new message to UI: ${newMsg.message_text}")
                                    }
                                }
                                scrollToBottom()
                            }
                            Log.d("ChatInbox", "✅ UI updated with ${newServerMessages.size} new messages")
                        }
                    } else {
                        Log.e("ChatInbox", "❌ Response data is null")
                    }
                } else {
                    Log.e("ChatInbox", "❌ API returned success=false: ${body?.message}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("ChatInbox", "Fetch messages cancelled")
                // Don't log as error for cancellation
            } catch (e: com.google.gson.JsonSyntaxException) {
                Log.e("ChatInbox", "❌ JSON Parse Error: ${e.message}", e)
                Toast.makeText(
                    this@ChatInboxActivity,
                    "Server returned invalid data",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error fetching messages: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    // ==================== REAL-TIME POLLING ====================

    private fun startMessagePolling() {
        pollingJob?.cancel() // Cancel any existing polling job
        pollingJob = lifecycleScope.launch {
            try {
                while (true) {
                    delay(15000) // Increased to 15 seconds to reduce server load
                    if (!isSendingMessage && !isFinishing) {  // Check if activity is still active
                        fetchMessagesFromServer()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("ChatInbox", "Message polling cancelled")
            }
        }
    }

    // ==================== SEND TEXT MESSAGE ====================

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("ChatInbox", "=== Sending text message ===")
        Log.d("ChatInbox", "Message text: $text")
        Log.d("ChatInbox", "Receiver ID: $targetUserId")
        Log.d("ChatInbox", "Vanish mode: $vanishMode")

        val authToken = sessionManager.getAuthToken()
        val timestamp = System.currentTimeMillis()

        // Save to local DB immediately (pending upload)
        val localId = messageDB.insertMessage(
            messageId = 0,
            threadId = threadId,
            senderId = currentUserId,
            receiverId = targetUserId,
            messageText = text,
            messageType = "text",
            mediaBase64 = null,
            fileName = null,
            fileSize = null,
            timestamp = timestamp,
            vanishMode = vanishMode,
            synced = false,
            pendingUpload = true
        )

        Log.d("ChatInbox", "✅ Message saved locally with ID: $localId")

        // Add message to UI immediately for better UX
        val tempMessage = MessageItem(
            message_id = localId.toInt(),
            sender_id = currentUserId,
            receiver_id = targetUserId,
            message_text = text,
            message_type = "text",
            media_base64 = null,
            file_name = null,
            file_size = null,
            timestamp = timestamp,
            edited = false,
            edited_at = null,
            is_deleted = false,
            vanish_mode = vanishMode,
            seen = false,
            seen_at = null
        )
        
        // Add to messages list and update UI immediately
        messages.add(tempMessage)
        runOnUiThread {
            adapter.notifyItemInserted(messages.size - 1)
            scrollToBottom()
            Log.d("ChatInbox", "✅ Message added to UI immediately")
        }

        // Clear input
        messageInput.text.clear()        // Reload UI
        // Note: Don't call loadCachedMessages here since we already added to UI above

        // Upload to server
        if (authToken != null) {
            lifecycleScope.launch {
                isSendingMessage = true
                try {
                    Log.d("ChatInbox", "📤 Uploading message to server...")

                    val request = SendMessageRequest(
                        auth_token = authToken,
                        receiver_id = targetUserId,
                        message_type = "text",
                        message_text = text,
                        vanish_mode = vanishMode
                    )

                    Log.d(
                        "ChatInbox",
                        "Request: auth_token=${authToken.take(10)}..., receiver_id=$targetUserId"
                    )

                    val response = RetrofitClient.apiService.sendMessage(request)

                    Log.d("ChatInbox", "Upload response code: ${response.code()}")
                    Log.d("ChatInbox", "Upload response success: ${response.isSuccessful}")

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ChatInbox", "❌ Upload failed: $errorBody")
                        Toast.makeText(
                            this@ChatInboxActivity,
                            "Message will be sent when online",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val body = response.body()
                    Log.d("ChatInbox", "Upload response body: $body")

                    if (body?.success == true) {
                        val data = body.data

                        if (data != null) {
                            Log.d("ChatInbox", "✅ Message uploaded! Server ID: ${data.message_id}")

                            // Mark as synced in local DB
                            messageDB.markMessageAsSynced(localId.toInt(), data.message_id)

                            // ✅ Send FCM notification to recipient
                            val senderName = sessionManager.getUsername() ?: "User"
                            Notificationhelperfcm.sendMessageNotification(
                                senderId = currentUserId.toString(),
                                senderName = senderName,
                                receiverId = targetUserId.toString(),
                                messageText = text
                            )

                            // Update the message in the UI with the server ID
                            val messageIndex = messages.indexOfFirst {
                                it.sender_id == currentUserId && it.timestamp == timestamp
                            }
                            if (messageIndex >= 0) {
                                messages[messageIndex] = messages[messageIndex].copy(
                                    message_id = data.message_id
                                )
                                adapter.notifyItemChanged(messageIndex)
                                Log.d("ChatInbox", "✅ Updated message in UI with server ID")
                            }

                            // Note: Don't call loadCachedMessages() here to avoid UI flickering
                        }
                    } else {
                        Log.e("ChatInbox", "❌ Upload returned success=false: ${body?.message}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatInbox", "❌ Error sending message: ${e.message}", e)
                    e.printStackTrace()
                    Toast.makeText(
                        this@ChatInboxActivity,
                        "Message will be sent when online",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isSendingMessage = false
                }
            }
        } else {
            Log.e("ChatInbox", "❌ No auth token for upload")
        }
    }

    // ==================== SEND MEDIA MESSAGE ====================

    private fun sendMediaMessage(
        type: String,
        base64Data: String,
        fileName: String? = null,
        fileSize: Int? = null
    ) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("ChatInbox", "=== Sending $type message ===")
        Log.d("ChatInbox", "File name: $fileName")
        Log.d("ChatInbox", "File size: $fileSize")
        Log.d("ChatInbox", "Base64 length: ${base64Data.length}")

        Toast.makeText(this, "Uploading $type...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val request = SendMessageRequest(
                    auth_token = authToken,
                    receiver_id = targetUserId,
                    message_type = type,
                    media_base64 = base64Data,
                    file_name = fileName,
                    file_size = fileSize,
                    vanish_mode = vanishMode
                )

                val response = RetrofitClient.apiService.sendMessage(request)

                Log.d("ChatInbox", "Media upload response code: ${response.code()}")

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        Log.d("ChatInbox", "✅ $type sent successfully")

                        // ✅ Send FCM notification to recipient
                        val senderName = sessionManager.getUsername() ?: "User"
                        val messageText = when(type) {
                            "image" -> "Sent an image"
                            "video" -> "Sent a video"
                            else -> "Sent a file"
                        }
                        Notificationhelperfcm.sendMessageNotification(
                            senderId = currentUserId.toString(),
                            senderName = senderName,
                            receiverId = targetUserId.toString(),
                            messageText = messageText
                        )

                        // Cache message
                        cacheMessage(
                            MessageItem(
                                message_id = data.message_id,
                                sender_id = data.sender_id,
                                receiver_id = data.receiver_id,
                                message_text = null,
                                message_type = type,
                                media_base64 = base64Data,
                                file_name = fileName,
                                file_size = fileSize,
                                timestamp = data.timestamp,
                                edited = false,
                                edited_at = null,
                                is_deleted = false,
                                vanish_mode = vanishMode,
                                seen = false,
                                seen_at = null
                            )
                        )

                        loadCachedMessages()
                        Toast.makeText(this@ChatInboxActivity, "$type sent", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Log.e("ChatInbox", "❌ Media upload failed")
                    Toast.makeText(
                        this@ChatInboxActivity,
                        "Failed to send $type",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error sending $type: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Failed to send $type", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // ==================== EDIT MESSAGE ====================

    private fun editMessage(message: MessageItem) {
        try {
            if (message.sender_id != currentUserId) {
                Toast.makeText(this, "You can only edit your own messages", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            val timeSince = System.currentTimeMillis() - message.timestamp
            if (timeSince > 5 * 60 * 1000) {
                Toast.makeText(this, "Can only edit messages within 5 minutes", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            if (message.message_type != "text") {
                Toast.makeText(this, "Can only edit text messages", Toast.LENGTH_SHORT).show()
                return
            }

            val input = EditText(this)
            input.setText(message.message_text)
            input.setSelection(input.text.length)

            AlertDialog.Builder(this)
                .setTitle("Edit message")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newText = input.text.toString().trim()
                    if (newText.isEmpty()) {
                        Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    performEditMessage(message.message_id, newText)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("ChatInbox", "❌ Error in editMessage", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performEditMessage(messageId: Int, newText: String) {
        val authToken = sessionManager.getAuthToken() ?: return

        lifecycleScope.launch {
            try {
                val request = EditMessageRequest(
                    auth_token = authToken,
                    message_id = messageId,
                    new_text = newText
                )

                val response = RetrofitClient.apiService.editMessage(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        messageDB.updateMessageText(messageId, newText, data.edited_at)
                        loadCachedMessages()
                        Toast.makeText(
                            this@ChatInboxActivity,
                            "Message updated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@ChatInboxActivity,
                        response.body()?.message ?: "Failed to edit",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error editing message: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // ==================== DELETE MESSAGE ====================

    private fun deleteMessage(message: MessageItem) {
        try {
            if (message.sender_id != currentUserId) {
                Toast.makeText(this, "You can only delete your own messages", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            val timeSince = System.currentTimeMillis() - message.timestamp
            if (timeSince > 5 * 60 * 1000) {
                Toast.makeText(
                    this,
                    "Can only delete messages within 5 minutes",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            AlertDialog.Builder(this)
                .setTitle("Delete message?")
                .setMessage("This action cannot be undone")
                .setPositiveButton("Delete") { _, _ ->
                    performDeleteMessage(message.message_id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("ChatInbox", "❌ Error in deleteMessage", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performDeleteMessage(messageId: Int) {
        val authToken = sessionManager.getAuthToken() ?: return

        lifecycleScope.launch {
            try {
                val request = DeleteMessageRequest(
                    auth_token = authToken,
                    message_id = messageId
                )

                val response = RetrofitClient.apiService.deleteMessage(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        messageDB.deleteMessage(messageId, data.deleted_at)
                        loadCachedMessages()
                        Toast.makeText(
                            this@ChatInboxActivity,
                            "Message deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@ChatInboxActivity,
                        response.body()?.message ?: "Failed to delete",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error deleting message: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // ==================== VANISH MODE ====================

    private fun toggleVanishMode() {
        val authToken = sessionManager.getAuthToken() ?: return

        AlertDialog.Builder(this)
            .setTitle("Vanish Mode")
            .setMessage(if (vanishMode) "Disable vanish mode?" else "Enable vanish mode?\n\nMessages will disappear once seen and the chat is closed.")
            .setPositiveButton(if (vanishMode) "Disable" else "Enable") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val request = ToggleVanishModeRequest(
                            auth_token = authToken,
                            other_user_id = targetUserId,
                            vanish_mode = !vanishMode
                        )

                        val response = RetrofitClient.apiService.toggleVanishMode(request)

                        if (response.isSuccessful && response.body()?.success == true) {
                            val data = response.body()?.data

                            if (data != null) {
                                vanishMode = data.vanish_mode
                                updateVanishModeUI()

                                Toast.makeText(
                                    this@ChatInboxActivity,
                                    if (vanishMode) "Vanish mode enabled" else "Vanish mode disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.d("ChatInbox", "Vanish mode toggle cancelled")
                    } catch (e: Exception) {
                        Log.e("ChatInbox", "❌ Error toggling vanish mode: ${e.message}", e)
                        Toast.makeText(
                            this@ChatInboxActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateVanishModeUI() {
        vanishModeBtn.setImageResource(
            if (vanishMode) R.drawable.ic_vanish_on else R.drawable.ic_vanish_off
        )
    }

    // ==================== HELPERS ====================

    private fun cacheMessage(message: MessageItem) {
        messageDB.insertMessage(
            messageId = message.message_id,
            threadId = threadId,
            senderId = message.sender_id,
            receiverId = message.receiver_id,
            messageText = message.message_text,
            messageType = message.message_type,
            mediaBase64 = message.media_base64,
            fileName = message.file_name,
            fileSize = message.file_size,
            timestamp = message.timestamp,
            vanishMode = message.vanish_mode,
            synced = true,
            pendingUpload = false
        )
    }

    private fun syncPendingMessages() {
        val authToken = sessionManager.getAuthToken() ?: return

        lifecycleScope.launch {
            try {
                val pendingMessages = messageDB.getPendingUploadMessages()

                Log.d("ChatInbox", "Syncing ${pendingMessages.size} pending messages")

                pendingMessages.forEach { pending ->
                    try {
                        val request = SendMessageRequest(
                            auth_token = authToken,
                            receiver_id = pending.receiverId,
                            message_type = pending.messageType,
                            message_text = pending.messageText,
                            media_base64 = pending.mediaBase64,
                            file_name = pending.fileName,
                            file_size = pending.fileSize,
                            vanish_mode = pending.vanishMode
                        )

                        val response = RetrofitClient.apiService.sendMessage(request)

                        if (response.isSuccessful && response.body()?.success == true) {
                            val data = response.body()?.data
                            if (data != null) {
                                messageDB.markMessageAsSynced(pending.id, data.message_id)
                                Log.d("ChatInbox", "✅ Synced pending message ${pending.id}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatInbox", "❌ Error syncing message ${pending.id}: ${e.message}")
                    }
                }

                if (pendingMessages.isNotEmpty()) {
                    loadCachedMessages()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error syncing pending messages: ${e.message}", e)
            }
        }
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            recycler.scrollToPosition(messages.size - 1)
        }
    }

    // ==================== MEDIA PICKERS ====================

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        intent.type = "video/*"
        startActivityForResult(intent, PICK_VIDEO_REQUEST)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return

            when (requestCode) {
                PICK_IMAGE_REQUEST -> handleImageUpload(uri)
                PICK_VIDEO_REQUEST -> handleVideoUpload(uri)
                PICK_FILE_REQUEST -> handleFileUpload(uri)
            }
        }
    }

    private fun handleImageUpload(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    // Compress image
                    val maxSize = 1024
                    val scale = minOf(
                        maxSize.toFloat() / bitmap.width,
                        maxSize.toFloat() / bitmap.height,
                        1.0f
                    )

                    val scaledBitmap = if (scale < 1.0f) {
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(),
                            true
                        )
                    } else {
                        bitmap
                    }

                    val baos = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                    sendMediaMessage("image", base64Image)
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "❌ Error uploading image: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Error uploading image", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun handleVideoUpload(uri: Uri) {
        Toast.makeText(this, "Video upload - compress and implement", Toast.LENGTH_SHORT).show()
    }

    private fun handleFileUpload(uri: Uri) {
        Toast.makeText(this, "File upload - implement", Toast.LENGTH_SHORT).show()
    }

    // ==================== STORAGE PERMISSION FOR SCREENSHOTS ====================
    
    /**
     * Check if storage permission is granted, request if not
     */
    private fun checkAndRequestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ChatInbox", "📱 Storage permission not granted - requesting permission")
            
            // Show explanation dialog if needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showStoragePermissionExplanation(permission)
            } else {
                // Request permission directly
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    STORAGE_PERMISSION_REQUEST
                )
            }
        } else {
            Log.d("ChatInbox", "✅ Storage permission already granted")
            // Permission already granted, start screenshot detection
            startScreenshotDetection()
        }
    }
    
    /**
     * Show explanation dialog for storage permission
     */
    private fun showStoragePermissionExplanation(permission: String) {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("Screenshot detection requires access to your device's media files. This allows the app to detect when you or others take screenshots during the chat.")
            .setPositiveButton("Grant Permission") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    STORAGE_PERMISSION_REQUEST
                )
            }
            .setNegativeButton("Skip") { _, _ ->
                Log.d("ChatInbox", "⚠️ User declined storage permission - screenshot detection disabled")
                Toast.makeText(this, "Screenshot detection disabled", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    /**
     * Handle permission request result
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("ChatInbox", "✅ Storage permission granted by user")
                    Toast.makeText(this, "Screenshot detection enabled", Toast.LENGTH_SHORT).show()
                    startScreenshotDetection()
                } else {
                    Log.d("ChatInbox", "❌ Storage permission denied by user")
                    Toast.makeText(this, "Screenshot detection disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Start screenshot detection after permission is granted
     */
    private fun startScreenshotDetection() {
        Log.d("ChatInbox", "🔄 Starting screenshot detection...")
        screenshotDetector?.startWatching()
        Log.d("ChatInbox", "✅ Screenshot detection started")
    }

    // ==================== CALLS ====================

    private fun startCallToTarget(isVideo: Boolean) {
        Log.d("ChatInbox", "=== Starting call to target ===" )
        Log.d("ChatInbox", "Current User ID: $currentUserId")
        Log.d("ChatInbox", "Target User ID: $targetUserId")
        Log.d("ChatInbox", "Is Video: $isVideo")
        
        val channel = threadId + "_" + System.currentTimeMillis().toString().takeLast(5)
        val callerName = sessionManager.getUsername() ?: "Caller"
        
        Log.d("ChatInbox", "Channel Name: $channel")
        Log.d("ChatInbox", "Caller Name: $callerName")

        val callMap = mapOf(
            "callerId" to currentUserId.toString(),
            "callerName" to callerName,
            "calleeId" to targetUserId.toString(),
            "calleeName" to targetName,
            "status" to "ringing",
            "type" to if (isVideo) "video" else "voice",
            "started" to false,
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("ChatInbox", "Creating call data in Firebase: $callMap")
        
        FirebaseDatabase.getInstance(
            "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
        ).reference.child("calls").child(channel).setValue(callMap)
            .addOnSuccessListener {
                Log.d("ChatInbox", "✅ Call data created successfully in Firebase")
                
                val intent = Intent(this, CallActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channel)
                    putExtra("IS_CALLER", true)
                    putExtra("IS_VIDEO", isVideo)
                    putExtra("TARGET_USER_ID", targetUserId)
                    putExtra("TARGET_NAME", targetName)
                }
                
                Log.d("ChatInbox", "Starting CallActivity with channel: $channel")
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Log.e("ChatInbox", "❌ Failed to create call: ${e.message}", e)
                Toast.makeText(this, "Failed to initiate call", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForIncomingCalls() {
        val currentUserIdString = currentUserId.toString()
        val currentUserEmail = sessionManager.getEmail()
        val currentFirebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        
        Log.d("ChatInbox", "=== Starting incoming call listener ===" )
        Log.d("ChatInbox", "Listening for calls to:")
        Log.d("ChatInbox", "  - User ID: $currentUserIdString")
        Log.d("ChatInbox", "  - Email: $currentUserEmail")
        Log.d("ChatInbox", "  - Firebase UID: $currentFirebaseUid")
        
        val callsRef = FirebaseDatabase.getInstance(
            "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
        ).reference.child("calls")

        callsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("ChatInbox", "📞 Raw Firebase snapshot: ${snapshot.value}")
                Log.d("ChatInbox", "📞 Available keys: ${snapshot.children.map { it.key }}")
                
                // Handle multiple call data formats
                val calleeIdString = snapshot.child("calleeId").getValue(String::class.java)
                val calleeIdInt = try {
                    calleeIdString?.toIntOrNull()
                } catch (e: Exception) {
                    null
                }
                
                // Handle email-based format (old system)
                val toId = snapshot.child("toId").getValue(String::class.java)
                val toEmail = snapshot.child("to").getValue(String::class.java)
                
                val status = snapshot.child("status").getValue(String::class.java)
                
                Log.d("ChatInbox", "📞 Call event detected: calleeIdString=$calleeIdString, calleeIdInt=$calleeIdInt, toId=$toId, status=$status")
                Log.d("ChatInbox", "Current identifiers: userID=$currentUserIdString, email=$currentUserEmail, firebaseUID=$currentFirebaseUid")
                
                // Check if this call is for the current user (multiple formats)
                val isIncomingCall = when {
                    // Format 1: Database user ID (integer or string)
                    calleeIdString == currentUserIdString || calleeIdInt == currentUserId -> true
                    // Format 2: Firebase Auth UID
                    currentFirebaseUid != null && calleeIdString == currentFirebaseUid -> true
                    // Format 3: Email-based (old system)
                    toId != null && currentUserEmail != null && toId.replace("_at_", "@").replace("_com", ".com") == currentUserEmail -> true
                    toEmail != null && currentUserEmail != null && toEmail == currentUserEmail -> true
                    else -> false
                }
                
                if (isIncomingCall && status == "ringing") {
                    Log.d("ChatInbox", "✅ INCOMING CALL FOR THIS USER!")
                    // Check if call is recent (within last 2 minutes) to avoid processing old calls
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val currentTime = System.currentTimeMillis()
                    val timeDifference = currentTime - timestamp
                    val twoMinutesInMs = 2 * 60 * 1000L // 2 minutes
                    
                    if (timeDifference > twoMinutesInMs) {
                        Log.d("ChatInbox", "📞 Ignoring old call (${timeDifference / 1000}s ago)")
                        return
                    }
                    
                    val channelName = snapshot.key ?: return
                    val isVideo = when (val type = snapshot.child("type").getValue(String::class.java)) {
                        "video" -> true
                        "voice" -> false
                        else -> true
                    }
                    
                    // Get caller name from multiple possible fields
                    val callerName = snapshot.child("callerName").getValue(String::class.java)
                        ?: snapshot.child("from").getValue(String::class.java)
                        ?: "Unknown Caller"
                    
                    Log.d("ChatInbox", "✅ Incoming call detected!")
                    Log.d("ChatInbox", "Channel: $channelName")
                    Log.d("ChatInbox", "Is Video: $isVideo")
                    Log.d("ChatInbox", "Caller: $callerName")

                    val intent = Intent(this@ChatInboxActivity, IncomingCallActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("IS_VIDEO", isVideo)
                        putExtra("CALLER_NAME", callerName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    
                    startActivity(intent)
                } else {
                    if (!isIncomingCall) {
                        Log.d("ChatInbox", "📞 Call is for user $calleeIdString, not for current user $currentUserIdString")
                    } else {
                        Log.d("ChatInbox", "📞 Call status is '$status', not 'ringing'")
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle call status changes if needed
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatInbox", "❌ Call listener error: ${error.message}")
            }
        })
        
        Log.d("ChatInbox", "✅ Incoming call listener started")
        
        // Clean up old call records (older than 10 minutes) to reduce noise
        cleanupOldCallRecords()
    }
    
    private fun cleanupOldCallRecords() {
        val callsRef = FirebaseDatabase.getInstance(
            "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
        ).reference.child("calls")
        
        val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000) // 10 minutes
        
        callsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: continue
                    val status = child.child("status").getValue(String::class.java)
                    
                    // Remove old completed calls
                    if (timestamp < tenMinutesAgo && (status == "ended" || status == "declined")) {
                        child.ref.removeValue()
                        Log.d("ChatInbox", "🗑️ Cleaned up old call record: ${child.key}")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatInbox", "Failed to cleanup old calls: ${error.message}")
            }
        })
    }

    // ==================== LIFECYCLE ====================

    override fun onBackPressed() {
        // Clear vanish mode messages before leaving
        if (vanishMode) {
            messageDB.clearVanishMessages(threadId)

            val authToken = sessionManager.getAuthToken()
            if (authToken != null) {
                lifecycleScope.launch {
                    try {
                        RetrofitClient.apiService.clearVanishMessages(
                            ClearVanishMessagesRequest(authToken, targetUserId)
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.d("ChatInbox", "Clear vanish messages cancelled")
                    } catch (e: Exception) {
                        Log.e("ChatInbox", "❌ Error clearing vanish messages: ${e.message}")
                    }
                }
            }
        }

        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // Screenshot detection is started after permission is granted
        Log.d("ChatInbox", "Chat activity resumed")

        // Refresh messages when returning to chat
        loadCachedMessages()
        if (!isSendingMessage) {
            fetchMessagesFromServer()
        }
    }

    override fun onPause() {
        super.onPause()
        screenshotDetector?.stopWatching()
        Log.d("ChatInbox", "Screenshot detection stopped")
    }

    /**
     * Add screenshot system message to the chat
     */
    private fun addScreenshotSystemMessage(systemMessage: String, timestamp: Long) {
        Log.d("ChatInbox", "📨 Received system message callback: $systemMessage")
        
        // Check if this system message already exists (to avoid duplicates)
        val existingSystemMessage = messages.any { 
            it.message_type == "system" && 
            it.message_text == systemMessage &&
            (timestamp - it.timestamp) < 60000 // Within last minute
        }
        
        Log.d("ChatInbox", "🔍 Duplicate check: exists=$existingSystemMessage")
        
        if (!existingSystemMessage) {
            Log.d("ChatInbox", "💾 Inserting system message to database...")
            
            // Insert into local database
            val localId = messageDB.insertMessage(
                messageId = 0,
                threadId = threadId,
                senderId = -1, // System messages use -1
                receiverId = -1,
                messageText = systemMessage,
                messageType = "system",
                mediaBase64 = null,
                fileName = null,
                fileSize = null,
                timestamp = timestamp,
                vanishMode = false,
                synced = true,
                pendingUpload = false
            )
            
            Log.d("ChatInbox", "✅ Database insert completed with ID: $localId")
            
            // Add to UI
            val systemMessageItem = MessageItem(
                message_id = localId.toInt(),
                sender_id = -1,
                receiver_id = -1,
                message_text = systemMessage,
                message_type = "system",
                media_base64 = null,
                file_name = null,
                file_size = null,
                timestamp = timestamp,
                edited = false,
                edited_at = null,
                is_deleted = false,
                vanish_mode = false,
                seen = true,
                seen_at = timestamp
            )
            
            Log.d("ChatInbox", "📱 Adding message to UI (position ${messages.size})...")
            
            messages.add(systemMessageItem)
            adapter.notifyItemInserted(messages.size - 1)
            scrollToBottom()
            
            Log.d("ChatInbox", "✅ Screenshot system message added to chat successfully")
        } else {
            Log.d("ChatInbox", "⚠️ Duplicate system message detected - skipping")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotDetector?.stopWatching()
        screenshotDetector = null
        pollingJob?.cancel()
        Log.d("ChatInbox", "ChatInboxActivity destroyed and cleanup completed")
    }
}