package com.FEdev.i221279_i220809

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.FEdev.i221279_i220809.models.UpdateProfilePictureRequest
import com.FEdev.i221279_i220809.network.RetrofitClient
import com.FEdev.i221279_i220809.utils.SessionManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class editprofile : AppCompatActivity() {

    private lateinit var profileImage: CircleImageView
    private lateinit var sessionManager: SessionManager

    private var selectedImageUri: Uri? = null
    private var isImageChanged = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                selectedImageUri = uri
                profileImage.setImageURI(uri)
                isImageChanged = true
                Log.d("EditProfile", "✅ Image selected: $uri")
                Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editprofile)

        Log.d("EditProfile", "onCreate")

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        profileImage = findViewById(R.id.profilePhoto)
        Log.d("EditProfile", "Views initialized")
    }

    private fun setupClickListeners() {
        // Tap on the profile image
        profileImage.setOnClickListener {
            Log.d("EditProfile", "Profile image clicked")
            openGallery()
        }

        // Tap on "Change Profile Photo" text
        findViewById<TextView>(R.id.changephoto).setOnClickListener {
            Log.d("EditProfile", "Change photo text clicked")
            openGallery()
        }

        // Cancel
        findViewById<TextView>(R.id.cancelText).setOnClickListener {
            Log.d("EditProfile", "Cancel clicked")
            finish()
        }

        // Done
        findViewById<TextView>(R.id.doneText).setOnClickListener {
            Log.d("EditProfile", "Done clicked")
            if (isImageChanged && selectedImageUri != null) {
                uploadProfilePicture()
            } else {
                Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun openGallery() {
        Log.d("EditProfile", "Opening gallery")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun uploadProfilePicture() {
        val authToken = sessionManager.getAuthToken()

        if (authToken == null) {
            Log.e("EditProfile", "❌ No auth token")
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("EditProfile", "Starting upload with token: ${authToken.take(10)}...")
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                Log.d("EditProfile", "Converting and compressing image...")
                val base64Image = convertAndCompressImage(selectedImageUri!!)

                if (base64Image == null) {
                    Log.e("EditProfile", "❌ Image conversion failed")
                    runOnUiThread {
                        Toast.makeText(this@editprofile, "Image conversion failed", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("EditProfile", "✅ Image converted, size: ${base64Image.length} chars")

                val request = UpdateProfilePictureRequest(
                    auth_token = authToken,
                    profile_picture = base64Image
                )

                Log.d("EditProfile", "📡 Sending request to API...")
                val response = RetrofitClient.apiService.updateProfilePicture(request)

                Log.d("EditProfile", "Response code: ${response.code()}")
                Log.d("EditProfile", "Response message: ${response.message()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("EditProfile", "Response body: $body")

                    if (body?.success == true) {
                        Log.d("EditProfile", "✅ Upload successful!")

                        // Save to SessionManager
                        try {
                            sessionManager.saveProfilePic(base64Image)
                            Log.d("EditProfile", "✅ Saved to SessionManager")

                            // Verify it was saved
                            val savedPic = sessionManager.getProfilePic()
                            Log.d("EditProfile", "Verification - Saved pic length: ${savedPic?.length ?: 0}")
                        } catch (e: Exception) {
                            Log.e("EditProfile", "❌ Error saving to SessionManager: ${e.message}", e)
                        }

                        runOnUiThread {
                            Toast.makeText(
                                this@editprofile,
                                "Profile picture updated!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Navigate back to profile
                            val intent = Intent(this@editprofile, activityprofile::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        val errorMsg = body?.message ?: "Unknown error"
                        Log.e("EditProfile", "❌ API returned success=false: $errorMsg")
                        runOnUiThread {
                            Toast.makeText(
                                this@editprofile,
                                "Upload failed: $errorMsg",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("EditProfile", "❌ HTTP Error ${response.code()}")
                    Log.e("EditProfile", "Error body: $errorBody")

                    runOnUiThread {
                        Toast.makeText(
                            this@editprofile,
                            "Upload failed: HTTP ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("EditProfile", "❌ Exception: ${e.message}", e)
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@editprofile,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun convertAndCompressImage(imageUri: Uri): String? {
        return try {
            Log.d("EditProfile", "Reading image from: $imageUri")

            val inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e("EditProfile", "Failed to open input stream")
                return null
            }

            // Decode bitmap
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                Log.e("EditProfile", "Failed to decode bitmap")
                return null
            }

            Log.d("EditProfile", "Original image size: ${originalBitmap.width}x${originalBitmap.height}")

            // Compress and resize
            val maxSize = 800 // Max width/height
            val ratio = Math.min(
                maxSize.toFloat() / originalBitmap.width,
                maxSize.toFloat() / originalBitmap.height
            )

            val newWidth = (originalBitmap.width * ratio).toInt()
            val newHeight = (originalBitmap.height * ratio).toInt()

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            Log.d("EditProfile", "Resized image to: ${newWidth}x${newHeight}")

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()

            Log.d("EditProfile", "Compressed image size: ${bytes.size} bytes")

            // Convert to base64
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Log.d("EditProfile", "Base64 length: ${base64.length}")

            // Clean up
            originalBitmap.recycle()
            resizedBitmap.recycle()

            base64
        } catch (e: Exception) {
            Log.e("EditProfile", "❌ Image conversion failed: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("EditProfile", "onDestroy")
    }
}