package com.FEdev.i221279_i220809

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class storyviewer1 : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance().getReference("Stories")

    private var base64Image: String? = null  // ✅ store image for upload when button pressed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_storyviewer1)
        Log.d("ActivityStack", "Storyviewer1 onCreate")

        auth = FirebaseAuth.getInstance()

        val nextBtn = findViewById<ImageButton>(R.id.nextBtn)
        val backBtn = findViewById<ImageView>(R.id.back)
        val storyImage = findViewById<ImageView>(R.id.storyImage)

        base64Image = intent.getStringExtra("imageBase64")

        // ✅ Show story preview only (no upload yet)
        if (base64Image != null) {
            try {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    storyImage.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("StoryViewer1", "Error decoding: ${e.message}")
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No image data found", Toast.LENGTH_SHORT).show()
        }

        // ✅ Upload story only when "Next" button is clicked
        nextBtn.setOnClickListener {
            if (base64Image != null) {
                uploadStoryToFirebase(base64Image!!)
            } else {
                Toast.makeText(this, "No image to upload", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Back button - go back to takepicture activity
        backBtn.setOnClickListener {
            val intent = Intent(this, takepicture::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Optional: Adjust padding for edge-to-edge layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // ✅ Upload story to Firebase
    // ✅ Upload story to Firebase with correct username
    // ✅ Upload story to Firebase - fetch username by searching for UID
    private fun uploadStoryToFirebase(base64Image: String) {
        val userId = auth.currentUser?.uid ?: return
        val storyId = db.child(userId).push().key ?: return
        val timestamp = System.currentTimeMillis()
        val expiresAt = timestamp + 24 * 60 * 60 * 1000 // 24 hours

        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        // ✅ Query all users to find the one with matching UID
        usersRef.get().addOnSuccessListener { snapshot ->
            var username = "User"

            // Search through all users to find the one with our UID
            for (userSnap in snapshot.children) {
                val uid = userSnap.child("uid").getValue(String::class.java)
                if (uid == userId) {
                    username = userSnap.child("username").getValue(String::class.java) ?: "User"
                    Log.d("Firebase", "Found username: $username")
                    break
                }
            }

            val storyData = mapOf(
                "imageBase64" to base64Image,
                "timestamp" to timestamp,
                "expiresAt" to expiresAt,
                "uploadedBy" to userId,
                "username" to username
            )

            db.child(userId).child(storyId).setValue(storyData)
                .addOnSuccessListener {
                    Log.d("Firebase", "Story uploaded successfully with username: $username ✅")
                    Toast.makeText(this, "Story posted!", Toast.LENGTH_SHORT).show()

                    // ✅ Go to homepage after upload
                    val intent = Intent(this, homepage::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to upload story ❌", e)
                    Toast.makeText(this, "Failed to upload story", Toast.LENGTH_SHORT).show()
                }

        }.addOnFailureListener { e ->
            Log.e("Firebase", "Failed to fetch users ❌", e)

            // Fallback with default username
            val storyData = mapOf(
                "imageBase64" to base64Image,
                "timestamp" to timestamp,
                "expiresAt" to expiresAt,
                "uploadedBy" to userId,
                "username" to "User"
            )

            db.child(userId).child(storyId).setValue(storyData)
                .addOnSuccessListener {
                    Log.d("Firebase", "Story uploaded with default username ✅")
                    Toast.makeText(this, "Story posted!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, homepage::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to upload story ❌", e)
                    Toast.makeText(this, "Failed to upload story", Toast.LENGTH_SHORT).show()
                }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "Storyviewer1 onDestroy")
    }
}
