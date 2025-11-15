package com.FEdev.i221279_i220809

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*

class inbox : AppCompatActivity() {

    private lateinit var currentUserEmail: String
    private lateinit var targetUserEmail: String
    private lateinit var currentUserId: String
    private lateinit var targetUserId: String
    private val db by lazy {
        FirebaseDatabase.getInstance("https://i1279-22i0809-assignment2-default-rtdb.firebaseio.com/").reference
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        currentUserEmail = "emanshakeel909@gmail.com"
        targetUserEmail = "fizawajid001@gmail.com"

        currentUserId = emailToFirebaseKey(currentUserEmail)
        targetUserId = emailToFirebaseKey(targetUserEmail)

        val callBtn = findViewById<ImageView>(R.id.call)
        callBtn.setOnClickListener {
            startCall(targetUserEmail, isVideo = true)
        }

        listenForIncomingCalls()
    }

    private fun emailToFirebaseKey(email: String): String {
        return email.replace(".", "_").replace("@", "_at_")
    }

    @SuppressLint("RestrictedApi")
    private fun startCall(targetUserEmail: String, isVideo: Boolean) {
        val timestamp = System.currentTimeMillis()
        val channel = "call_${currentUserId}_${targetUserId}_$timestamp"

        val callRef = db.child("calls").child(channel)
        val payload = mapOf(
            "from" to currentUserEmail,
            "to" to targetUserEmail,
            "fromId" to currentUserId,
            "toId" to targetUserId,
            "status" to "ringing",
            "type" to if (isVideo) "video" else "audio",
            "timestamp" to ServerValue.TIMESTAMP,
            "channelName" to channel
        )

        callRef.setValue(payload)
            .addOnSuccessListener {
                listenForCallUpdates(channel)
                val intent = Intent(this, CallActivity::class.java)
                intent.putExtra("CHANNEL_NAME", channel)
                intent.putExtra("IS_CALLER", true)
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun listenForCallUpdates(channel: String) {
        db.child("calls").child(channel).child("status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    when (snapshot.getValue(String::class.java)) {
                        "declined" -> {
                            Toast.makeText(this@inbox, "Call declined", Toast.LENGTH_SHORT).show()
                        }
                        "ended" -> {
                            Toast.makeText(this@inbox, "Call ended", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForIncomingCalls() {
        db.child("calls")
            .orderByChild("to")
            .equalTo(currentUserEmail)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val callData = snapshot.value as? Map<String, Any> ?: return
                    val status = callData["status"] as? String ?: return
                    val caller = callData["from"] as? String ?: ""
                    val channel = callData["channelName"] as? String ?: ""

                    if (status == "ringing") {
                        val intent = Intent(this@inbox, IncomingCallActivity::class.java)
                        intent.putExtra("CALLER_NAME", caller)
                        intent.putExtra("CHANNEL_NAME", channel)
                        startActivity(intent)
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}








//
//
//package com.FEdev.i221279_i220809
//
//import android.app.AlertDialog
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import android.widget.EditText
//import android.widget.ImageButton
//import android.widget.ImageView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.Query
//import android.provider.MediaStore
//import com.google.firebase.storage.FirebaseStorage
//import android.net.Uri
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import java.io.ByteArrayOutputStream
//import java.io.InputStream
//import java.util.UUID
//
//
//class inbox : AppCompatActivity() {
//
//    private lateinit var recyclerView: RecyclerView
//    private lateinit var adapter: chatadapter
//    private lateinit var messageInput: EditText
//    private lateinit var sendButton: ImageView
//    private val messages = mutableListOf<Message>()
//    private val db = FirebaseFirestore.getInstance()
//    private var currentUserId: String = ""
//    private val chatId = "sample_chat_1"
//
//    private lateinit var photoButton: ImageView
//    private val PICK_IMAGE_REQUEST = 1
//    private val storage = FirebaseStorage.getInstance()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//
//        try {
//            Log.d("inbox", "onCreate started")
//            setContentView(R.layout.activity_inbox)
//            Log.d("inbox", "setContentView successful")
//
//            val back_arrow=findViewById<ImageButton>(R.id.back_arrow)
//            back_arrow.setOnClickListener {
//                val intent = Intent(this, activityprofile2::class.java)
//                startActivity(intent)
//            }
//
//            // Get current user ID from Firebase Auth or use a mock ID for testing
//            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "test_user_123"
//
//            Log.d("inbox", "Current user ID: $currentUserId")
//
//            // Initialize views with null checks
//            try {
//                recyclerView = findViewById(R.id.recyclerView)
//                Log.d("inbox", "RecyclerView found")
//            } catch (e: Exception) {
//                Log.e("inbox", "Error finding RecyclerView", e)
//                Toast.makeText(this, "Error: RecyclerView not found", Toast.LENGTH_SHORT).show()
//                finish()
//                return
//            }
//
//            try {
//                messageInput = findViewById(R.id.messageInput)
//                Log.d("inbox", "MessageInput found")
//            } catch (e: Exception) {
//                Log.e("inbox", "Error finding messageInput", e)
//                Toast.makeText(this, "Error: MessageInput not found", Toast.LENGTH_SHORT).show()
//                finish()
//                return
//            }
//
//            try {
//                sendButton = findViewById(R.id.send)
//                Log.d("inbox", "SendButton found")
//            } catch (e: Exception) {
//                Log.e("inbox", "Error finding send", e)
//                Toast.makeText(this, "Error: SendButton not found", Toast.LENGTH_SHORT).show()
//                finish()
//                return
//            }
//
//            // In onCreate(), after finding sendButton, add:
//            try {
//                photoButton = findViewById(R.id.photoButton)
//                photoButton.setOnClickListener {
//                    openImagePicker()
//                }
//                Log.d("inbox", "PhotoButton found")
//            } catch (e: Exception) {
//                Log.e("inbox", "Error finding photoButton", e)
//            }
//
//            // Setup RecyclerView
//            try {
//                recyclerView.layoutManager = LinearLayoutManager(this)
//                adapter = chatadapter(
//                    this,
//                    messages,
//                    currentUserId,
//                    onEditClick = { message -> editMessage(message) },
//                    onDeleteClick = { message -> deleteMessage(message) }
//                )
//                recyclerView.adapter = adapter
//                Log.d("inbox", "RecyclerView setup complete")
//            } catch (e: Exception) {
//                Log.e("inbox", "Error setting up RecyclerView", e)
//                Toast.makeText(this, "Error setting up chat: ${e.message}", Toast.LENGTH_LONG).show()
//                finish()
//                return
//            }
//
//            // Setup send button
//            sendButton.setOnClickListener {
//                Log.d("inbox", "Send button clicked")
//                sendMessage()
//            }
//
//            // Load messages from Firestore
//            try {
//                loadMessages()
//                Log.d("inbox", "loadMessages called")
//            } catch (e: Exception) {
//                Log.e("inbox", "Error in loadMessages", e)
//                Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//
//            Log.d("inbox", "onCreate completed successfully")
//
//        } catch (e: Exception) {
//            Log.e("inbox", "Fatal error in onCreate", e)
//            Toast.makeText(this, "Fatal error: ${e.message}", Toast.LENGTH_LONG).show()
//            finish()
//        }
//    }
//
//    private fun loadMessages() {
//        try {
//            db.collection("chats")
//                .document(chatId)
//                .collection("messages")
//                .orderBy("timestamp", Query.Direction.ASCENDING)
//                .addSnapshotListener { snapshot, error ->
//                    if (error != null) {
//                        Log.e("inbox", "Error loading messages", error)
//                        Toast.makeText(this, "Error loading messages: ${error.message}", Toast.LENGTH_SHORT).show()
//                        return@addSnapshotListener
//                    }
//
//                    try {
//                        messages.clear()
//                        snapshot?.documents?.forEach { doc ->
//                            try {
//                                val msg = doc.toObject(Message::class.java)
//                                if (msg != null) {
//                                    messages.add(msg)
//                                    Log.d("inbox", "Message loaded: ${msg.messageId}")
//                                }
//                            } catch (e: Exception) {
//                                Log.e("inbox", "Error parsing message", e)
//                            }
//                        }
//
//                        runOnUiThread {
//                            adapter.notifyDataSetChanged()
//                            Log.d("inbox", "Adapter updated with ${messages.size} messages")
//
//                            // Scroll to bottom to show latest message
//                            if (messages.isNotEmpty()) {
//                                recyclerView.scrollToPosition(messages.size - 1)
//                            }
//                        }
//                    } catch (e: Exception) {
//                        Log.e("inbox", "Error updating UI", e)
//                    }
//                }
//        } catch (e: Exception) {
//            Log.e("inbox", "Error setting up listener", e)
//            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }
//
//    private fun sendMessage() {
//        try {
//            val messageText = messageInput.text.toString().trim()
//
//            if (messageText.isEmpty()) {
//                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
//                return
//            }
//
//            val messageId = db.collection("chats").document(chatId)
//                .collection("messages").document().id
//
//            val message = Message(
//                messageId = messageId,
//                senderId = currentUserId,
//                messageText = messageText,
//                messageType = "text",
//                timestamp = System.currentTimeMillis(),
//                edited = false
//            )
//
//            db.collection("chats")
//                .document(chatId)
//                .collection("messages")
//                .document(messageId)
//                .set(message)
//                .addOnSuccessListener {
//                    messageInput.text.clear()
//                    Log.d("inbox", "Message sent successfully")
//                    Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
//                }
//                .addOnFailureListener { e ->
//                    Log.e("inbox", "Error sending message", e)
//                    Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//        } catch (e: Exception) {
//            Log.e("inbox", "Error in sendMessage", e)
//            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun editMessage(message: Message) {
//        try {
//            // Check if message is still editable (within 5 minutes)
//            val timeSince = System.currentTimeMillis() - message.timestamp
//            if (timeSince > 5 * 60 * 1000) {
//                Toast.makeText(this, "Can only edit messages within 5 minutes", Toast.LENGTH_SHORT).show()
//                return
//            }
//
//            // Only allow editing text messages
//            if (message.messageType != "text") {
//                Toast.makeText(this, "Can only edit text messages", Toast.LENGTH_SHORT).show()
//                return
//            }
//
//            val input = EditText(this)
//            input.setText(message.messageText)
//            input.setSelection(input.text.length)
//
//            AlertDialog.Builder(this)
//                .setTitle("Edit message")
//                .setView(input)
//                .setPositiveButton("Save") { _, _ ->
//                    val newText = input.text.toString().trim()
//                    if (newText.isEmpty()) {
//                        Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
//                        return@setPositiveButton
//                    }
//
//                    db.collection("chats")
//                        .document(chatId)
//                        .collection("messages")
//                        .document(message.messageId)
//                        .update(mapOf(
//                            "messageText" to newText,
//                            "edited" to true
//                        ))
//                        .addOnSuccessListener {
//                            Toast.makeText(this, "Message updated", Toast.LENGTH_SHORT).show()
//                        }
//                        .addOnFailureListener { e ->
//                            Log.e("inbox", "Error updating message", e)
//                            Toast.makeText(this, "Failed to update message", Toast.LENGTH_SHORT).show()
//                        }
//                }
//                .setNegativeButton("Cancel", null)
//                .show()
//        } catch (e: Exception) {
//            Log.e("inbox", "Error in editMessage", e)
//            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun deleteMessage(message: Message) {
//        try {
//            // Check if message is still deletable (within 5 minutes)
//            val timeSince = System.currentTimeMillis() - message.timestamp
//            if (timeSince > 5 * 60 * 1000) {
//                Toast.makeText(this, "Can only delete messages within 5 minutes", Toast.LENGTH_SHORT).show()
//                return
//            }
//
//            AlertDialog.Builder(this)
//                .setTitle("Delete message?")
//                .setMessage("This action cannot be undone")
//                .setPositiveButton("Delete") { _, _ ->
//                    db.collection("chats")
//                        .document(chatId)
//                        .collection("messages")
//                        .document(message.messageId)
//                        .delete()
//                        .addOnSuccessListener {
//                            Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
//                        }
//                        .addOnFailureListener { e ->
//                            Log.e("inbox", "Error deleting message", e)
//                            Toast.makeText(this, "Failed to delete message", Toast.LENGTH_SHORT).show()
//                        }
//                }
//                .setNegativeButton("Cancel", null)
//                .show()
//        } catch (e: Exception) {
//            Log.e("inbox", "Error in deleteMessage", e)
//            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//    private fun openImagePicker() {
//        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        intent.type = "image/*"
//        startActivityForResult(intent, PICK_IMAGE_REQUEST)
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null)  {
//            val imageUri = data.data
//            if (imageUri != null) {
//                uploadImageAndSendMessage(imageUri)
//            }
//        }
//    }
//
////    private fun uploadImageAndSendMessage(imageUri: Uri) {
////        // Show progress toast
////        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
////
////        // Create unique filename
////        val fileName = "chat_images/${UUID.randomUUID()}.jpg"
////        val storageRef = storage.reference.child(fileName)
////
////        // Upload image
////        storageRef.putFile(imageUri)
////            .addOnSuccessListener { taskSnapshot ->
////                // Get download URL
////                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
////                    sendImageMessage(downloadUri.toString())
////                }
////            }
////            .addOnFailureListener { e ->
////                Log.e("inbox", "Error uploading image", e)
////                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
////            }
////    }
////
////    private fun sendImageMessage(imageUrl: String) {
////        try {
////            val messageId = db.collection("chats").document(chatId)
////                .collection("messages").document().id
////
////            val message = Message(
////                messageId = messageId,
////                senderId = currentUserId,
////                messageText = null,
////                messageType = "image",
////                imageUrl = imageUrl,
////                timestamp = System.currentTimeMillis(),
////                edited = false
////            )
////
////            db.collection("chats")
////                .document(chatId)
////                .collection("messages")
////                .document(messageId)
////                .set(message)
////                .addOnSuccessListener {
////                    Log.d("inbox", "Image message sent successfully")
////                    Toast.makeText(this, "Image sent", Toast.LENGTH_SHORT).show()
////                }
////                .addOnFailureListener { e ->
////                    Log.e("inbox", "Error sending image message", e)
////                    Toast.makeText(this, "Failed to send image: ${e.message}", Toast.LENGTH_SHORT).show()
////                }
////        } catch (e: Exception) {
////            Log.e("inbox", "Error in sendImageMessage", e)
////            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
////        }
////    }
//private fun uploadImageAndSendMessage(imageUri: Uri) {
//    Toast.makeText(this, "Processing image...", Toast.LENGTH_SHORT).show()
//
//    try {
//        // Convert image to Base64
//        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
//        val bitmap = BitmapFactory.decodeStream(inputStream)
//
//        // Compress bitmap to reduce size
//        val baos = ByteArrayOutputStream()
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos) // 50% quality
//        val imageBytes = baos.toByteArray()
//        val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
//
//        // Send the Base64 string as the image
//        sendImageMessage(base64Image)
//
//    } catch (e: Exception) {
//        Log.e("inbox", "Error processing image", e)
//        Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
//    }
//}
//
//    // Update sendImageMessage to handle Base64:
//    private fun sendImageMessage(base64Image: String) {
//        try {
//            val messageId = db.collection("chats").document(chatId)
//                .collection("messages").document().id
//
//            val message = Message(
//                messageId = messageId,
//                senderId = currentUserId,
//                messageText = null,
//                messageType = "image",
//                imageUrl = base64Image, // Store Base64 string here
//                timestamp = System.currentTimeMillis(),
//                edited = false
//            )
//
//            db.collection("chats")
//                .document(chatId)
//                .collection("messages")
//                .document(messageId)
//                .set(message)
//                .addOnSuccessListener {
//                    Log.d("inbox", "Image message sent successfully")
//                    Toast.makeText(this, "Image sent", Toast.LENGTH_SHORT).show()
//                }
//                .addOnFailureListener { e ->
//                    Log.e("inbox", "Error sending image message", e)
//                    Toast.makeText(this, "Failed to send image: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//        } catch (e: Exception) {
//            Log.e("inbox", "Error in sendImageMessage", e)
//            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun getCurrentUserId(): String {
//        // First try Firebase Auth
//        val firebaseUser = FirebaseAuth.getInstance().currentUser?.uid
//        if (firebaseUser != null) {
//            return firebaseUser
//        }
//
//        // If no Firebase user, use SharedPreferences
//        val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
//        var userId = sharedPrefs.getString("user_id", null)
//
//        if (userId == null) {
//            // Generate new ID and save it
//            userId = "user_${System.currentTimeMillis()}"
//            sharedPrefs.edit().putString("user_id", userId).apply()
//        }
//
//        return userId
//    }
//
//
//
//
//}
