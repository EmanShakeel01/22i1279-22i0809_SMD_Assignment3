package com.FEdev.i221279_i220809

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.models.StoryUploadRequest
import com.FEdev.i221279_i220809.utils.SessionManager
import kotlinx.coroutines.launch

class storyviewer1 : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var base64Image: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storyviewer1)

        Log.d("StoryViewer1", "=== onCreate ===")

        sessionManager = SessionManager(this)

        val nextBtn = findViewById<ImageButton>(R.id.nextBtn)
        val backBtn = findViewById<ImageView>(R.id.back)
        val storyImage = findViewById<ImageView>(R.id.storyImage)

        base64Image = intent.getStringExtra("imageBase64")

        Log.d("StoryViewer1", "Received image: ${base64Image != null}, Length: ${base64Image?.length ?: 0}")

        // Show story preview
        if (base64Image != null) {
            try {
                val cleanBase64 = base64Image!!.replace("\n", "").replace("\r", "")
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                if (bitmap != null) {
                    storyImage.setImageBitmap(bitmap)
                    Log.d("StoryViewer1", "✅ Image decoded and displayed")
                } else {
                    Log.e("StoryViewer1", "❌ Bitmap is null")
                    Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("StoryViewer1", "❌ Error decoding: ${e.message}", e)
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("StoryViewer1", "❌ No image data found")
            Toast.makeText(this, "No image data found", Toast.LENGTH_SHORT).show()
        }

        // Upload story when "Next" button is clicked
        nextBtn.setOnClickListener {
            if (base64Image != null) {
                uploadStoryToServer(base64Image!!)
            } else {
                Toast.makeText(this, "No image to upload", Toast.LENGTH_SHORT).show()
            }
        }

        // Back button
        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun uploadStoryToServer(base64Image: String) {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.e("StoryViewer1", "❌ No auth token")
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading
        Toast.makeText(this, "Uploading story...", Toast.LENGTH_SHORT).show()
        Log.d("StoryViewer1", "Uploading story with token: ${authToken.take(10)}...")

        lifecycleScope.launch {
            try {
                // Clean base64 string
                val cleanBase64 = base64Image.replace("\n", "").replace("\r", "")

                val request = StoryUploadRequest(
                    auth_token = authToken,
                    image_base64 = cleanBase64
                )

                Log.d("StoryViewer1", "Making API call...")
                val response = RetrofitClient.apiService.uploadStory(request)

                Log.d("StoryViewer1", "Response code: ${response.code()}")
                Log.d("StoryViewer1", "Response body: ${response.body()}")

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d("StoryViewer1", "✅ Story uploaded successfully")
                    Toast.makeText(
                        this@storyviewer1,
                        "Story posted!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Go to homepage
                    val intent = Intent(this@storyviewer1, homepage::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                } else {
                    val errorMsg = response.body()?.message ?: "Unknown error"
                    Log.e("StoryViewer1", "❌ Upload failed: $errorMsg")
                    Toast.makeText(
                        this@storyviewer1,
                        "Failed: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("StoryViewer1", "❌ Exception: ${e.message}", e)
                Toast.makeText(
                    this@storyviewer1,
                    "Network error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}