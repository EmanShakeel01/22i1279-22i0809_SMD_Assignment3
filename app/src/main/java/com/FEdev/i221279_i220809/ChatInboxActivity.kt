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
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class ChatInboxActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var photoBtn: ImageView
    private lateinit var title: TextView

    private val rtdb = FirebaseDatabase.getInstance(
        "https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/"
    ).reference

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: chatadapter

    private var currentUid: String = ""
    private var targetUid: String = ""
    private var targetName: String = ""
    private var chatDocId: String = ""
    private var messagesListener: ValueEventListener? = null

    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_inbox)

        currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        targetUid = intent.getStringExtra("targetUid") ?: ""
        targetName = intent.getStringExtra("targetName") ?: "User"

        if (currentUid.isEmpty() || targetUid.isEmpty()) {
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatDocId = threadId(currentUid, targetUid)

        bindViews()
        setupRecycler()
        subscribeMessages()
        listenForIncomingCalls()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnCall).setOnClickListener {
            startCallToTarget(isVideo = true)
        }

        sendBtn.setOnClickListener { sendTextMessage() }
        photoBtn.setOnClickListener { openImagePicker() }
    }

    private fun bindViews() {
        recycler = findViewById(R.id.recyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendBtn = findViewById(R.id.send)
        photoBtn = findViewById(R.id.photoButton)
        title = findViewById(R.id.chatTitle)
        title.text = targetName
    }

    private fun setupRecycler() {
        adapter = chatadapter(
            this, messages, currentUserId = currentUid,
            onEditClick = { msg -> editMessage(msg) },
            onDeleteClick = { msg -> deleteMessage(msg) }
        )
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter
    }

    private fun threadId(a: String, b: String): String {
        return if (a < b) "${a}_${b}" else "${b}_${a}"
    }

    private fun chatsRef() = rtdb.child("chats").child(chatDocId).child("messages")

    private fun subscribeMessages() {
        val ref = chatsRef()

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages.clear()

                // Get all messages and sort by timestamp
                val messageList = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val msg = child.getValue(Message::class.java)
                    if (msg != null) {
                        messageList.add(msg)
                    }
                }

                // Sort by timestamp
                messageList.sortBy { it.timestamp }
                messages.addAll(messageList)

                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    recycler.scrollToPosition(messages.size - 1)
                }

                Log.d("ChatInbox", "Loaded ${messages.size} messages")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatInbox", "Error loading messages: ${error.message}")
                Toast.makeText(this@ChatInboxActivity, "Error loading messages", Toast.LENGTH_SHORT).show()
            }
        }

        ref.addValueEventListener(messagesListener!!)
    }

    private fun startCallToTarget(isVideo: Boolean) {
        val channel = threadId(currentUid, targetUid) + "_" + System.currentTimeMillis().toString().takeLast(5)
        val callerName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Caller"

        val callMap = mapOf(
            "callerId" to currentUid,
            "callerName" to callerName,
            "calleeId" to targetUid,
            "status" to "ringing",
            "type" to if (isVideo) "video" else "voice",
            "started" to false
        )

        rtdb.child("calls").child(channel).setValue(callMap)
            .addOnSuccessListener {
                val intent = Intent(this, CallActivity::class.java).apply {
                    putExtra("CHANNEL_NAME", channel)
                    putExtra("IS_CALLER", true)
                    putExtra("IS_VIDEO", isVideo)
                    putExtra("TARGET_UID", targetUid)
                }
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to start call: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForIncomingCalls() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val callsRef = rtdb.child("calls")

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

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        val messageId = chatsRef().push().key ?: return
        val msg = Message(
            messageId = messageId,
            senderId = currentUid,
            messageText = text,
            messageType = "text",
            imageUrl = null,
            postContent = null,
            timestamp = System.currentTimeMillis(),
            edited = false
        )

        chatsRef().child(messageId).setValue(msg)
            .addOnSuccessListener {
                messageInput.text.clear()
                Log.d("ChatInbox", "Message sent successfully: $messageId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatInbox", "Error sending message", e)
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data
            if (imageUri != null) uploadImageAndSendMessage(imageUri)
        }
    }

    private fun uploadImageAndSendMessage(imageUri: Uri) {
        contentResolver.openInputStream(imageUri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val baos = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
            val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
            sendImageMessage(base64Image)
        }
    }

    private fun sendImageMessage(base64Image: String) {
        val messageId = chatsRef().push().key ?: return
        val msg = Message(
            messageId = messageId,
            senderId = currentUid,
            messageText = null,
            messageType = "image",
            imageUrl = base64Image,
            postContent = null,
            timestamp = System.currentTimeMillis(),
            edited = false
        )

        chatsRef().child(messageId).setValue(msg)
            .addOnSuccessListener {
                Log.d("ChatInbox", "Image sent successfully")
            }
            .addOnFailureListener { e ->
                Log.e("ChatInbox", "Error sending image", e)
                Toast.makeText(this, "Failed to send image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun editMessage(message: Message) {
        try {
            if (message.senderId != currentUid) {
                Toast.makeText(this, "You can only edit your own messages", Toast.LENGTH_SHORT).show()
                return
            }

            val timeSince = System.currentTimeMillis() - message.timestamp
            if (timeSince > 5 * 60 * 1000) {
                Toast.makeText(this, "Can only edit messages within 5 minutes", Toast.LENGTH_SHORT).show()
                return
            }

            if (message.messageType != "text") {
                Toast.makeText(this, "Can only edit text messages", Toast.LENGTH_SHORT).show()
                return
            }

            val input = EditText(this)
            input.setText(message.messageText)
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

                    chatsRef().child(message.messageId).updateChildren(mapOf(
                        "messageText" to newText,
                        "edited" to true
                    ))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Message updated", Toast.LENGTH_SHORT).show()
                            Log.d("ChatInbox", "Message edited successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatInbox", "Error updating message", e)
                            Toast.makeText(this, "Failed to update message: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("ChatInbox", "Error in editMessage", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteMessage(message: Message) {
        try {
            if (message.senderId != currentUid) {
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
                    chatsRef().child(message.messageId).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                            Log.d("ChatInbox", "Message deleted successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatInbox", "Error deleting message", e)
                            Toast.makeText(this, "Failed to delete message: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("ChatInbox", "Error in deleteMessage", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let { chatsRef().removeEventListener(it) }
    }
}