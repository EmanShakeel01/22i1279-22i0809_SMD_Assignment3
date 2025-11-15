
// ============================================
// 2. UPDATED takepicture.kt (Remove duplicate upload)
// ============================================
package com.FEdev.i221279_i220809

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream
import java.io.IOException

class takepicture : AppCompatActivity() {

    private lateinit var shutterButton: ImageView
    private lateinit var galleryButton: ImageView

    private val CAMERA_REQUEST_CODE = 100
    private val GALLERY_REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_takepicture)
        Log.d("ActivityStack", "takepicture onCreate")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        shutterButton = findViewById(R.id.shutterButton)
        galleryButton = findViewById(R.id.galleryPreview)

        // 📸 Capture photo
        shutterButton.setOnClickListener {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (cameraIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
            } else {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }

        // 🖼️ Pick image from gallery
        galleryButton.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)
        }

        // 🔙 Back to homepage
        val backButton = findViewById<ImageView>(R.id.back)
        backButton.setOnClickListener {
            val intent = Intent(this, homepage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    // 🎯 Handle camera/gallery result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val bitmap: Bitmap? = when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    data.extras?.get("data") as? Bitmap
                }
                GALLERY_REQUEST_CODE -> {
                    val imageUri: Uri? = data.data
                    if (imageUri != null) {
                        contentResolver.openInputStream(imageUri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                    } else null
                }
                else -> null
            }

            if (bitmap != null) {
                val imageBase64 = encodeToBase64(bitmap)

                // ✅ Go to story preview screen (same for camera + gallery)
                val previewIntent = Intent(this, storyviewer1::class.java)
                previewIntent.putExtra("imageBase64", imageBase64)
                startActivity(previewIntent)
            } else {
                Toast.makeText(this, "❌ Could not process image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e("ImageError", "Error loading image: ${e.message}")
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }


    // 🧠 Encode bitmap to Base64
    private fun encodeToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val byteArray = baos.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActivityStack", "takepicture onDestroy")
    }
}
