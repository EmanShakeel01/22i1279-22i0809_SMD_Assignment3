package com.FEdev.i221279_i220809

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
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.models.PostUploadRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class selectpicture : AppCompatActivity() {

    private var imageUri: Uri? = null
    private lateinit var selectedImage: ImageView
    private lateinit var sessionManager: SessionManager
    private var isStory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_selectpicture)
        Log.d("ActivityStack", "selectpicture onCreate")

        sessionManager = SessionManager(this)

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
                    savePostToWebService(imageUri!!)
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

    // ✅ Function for uploading POSTS via Web Service
    private fun savePostToWebService(uri: Uri) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Convert image to base64
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            Log.d("PostUpload", "Image size: ${imageBytes.size} bytes")

            // Show loading
            Toast.makeText(this, "Uploading post...", Toast.LENGTH_SHORT).show()

            // Upload to web service
            lifecycleScope.launch {
                try {
                    val request = PostUploadRequest(
                        auth_token = authToken,
                        image_base64 = base64Image,
                        caption = "Shared via Socially!"
                    )

                    Log.d("PostUpload", "Sending request to server...")
                    val response = RetrofitClient.apiService.uploadPost(request)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        Log.d("PostUpload", "✅ Post uploaded successfully! Post ID: ${data?.post_id}")

                        Toast.makeText(
                            this@selectpicture,
                            "Post created successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Go to homepage
                        val intent = Intent(this@selectpicture, homepage::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e("PostUpload", "❌ Upload failed: ${response.body()?.message}")
                        Toast.makeText(
                            this@selectpicture,
                            response.body()?.message ?: "Failed to upload post",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("PostUpload", "❌ Error: ${e.message}", e)
                    Toast.makeText(
                        this@selectpicture,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PostUpload", "Failed to process image: ${e.message}")
            Toast.makeText(this, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Function for uploading STORIES to Firebase (unchanged)
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
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

            Toast.makeText(this, "Uploading story...", Toast.LENGTH_SHORT).show()

            dbRef.child(storyId).setValue(storyData)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Story uploaded!", Toast.LENGTH_SHORT).show()

                    // Open viewer for this story
                    val intent = Intent(this, storyviewer2::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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