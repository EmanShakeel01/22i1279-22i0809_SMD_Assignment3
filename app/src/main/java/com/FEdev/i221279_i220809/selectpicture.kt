package com.FEdev.i221279_i220809

import Post
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class selectpicture : AppCompatActivity() {

    private var imageUri: Uri? = null
    private lateinit var selectedImage: ImageView
    private var isStory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_selectpicture)
        Log.d("ActivityStack", "selectpicture onCreate")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        selectedImage = findViewById(R.id.selectedImage)
        isStory = intent.getBooleanExtra("isStory", false)

        // --- Select image from gallery ---
        selectedImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 101)
        }

        // --- Upload when next is clicked ---
        val nextBtn = findViewById<TextView>(R.id.next)
        nextBtn.setOnClickListener {
            if (imageUri != null) {
                if (isStory) {
                    saveStoryToFirebase(imageUri!!)
                } else {
                    savePostToFirebase(imageUri!!)
                }
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Cancel button ---
        val cancelBtn = findViewById<TextView>(R.id.cancelText)
        cancelBtn.setOnClickListener { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            selectedImage.setImageURI(imageUri)
        }
    }

    // ✅ Function for uploading POSTS under logged-in user
    private fun savePostToFirebase(uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val safeEmail = user.email!!.replace(".", ",")
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(safeEmail)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val username = snapshot.child("username").getValue(String::class.java) ?: "Anonymous"

                Log.d("FirebaseDebug", "✅ Username fetched: $username")

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                    val dbRef = FirebaseDatabase.getInstance().getReference("Posts")
                    val postId = dbRef.push().key ?: UUID.randomUUID().toString()

                    val post = mapOf(
                        "postId" to postId,
                        "userId" to user.uid,
                        "username" to username,
                        "imageUrl" to base64Image,
                        "caption" to "Shared via Socially!",
                        "timestamp" to System.currentTimeMillis()
                    )

                    dbRef.child(postId).setValue(post)
                        .addOnSuccessListener {
                            Toast.makeText(this@selectpicture, "✅ Post uploaded successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@selectpicture, "❌ Database error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@selectpicture, "Failed to save post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@selectpicture, "Failed to load user: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ✅ Function for uploading STORIES under logged-in user
    private fun saveStoryToFirebase(uri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            val dbRef = FirebaseDatabase.getInstance().getReference("Stories").child(userId)
            val storyId = dbRef.push().key ?: UUID.randomUUID().toString()

            val timestamp = System.currentTimeMillis()
            val expiresAt = timestamp + 24 * 60 * 60 * 1000 // 24 hours

            val storyData = mapOf(
                "storyId" to storyId,
                "userId" to userId,
                "imageBase64" to base64Image,
                "timestamp" to timestamp,
                "expiresAt" to expiresAt
            )

            dbRef.child(storyId).setValue(storyData)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Story uploaded!", Toast.LENGTH_SHORT).show()

                    // Open viewer for this story
                    val intent = Intent(this, storyviewer2::class.java)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "❌ Database error: ${it.message}", Toast.LENGTH_SHORT).show()
                }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save story: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "selectpicture onDestroy")
    }
}