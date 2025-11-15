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
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.IOException

class takepicture : AppCompatActivity() {

    private lateinit var shutterButton: ImageView
    private lateinit var galleryButton: ImageView

    private val CAMERA_REQUEST_CODE = 100
    private val GALLERY_REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_takepicture)
        Log.d("TakePicture", "onCreate")

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
            finish()
        }
    }

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
                // ✅ Compress and scale image before encoding
                val compressedBitmap = compressImage(bitmap)
                val imageBase64 = encodeToBase64(compressedBitmap)

                Log.d("TakePicture", "Image size: ${imageBase64.length} characters")

                // Go to story preview screen
                val previewIntent = Intent(this, storyviewer1::class.java)
                previewIntent.putExtra("imageBase64", imageBase64)
                startActivity(previewIntent)
            } else {
                Toast.makeText(this, "Could not process image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e("TakePicture", "Error loading image: ${e.message}", e)
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        } catch (e: OutOfMemoryError) {
            Log.e("TakePicture", "Out of memory: ${e.message}", e)
            Toast.makeText(this, "Image too large", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Compress and scale image
    private fun compressImage(bitmap: Bitmap): Bitmap {
        val maxWidth = 1080
        val maxHeight = 1920

        var width = bitmap.width
        var height = bitmap.height

        // Calculate scale factor
        val scale = minOf(
            maxWidth.toFloat() / width.toFloat(),
            maxHeight.toFloat() / height.toFloat(),
            1.0f // Don't upscale
        )

        if (scale < 1.0f) {
            width = (width * scale).toInt()
            height = (height * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }

        return bitmap
    }

    // ✅ Encode bitmap to Base64 with compression
    private fun encodeToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        // Compress to 70% quality JPEG
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val byteArray = baos.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP) // NO_WRAP removes newlines
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TakePicture", "onDestroy")
    }
}