package com.FEdev.i221279_i220809

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.FEdev.i221279_i220809.database.MessageDatabaseHelper
import com.FEdev.i221279_i220809.models.*
import com.FEdev.i221279_i220809.network.ApiService

import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ChatInboxActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var photoBtn: ImageView
    private lateinit var videoBtn: ImageView
    private lateinit var fileBtn: ImageView
    private lateinit var vanishModeBtn: ImageView
    private lateinit var title: TextView

    private val sessionManager by lazy { SessionManager(this) }
    private val messageDB by lazy { MessageDatabaseHelper(this) }

//    private val messages = mutableListOf<ApiService.MessageItem>()
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

    private var pollingJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_inbox)

        currentUserId = sessionManager.getUserId()
        targetUserId = intent.getIntExtra("targetUserId", 0)
        targetName = intent.getStringExtra("targetName") ?: "User"

        if (currentUserId <= 0 || targetUserId <= 0) {
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        threadId = if (currentUserId < targetUserId) {
            "${currentUserId}_${targetUserId}"
        } else {
            "${targetUserId}_${currentUserId}"
        }

        screenshotDetector = ScreenshotDetector(
            context = this,
            targetUserId = targetUserId,
            targetUsername = targetName
        )



        bindViews()
        setupRecycler()
        loadCachedMessages()
        fetchMessagesFromServer()
        startMessagePolling()
        syncPendingMessages()
        listenForIncomingCalls()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }

        findViewById<ImageView>(R.id.btnCall).setOnClickListener {
            startCallToTarget(isVideo = true)
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
        title.text = targetName
    }

    private fun setupRecycler() {
        adapter = NewChatAdapter(
            context = this,
            messages = messages,
            currentUserId = currentUserId,
            onEditClick = { msg: MessageItem -> editMessage(msg) },  // Explicit type: MessageItem
            onDeleteClick = { msg: MessageItem -> deleteMessage(msg) }  // Explicit type: MessageItem
        )
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter
    }

    // ==================== LOAD CACHED MESSAGES ====================

    private fun loadCachedMessages() {
        lifecycleScope.launch {
            try {
                val cachedMessages = messageDB.getMessages(threadId)

                messages.clear()
                messages.addAll(cachedMessages.map { cached ->
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
                })

                adapter.notifyDataSetChanged()
                scrollToBottom()

                Log.d("ChatInbox", "✅ Loaded ${messages.size} cached messages")
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error loading cached messages: ${e.message}", e)
            }
        }
    }

    // ==================== FETCH FROM SERVER ====================

    private fun fetchMessagesFromServer() {
        val authToken = sessionManager.getAuthToken() ?: return

        lifecycleScope.launch {
            try {
                val request = GetMessagesRequest(
                    auth_token = authToken,
                    other_user_id = targetUserId,
                    last_message_id = lastMessageId
                )

                val response = RetrofitClient.apiService.getMessages(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        vanishMode = data.vanish_mode
                        updateVanishModeUI()

                        // Cache new messages
                        data.messages.forEach { msg ->
                            cacheMessage(msg)

                            // Update last message ID
                            if (msg.message_id > lastMessageId) {
                                lastMessageId = msg.message_id
                            }
                        }

                        // Reload from cache to reflect changes
                        loadCachedMessages()

                        Log.d("ChatInbox", "✅ Fetched ${data.messages.size} messages from server")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error fetching messages: ${e.message}", e)
            }
        }
    }

    // ==================== REAL-TIME POLLING ====================

    private fun startMessagePolling() {
        pollingJob = lifecycleScope.launch {
            while (true) {
                delay(3000) // Poll every 3 seconds
                fetchMessagesFromServer()
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

        val authToken = sessionManager.getAuthToken()

        val timestamp = System.currentTimeMillis()

        // Save to local DB immediately (pending upload)
        val localId = messageDB.insertMessage(
            messageId = 0, // Will be updated after upload
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

        // Clear input
        messageInput.text.clear()

        // Reload UI
        loadCachedMessages()

        // Upload to server
        if (authToken != null) {
            lifecycleScope.launch {
                try {
                    val request = SendMessageRequest(
                        auth_token = authToken,
                        receiver_id = targetUserId,
                        message_type = "text",
                        message_text = text,
                        vanish_mode = vanishMode
                    )

                    val response = RetrofitClient.apiService.sendMessage(request)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data

                        if (data != null) {
                            // Mark as synced in local DB
                            messageDB.markMessageAsSynced(localId.toInt(), data.message_id)
                            loadCachedMessages()

                            Log.d("ChatInbox", "✅ Message sent and synced")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatInbox", "Error sending message: ${e.message}", e)
                    Toast.makeText(this@ChatInboxActivity, "Message will be sent when online", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== SEND MEDIA MESSAGE ====================

    private fun sendMediaMessage(type: String, base64Data: String, fileName: String? = null, fileSize: Int? = null) {
        val authToken = sessionManager.getAuthToken() ?: return

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

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data

                    if (data != null) {
                        // Cache message
                        cacheMessage(MessageItem(
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
                        ))

                        loadCachedMessages()
                        Toast.makeText(this@ChatInboxActivity, "$type sent", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error sending $type: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Failed to send $type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== EDIT MESSAGE ====================

    private fun editMessage(message: MessageItem) {
        try {
            if (message.sender_id != currentUserId) {
                Toast.makeText(this, "You can only edit your own messages", Toast.LENGTH_SHORT).show()
                return
            }

            val timeSince = System.currentTimeMillis() - message.timestamp
            if (timeSince > 5 * 60 * 1000) {
                Toast.makeText(this, "Can only edit messages within 5 minutes", Toast.LENGTH_SHORT).show()
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
            Log.e("ChatInbox", "Error in editMessage", e)
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
                        // Update in local DB
                        messageDB.updateMessageText(messageId, newText, data.edited_at)
                        loadCachedMessages()

                        Toast.makeText(this@ChatInboxActivity, "Message updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ChatInboxActivity, response.body()?.message ?: "Failed to edit", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error editing message: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== DELETE MESSAGE ====================

    private fun deleteMessage(message: MessageItem) {
        try {
            if (message.sender_id != currentUserId) {
                Toast.makeText(this, "You can only delete your own messages", Toast.LENGTH_SHORT).show()
                return
            }

            val timeSince = System.currentTimeMillis() - message.timestamp
            if (timeSince > 5 * 60 * 1000) {
                Toast.makeText(this, "Can only delete messages within 5 minutes", Toast.LENGTH_SHORT).show()
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
            Log.e("ChatInbox", "Error in deleteMessage", e)
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
                        // Update in local DB
                        messageDB.deleteMessage(messageId, data.deleted_at)
                        loadCachedMessages()

                        Toast.makeText(this@ChatInboxActivity, "Message deleted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ChatInboxActivity, response.body()?.message ?: "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error deleting message: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    } catch (e: Exception) {
                        Log.e("ChatInbox", "Error toggling vanish mode: ${e.message}", e)
                        Toast.makeText(this@ChatInboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    // Upload to server
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
                }

                if (pendingMessages.isNotEmpty()) {
                    loadCachedMessages()
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error syncing pending messages: ${e.message}", e)
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
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                    val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
                    sendMediaMessage("image", base64Image)
                }
            } catch (e: Exception) {
                Log.e("ChatInbox", "Error uploading image: ${e.message}", e)
                Toast.makeText(this@ChatInboxActivity, "Error uploading image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleVideoUpload(uri: Uri) {
        // Similar to image, but with file size check
        Toast.makeText(this, "Video upload - implement compression", Toast.LENGTH_SHORT).show()
    }

    private fun handleFileUpload(uri: Uri) {
        // Similar to image, get file name and size
        Toast.makeText(this, "File upload - implement", Toast.LENGTH_SHORT).show()
    }

    // ==================== CALLS ====================

    private fun startCallToTarget(isVideo: Boolean) {
        val channel = threadId + "_" + System.currentTimeMillis().toString().takeLast(5)
        val callerName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Caller"

        val callMap = mapOf(
            "callerId" to currentUserId.toString(),
            "callerName" to callerName,
            "calleeId" to targetUserId.toString(),
            "status" to "ringing",
            "type" to if (isVideo) "video" else "voice",
            "started" to false
        )

        FirebaseDatabase.getInstance(
            "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
        ).reference.child("calls").child(channel).setValue(callMap)
            .addOnSuccessListener {
                val intent = Intent(this, CallActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channel)
                    putExtra("IS_CALLER", true)
                    putExtra("IS_VIDEO", isVideo)
                    putExtra("TARGET_UID", targetUserId.toString())
                }
                startActivity(intent)
            }
    }

    private fun listenForIncomingCalls() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val callsRef = FirebaseDatabase.getInstance(
            "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
        ).reference.child("calls")

        callsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val calleeId = snapshot.child("calleeId").getValue(String::class.java)
                val status = snapshot.child("status").getValue(String::class.java)
                if (calleeId == userId && status == "ringing") {
                    val channelName = snapshot.key ?: return
                    val isVideo = snapshot.child("type").getValue(String::class.java) == "video"

                    val intent = Intent(this@ChatInboxActivity, IncomingCallActivity::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("IS_VIDEO", isVideo)
                    }
                    startActivity(intent)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
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
                    } catch (e: Exception) {
                        Log.e("ChatInbox", "Error clearing vanish messages: ${e.message}")
                    }
                }
            }
        }

        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        screenshotDetector?.startWatching()
        Log.d("ChatInbox", "Screenshot detection started")
    }

    // Stop screenshot detection when activity is not visible
    override fun onPause() {
        super.onPause()
        screenshotDetector?.stopWatching()
        Log.d("ChatInbox", "Screenshot detection stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotDetector?.stopWatching()
        screenshotDetector = null
        pollingJob?.cancel()
    }
}